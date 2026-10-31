package com.acadbell.app.domain.topic

/**
 * Turns a large pasted blob into clean, individual topic titles.
 *
 * Academic topic lists are messy: numbered lists, roman numerals, bullet glyphs,
 * "Topic 1:" prefixes, trailing page references, wrapped lines. This parser is
 * deliberately deterministic and dependency-free so it can be unit-tested offline.
 *
 * Rules
 *  1. Split on newlines, semicolons and the "•" style separators.
 *  2. Strip leading enumerators: "1.", "1)", "1 -", "(1)", "i.", "a)", "Topic 3:", "-", "•".
 *  3. Strip trailing page markers like "(pp. 12-18)" or "- p.4".
 *  4. Drop empties, pure-number lines and duplicates (case-insensitive).
 *  5. Optionally split on commas ONLY when the line looks like an inline list
 *     (heuristic: many commas, few words between them).
 *  6. Title-case is preserved as typed; whitespace normalised.
 */
object TopicParser {

    private val enumeratorPrefix = Regex(
        "^\\s*(?:topic\\s*\\d+\\s*[:.\\-)]?|" +          // Topic 3:
            "chapter\\s*\\d+\\s*[:.\\-)]?|" +             // Chapter 2:
            "\\(?\\d{1,3}[.)\\]]|" +                     // 1.  2)  (3)
            "\\d{1,3}\\s*[-–]\\s+|" +                    // 12 - 
            "\\(?[ivxIVX]{1,5}[.)]\\s+|" +               // iv.  (roman)
            "\\(?[a-hA-H][.)]\\s+|" +                    // a)  b.
            "[-–—•*·▪◦]+\\s*" +                          // bullets
            ")\\s*",
        RegexOption.IGNORE_CASE
    )

    private val pageRefSuffix = Regex("\\s*[\\(\\[]?\\s*(pp?\\.|pages?)\\s*\\d+[^)\\]]*[\\)\\]]?\\s*$", RegexOption.IGNORE_CASE)
    private val trailingPunct = Regex("[\\s,;:.\\-–—]+$")
    private val multiSpace = Regex("\\s{2,}")

    data class ParseResult(
        val topics: List<String>,
        val rawLineCount: Int,
        val duplicatesRemoved: Int
    )

    /**
     * @param raw the pasted text
     * @param splitCommas also split comma-separated inline lists
     */
    fun parse(raw: String, splitCommas: Boolean = false): ParseResult {
        if (raw.isBlank()) return ParseResult(emptyList(), 0, 0)

        val rawLines = raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split('\n')
            .flatMap { line -> if (line.contains(';')) line.split(';') else listOf(line) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val seen = HashSet<String>()
        val out = ArrayList<String>()
        var dupes = 0

        fun accept(candidate: String) {
            var t = candidate.trim()
            if (t.isEmpty()) return
            // Pure numbers / leftover enumerators are noise.
            if (t.all { it.isDigit() || it == '.' || it == ')' || it == '-' }) return
            if (t.length < 2) return
            val key = t.lowercase()
            if (!seen.add(key)) {
                dupes++
                return
            }
            out.add(t)
        }

        for (line in rawLines) {
            var cleaned = line.replace(enumeratorPrefix, "")
            cleaned = cleaned.replace(pageRefSuffix, "")
            cleaned = cleaned.replace(multiSpace, " ")
            cleaned = cleaned.replace(trailingPunct, "")

            val shouldSplit = splitCommas && looksLikeInlineList(cleaned)
            if (shouldSplit) {
                cleaned.split(',').forEach { piece ->
                    accept(piece.replace(enumeratorPrefix, "").replace(trailingPunct, ""))
                }
            } else {
                accept(cleaned)
            }
        }

        return ParseResult(out, rawLines.size, dupes)
    }

    /** "Cell structure, organelles, mitosis, meiosis" -> true, but a normal sentence stays whole. */
    private fun looksLikeInlineList(line: String): Boolean {
        val commas = line.count { it == ',' }
        if (commas < 2) return false
        val segments = line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.size < 3) return false
        val avgWords = segments.sumOf { it.split(' ').size }.toDouble() / segments.size
        return avgWords <= 6.0
    }

    /** Rough time estimate used only as a helpful default when reviewing pasted topics. */
    fun estimateMinutes(title: String): Int {
        val words = title.split(' ').count { it.isNotBlank() }
        return (5 + words).coerceIn(5, 45)
    }
}
