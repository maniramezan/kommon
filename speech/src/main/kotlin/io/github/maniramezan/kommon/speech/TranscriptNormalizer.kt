package io.github.maniramezan.kommon.speech

import java.util.Locale

/** Splits transcripts into comparable words for [TranscriptAlignment]. */
internal object TranscriptNormalizer {
    private const val SEPARATING_DASHES = "-‐‑‒–—/"
    private const val APOSTROPHE = '\''
    private const val RIGHT_SINGLE_QUOTE = '’'
    private const val LEFT_SINGLE_QUOTE = '‘'

    private val punctuationAndSymbolTypes: Set<Int> =
        setOf(
            Character.CONNECTOR_PUNCTUATION,
            Character.DASH_PUNCTUATION,
            Character.START_PUNCTUATION,
            Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
            Character.MATH_SYMBOL,
            Character.CURRENCY_SYMBOL,
            Character.MODIFIER_SYMBOL,
            Character.OTHER_SYMBOL,
        ).map { it.toInt() }.toSet()

    /**
     * Splits [text] into words, keeping each word's original spelling.
     *
     * Whitespace, hyphens, dashes, and slashes separate words. Punctuation at the edges of a word
     * is dropped; apostrophes inside a word (`don't`, `o'clock`) are kept and straightened.
     */
    fun words(text: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (isSeparator(codePoint)) {
                appendWord(current, words)
            } else {
                current.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        appendWord(current, words)
        return words
    }

    /** The case-folded comparison key for [word] under [locale]. */
    fun key(
        word: String,
        locale: Locale,
    ): String = straighten(word).lowercase(locale)

    private fun appendWord(
        current: StringBuilder,
        words: MutableList<String>,
    ) {
        if (current.isEmpty()) return
        val trimmed = trimEdges(straighten(current.toString()))
        current.setLength(0)
        if (trimmed.isNotEmpty()) words += trimmed
    }

    private fun straighten(text: String): String = text.replace(RIGHT_SINGLE_QUOTE, APOSTROPHE).replace(LEFT_SINGLE_QUOTE, APOSTROPHE)

    private fun isSeparator(codePoint: Int): Boolean =
        Character.isWhitespace(codePoint) ||
            Character.isSpaceChar(codePoint) ||
            (codePoint < Character.MIN_SUPPLEMENTARY_CODE_POINT && SEPARATING_DASHES.indexOf(codePoint.toChar()) >= 0)

    private fun isEdgePunctuation(codePoint: Int): Boolean =
        codePoint == APOSTROPHE.code || Character.getType(codePoint) in punctuationAndSymbolTypes

    private fun trimEdges(text: String): String {
        val codePoints = text.codePoints().toArray()
        var start = 0
        var end = codePoints.size
        while (start < end && isEdgePunctuation(codePoints[start])) start++
        while (end > start && isEdgePunctuation(codePoints[end - 1])) end--
        return String(codePoints, start, end - start)
    }
}
