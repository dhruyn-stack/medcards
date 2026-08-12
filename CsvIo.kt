package com.medcards

/**
 * CSV import/export.
 *
 * Expected columns (header row required, order-independent, case-insensitive):
 *   front, back, subject, topic, tags
 * `tags` is semicolon-separated. `front` may contain cloze markup {{c1::...}}.
 */
object CsvIo {

    // ------------------------------------------------------------ parsing (RFC 4180)

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> { row.add(field.toString()); field.setLength(0) }
                    '\n' -> {
                        row.add(field.toString()); field.setLength(0)
                        rows.add(row); row = mutableListOf()
                    }
                    '\r' -> { /* skip */ }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filterNot { it.size == 1 && it[0].isBlank() }
    }

    data class ImportResult(
        val notes: Int = 0,
        val cards: Int = 0,
        val skipped: Int = 0,
        val error: String? = null
    )

    fun import(text: String, store: Store, defaultSubject: String = ""): ImportResult {
        val rows = parse(text)
        val header = rows.firstOrNull()
            ?: return ImportResult(error = "That file is empty.")

        val keys = header.map { it.trim().lowercase() }
        fun col(name: String): Int = keys.indexOf(name)

        val frontIdx = col("front")
        if (frontIdx < 0) {
            return ImportResult(
                error = "No 'front' column found. Header was: ${keys.joinToString(", ")}"
            )
        }
        val backIdx = col("back")
        val subjIdx = col("subject")
        val topicIdx = col("topic")
        val tagsIdx = col("tags")

        fun value(row: List<String>, idx: Int): String =
            if (idx >= 0 && idx < row.size) row[idx].trim() else ""

        var notes = 0
        var made = 0
        var skipped = 0

        for (row in rows.drop(1)) {
            val front = value(row, frontIdx)
            if (front.isBlank()) { skipped++; continue }
            val subject = value(row, subjIdx).ifBlank { defaultSubject }
            val tags = value(row, tagsIdx)
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            made += store.addNote(
                front = front,
                back = value(row, backIdx),
                subject = subject,
                topic = value(row, topicIdx),
                tags = tags,
                imageNames = emptyList()
            )
            notes++
        }
        return ImportResult(notes = notes, cards = made, skipped = skipped)
    }

    // ------------------------------------------------------------ export

    private fun escape(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s

    fun export(cards: List<Card>): String {
        val sb = StringBuilder("front,back,subject,topic,tags\n")
        val seen = HashSet<String>()
        for (c in cards) {
            // Collapse cloze siblings back into a single note.
            if (c.clozeIndex != null) {
                val key = c.front + "|" + c.back + "|" + c.subject
                if (!seen.add(key)) continue
            }
            val cols = listOf(c.front, c.back, c.subject, c.topic, c.tags.joinToString(";"))
            sb.append(cols.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }
}
