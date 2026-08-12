package com.medcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.Calendar
import java.util.UUID

/**
 * Single source of truth. Persists to filesDir/library.json.
 * Held for the lifetime of the Activity and passed down to composables.
 */
class Store(private val ctx: Context) {

    val cards = mutableStateListOf<Card>()
    val logs = mutableStateListOf<ReviewLog>()
    var settings by mutableStateOf(AppSettings())

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val libraryFile: File get() = File(ctx.filesDir, "library.json")
    val imagesDir: File get() = File(ctx.filesDir, "images").also { if (!it.exists()) it.mkdirs() }

    init {
        load()
    }

    // ------------------------------------------------------------ persistence

    private fun load() {
        if (!libraryFile.exists()) {
            cards.addAll(SampleData.starterCards())
            save()
            return
        }
        runCatching {
            val data = json.decodeFromString(LibraryData.serializer(), libraryFile.readText())
            cards.clear(); cards.addAll(data.cards)
            logs.clear(); logs.addAll(data.logs)
            settings = data.settings
        }
    }

    fun save() {
        runCatching {
            val data = LibraryData(cards.toList(), logs.toList(), settings)
            libraryFile.writeText(json.encodeToString(LibraryData.serializer(), data))
        }
    }

    // ------------------------------------------------------------ images

    fun saveImage(input: InputStream): String? = runCatching {
        val bitmap = BitmapFactory.decodeStream(input) ?: return null
        val scaled = scaleDown(bitmap, 1400)
        val name = UUID.randomUUID().toString() + ".jpg"
        File(imagesDir, name).outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        name
    }.getOrNull()

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    fun loadImage(name: String): Bitmap? = runCatching {
        BitmapFactory.decodeFile(File(imagesDir, name).absolutePath)
    }.getOrNull()

    fun deleteImage(name: String) {
        runCatching { File(imagesDir, name).delete() }
    }

    // ------------------------------------------------------------ CRUD

    fun upsert(card: Card) {
        val i = cards.indexOfFirst { it.id == card.id }
        if (i >= 0) cards[i] = card else cards.add(card)
        save()
    }

    fun delete(card: Card) {
        cards.removeAll { it.id == card.id }
        save()
    }

    /**
     * Expands a note with cloze markup into one card per cloze index.
     * Returns how many cards were created.
     */
    fun addNote(
        front: String,
        back: String,
        subject: String,
        topic: String,
        tags: List<String>,
        imageNames: List<String>
    ): Int {
        val indices = Cloze.indices(front)
        val made = if (indices.isEmpty()) {
            listOf(Card(front = front, back = back, subject = subject, topic = topic,
                tags = tags, imageNames = imageNames))
        } else {
            indices.map { i ->
                Card(front = front, back = back, subject = subject, topic = topic,
                    tags = tags, imageNames = imageNames, clozeIndex = i)
            }
        }
        cards.addAll(made)
        save()
        return made.size
    }

    // ------------------------------------------------------------ derived

    val subjects: List<String>
        get() = cards.map { it.subject }.filter { it.isNotBlank() }.distinct()
            .sortedBy { Mbbs.order(it) }

    data class TopicInfo(
        val name: String,
        val subjects: List<String>,
        val total: Int,
        val due: Int,
        val new: Int
    ) {
        /** A topic is "integrated" when it spans two or more subjects. */
        val isIntegrated: Boolean get() = subjects.size >= 2
    }

    val topics: List<TopicInfo>
        get() = cards.filter { it.topic.isNotBlank() && !it.suspended }
            .groupBy { it.topic }
            .map { (name, group) ->
                TopicInfo(
                    name = name,
                    subjects = group.map { it.subject }.distinct().sortedBy { Mbbs.order(it) },
                    total = group.size,
                    due = group.count { it.isDue && it.state != CardState.NEW },
                    new = group.count { it.state == CardState.NEW }
                )
            }
            .sortedWith(
                compareByDescending<TopicInfo> { it.isIntegrated }
                    .thenByDescending { it.due + it.new }
                    .thenBy { it.name }
            )

    fun dueCount(subject: String? = null): Int = cards.count {
        !it.suspended && it.isDue && it.state != CardState.NEW &&
                (subject == null || it.subject == subject)
    }

    fun newCount(subject: String? = null): Int = cards.count {
        !it.suspended && it.state == CardState.NEW &&
                (subject == null || it.subject == subject)
    }

    // ------------------------------------------------------------ queues

    /** Standard queue: due reviews first, then a capped number of new cards. */
    fun buildQueue(subject: String? = null, limit: Int? = null): List<Card> {
        val cap = limit ?: settings.sessionSize
        val pool = cards.filter { !it.suspended && (subject == null || it.subject == subject) }
        val due = pool.filter { it.isDue && it.state != CardState.NEW }.sortedBy { it.due }
        val fresh = pool.filter { it.state == CardState.NEW }.sortedBy { it.createdAt }

        val queue = due.take(cap).toMutableList()
        if (queue.size < cap) {
            val room = minOf(cap - queue.size, settings.newCardsPerDay)
            queue += fresh.take(room)
        }
        return queue
    }

