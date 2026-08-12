package com.medcards

import kotlinx.serialization.Serializable
import java.util.UUID

// ---------------------------------------------------------------- Rating

enum class Rating(val value: Int, val label: String) {
    AGAIN(1, "Again"),
    HARD(2, "Hard"),
    GOOD(3, "Good"),
    EASY(4, "Easy")
}

enum class CardState { NEW, LEARNING, REVIEW }

// ---------------------------------------------------------------- Card

@Serializable
data class Card(
    val id: String = UUID.randomUUID().toString(),

    /** Front text. Supports cloze syntax: {{c1::hidden text}} */
    val front: String = "",
    val back: String = "",

    /** e.g. "Pathology" */
    val subject: String = "",
    /** The integrating topic shared across subjects, e.g. "Diabetes Mellitus" */
    val topic: String = "",
    val tags: List<String> = emptyList(),

    /** File names stored in filesDir/images/ */
    val imageNames: List<String> = emptyList(),

    /** If non-null this card is one cloze deletion of [front]. */
    val clozeIndex: Int? = null,

    // --- scheduling state (FSRS) ---
    val state: CardState = CardState.NEW,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    /** epoch millis */
    val due: Long = System.currentTimeMillis(),
    val lastReview: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val suspended: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isDue: Boolean get() = !suspended && due <= System.currentTimeMillis()

    /** Text shown on the front, with the target cloze hidden. */
    val renderedFront: String
        get() = clozeIndex?.let { Cloze.render(front, revealing = null, target = it) }
            ?: Cloze.stripAll(front)

    /** Text shown on the back, with the target cloze revealed. */
    val renderedBack: String
        get() {
            val idx = clozeIndex ?: return back
            val revealed = Cloze.render(front, revealing = idx, target = idx)
            return if (back.isBlank()) revealed else revealed + "\n\n" + back
        }
}

// ---------------------------------------------------------------- Cloze

object Cloze {

    /** Matches {{c1::answer}} or {{c1::answer::hint}} */
    private val regex = Regex("""\{\{c(\d+)::(.*?)(?:::(.*?))?}}""", RegexOption.DOT_MATCHES_ALL)

    data class Item(val index: Int, val answer: String, val hint: String?, val range: IntRange)

    fun items(text: String): List<Item> =
        regex.findAll(text).mapNotNull { m ->
            val idx = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val hint = m.groups[3]?.value
            Item(idx, m.groupValues[2], hint, m.range)
        }.toList()

    fun indices(text: String): List<Int> = items(text).map { it.index }.distinct().sorted()

    fun hasCloze(text: String): Boolean = items(text).isNotEmpty()

    /** Removes all cloze markup, leaving plain answers. */
    fun stripAll(text: String): String {
        val sb = StringBuilder(text)
        for (item in items(text).reversed()) {
            sb.replace(item.range.first, item.range.last + 1, item.answer)
        }
        return sb.toString()
    }

    /**
     * Renders [text] with the [target] cloze blanked out (or revealed when
     * [revealing] equals it); every other cloze is shown plainly.
     */
    fun render(text: String, revealing: Int?, target: Int): String {
        val sb = StringBuilder(text)
        for (item in items(text).reversed()) {
            val replacement = if (item.index == target) {
                when {
                    revealing == item.index -> "[ ${item.answer} ]"
                    !item.hint.isNullOrBlank() -> "[ … ${item.hint} ]"
                    else -> "[ … ]"
                }
            } else {
                item.answer
            }
            sb.replace(item.range.first, item.range.last + 1, replacement)
        }
        return sb.toString()
    }
}

// ---------------------------------------------------------------- Logs & settings

@Serializable
data class ReviewLog(
    val id: String = UUID.randomUUID().toString(),
    val cardId: String,
    val date: Long,
    val rating: Int,
    val elapsedDays: Double,
    val scheduledDays: Double,
    val subject: String,
    val topic: String
)

@Serializable
data class AppSettings(
    val desiredRetention: Double = 0.9,
    /** Max cards per normal study session. */
    val sessionSize: Int = 20,
    /** Max cards per integrated (topic) session. */
    val topicSessionSize: Int = 15,
    val newCardsPerDay: Int = 20,
    val fsrsWeights: List<Double> = Fsrs.DEFAULT_WEIGHTS
)

@Serializable
data class LibraryData(
    val cards: List<Card> = emptyList(),
    val logs: List<ReviewLog> = emptyList(),
    val settings: AppSettings = AppSettings()
)

// ---------------------------------------------------------------- Subjects

object Mbbs {
    val subjects = listOf(
        "Anatomy",
        "Physiology",
        "Biochemistry",
        "Pathology",
        "Pharmacology",
        "Microbiology",
        "Forensic Medicine",
        "Community Medicine",
        "Medicine",
        "Surgery",
        "Obstetrics & Gynaecology",
        "Paediatrics",
        "ENT",
        "Ophthalmology",
        "Orthopaedics",
        "Psychiatry",
        "Dermatology",
        "Radiology",
        "Anaesthesia"
    )

    /**
     * Display order used to interleave integrated sessions
     * (basic sciences first, then clinical).
     */
    fun order(subject: String): Int {
        val i = subjects.indexOf(subject)
        return if (i >= 0) i else subjects.size
    }
}