    /**
     * Integrated queue for one topic: pulls due + new cards from EVERY subject
     * that covers the topic, then interleaves them round-robin in curriculum
     * order, so you meet the same concept from Anatomy through Medicine.
     */
    fun buildTopicQueue(topic: String, limit: Int? = null): List<Card> {
        val cap = limit ?: settings.topicSessionSize
        val candidates = cards.filter {
            !it.suspended && it.topic == topic && (it.isDue || it.state == CardState.NEW)
        }
        if (candidates.isEmpty()) return emptyList()

        val bySubject: Map<String, List<Card>> = candidates
            .groupBy { it.subject }
            .mapValues { (_, list) ->
                list.sortedWith(compareBy<Card> { it.state == CardState.NEW }.thenBy { it.due })
            }

        val orderedSubjects = bySubject.keys.sortedBy { Mbbs.order(it) }
        val queue = mutableListOf<Card>()
        var round = 0
        while (queue.size < cap) {
            var addedThisRound = false
            for (s in orderedSubjects) {
                val list = bySubject[s] ?: continue
                if (round >= list.size) continue
                queue += list[round]
                addedThisRound = true
                if (queue.size >= cap) break
            }
            if (!addedThisRound) break
            round++
        }
        return queue
    }

    /** Queue across several topics at once. */
    fun buildMultiTopicQueue(selected: List<String>, limit: Int? = null): List<Card> {
        val cap = limit ?: settings.topicSessionSize
        if (selected.isEmpty()) return emptyList()
        val per = maxOf(cap / selected.size, 1)
        return selected.flatMap { buildTopicQueue(it, per) }.take(cap)
    }

    // ------------------------------------------------------------ answering

    private fun engine() = Fsrs(settings.fsrsWeights, settings.desiredRetention)

    fun answer(card: Card, rating: Rating) {
        val outcome = engine().review(card, rating)
        val i = cards.indexOfFirst { it.id == card.id }
        if (i >= 0) cards[i] = outcome.card
        logs.add(
            ReviewLog(
                cardId = card.id,
                date = System.currentTimeMillis(),
                rating = rating.value,
                elapsedDays = outcome.elapsedDays,
                scheduledDays = outcome.scheduledDays,
                subject = card.subject,
                topic = card.topic
            )
        )
        save()
    }

    fun previews(card: Card): Map<Rating, String> = engine().previews(card)

    fun resetProgress(card: Card) {
        upsert(
            card.copy(
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                reps = 0,
                lapses = 0,
                lastReview = null,
                due = System.currentTimeMillis()
            )
        )
    }

    fun renameTopic(old: String, new: String) {
        for (i in cards.indices) {
            if (cards[i].topic == old) cards[i] = cards[i].copy(topic = new)
        }
        save()
    }

    // ------------------------------------------------------------ stats

    private fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun reviewsPerDay(days: Int = 30): List<Pair<Long, Int>> {
        val today = startOfDay(System.currentTimeMillis())
        val buckets = logs.groupingBy { startOfDay(it.date) }.eachCount()
        return (days - 1 downTo 0).map { offset ->
            val day = today - offset * Fsrs.DAY_MS
            day to (buckets[day] ?: 0)
        }
    }

    val reviewsToday: Int
        get() {
            val today = startOfDay(System.currentTimeMillis())
            return logs.count { startOfDay(it.date) == today }
        }

    val currentStreak: Int
        get() {
            val daysWithReviews = logs.map { startOfDay(it.date) }.toSet()
            if (daysWithReviews.isEmpty()) return 0
            var day = startOfDay(System.currentTimeMillis())
            if (!daysWithReviews.contains(day)) {
                day -= Fsrs.DAY_MS
                if (!daysWithReviews.contains(day)) return 0
            }
            var streak = 0
            while (daysWithReviews.contains(day)) {
                streak++
                day -= Fsrs.DAY_MS
            }
            return streak
        }

    /** Share of mature reviews answered better than "Again". */
    val retentionRate: Double
        get() {
            val mature = logs.filter { it.elapsedDays >= 1.0 }
            if (mature.isEmpty()) return 0.0
            return mature.count { it.rating != Rating.AGAIN.value }.toDouble() / mature.size
        }

    data class SubjectStats(val total: Int, val due: Int, val new: Int, val mature: Int)

    fun statsFor(subject: String): SubjectStats {
        val s = cards.filter { it.subject == subject }
        return SubjectStats(
            total = s.size,
            due = s.count { it.isDue && it.state != CardState.NEW },
            new = s.count { it.state == CardState.NEW },
            mature = s.count { it.stability >= 21 }
        )
    }

    /** Cards falling due over the next 14 days. */
    fun forecast(): List<Pair<Int, Int>> {
        val today = startOfDay(System.currentTimeMillis())
        val buckets = HashMap<Int, Int>()
        for (c in cards) {
            if (c.suspended || c.state != CardState.REVIEW) continue
            val diff = ((startOfDay(c.due) - today) / Fsrs.DAY_MS).toInt()
            if (diff in 0..14) buckets[diff] = (buckets[diff] ?: 0) + 1
        }
        return (0..14).map { it to (buckets[it] ?: 0) }
    }
}
