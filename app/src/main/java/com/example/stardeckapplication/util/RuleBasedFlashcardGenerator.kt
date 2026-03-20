package com.example.stardeckapplication.util

/**
 * Simple rule-based flashcard generator.
 *
 * It looks for lines like:
 *   Term - definition
 *   Term: definition
 *
 * If pattern is not found, it will use a simple sentence-based rule.
 */
class RuleBasedFlashcardGenerator {

    data class GeneratedCard(
        val front: String,
        val back: String
    )

    fun generate(rawText: String): List<GeneratedCard> {
        val text = rawText.trim()
        if (text.length < 10) return emptyList()

        val result = mutableListOf<GeneratedCard>()

        // First, use line-based patterns: "Term - definition" or "Term: definition"
        val lines = text.lines()
        for (line in lines) {
            val card = generateFromLinePattern(line)
            if (card != null) {
                result += card
            }
        }

        // If we already found enough cards, return
        if (result.size >= 20) {
            return result
        }

        // Second, simple sentence-based fallback for remaining text
        val sentences = splitIntoSentences(text)
        for (sentence in sentences) {
            if (result.size >= 40) break

            val card = generateFromSentence(sentence)
            if (card != null) {
                result += card
            }
        }

        return result
    }

    private fun generateFromLinePattern(line: String): GeneratedCard? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // Pattern 1: Term - definition
        val dashIndex = trimmed.indexOf(" - ")
        if (dashIndex > 0 && dashIndex < trimmed.length - 3) {
            val term = trimmed.substring(0, dashIndex).trim()
            val def = trimmed.substring(dashIndex + 3).trim()
            if (term.isNotEmpty() && def.isNotEmpty()) {
                return GeneratedCard(
                    front = term,
                    back = def
                )
            }
        }

        // Pattern 2: Term: definition
        val colonIndex = trimmed.indexOf(": ")
        if (colonIndex > 0 && colonIndex < trimmed.length - 2) {
            val term = trimmed.substring(0, colonIndex).trim()
            val def = trimmed.substring(colonIndex + 2).trim()
            if (term.isNotEmpty() && def.isNotEmpty()) {
                return GeneratedCard(
                    front = term,
                    back = def
                )
            }
        }

        return null
    }

    private fun splitIntoSentences(text: String): List<String> {
        // Very simple split by ., ?, !
        val raw = text.split('.', '?', '!')
        return raw.map { it.trim() }
            .filter { it.length > 15 }
    }

    private fun generateFromSentence(sentence: String): GeneratedCard? {
        val trimmed = sentence.trim()
        if (trimmed.length < 20) return null

        val words = trimmed.split(Regex("\\s+"))
        if (words.size < 4) return null

        // Front: first few words as a question
        val frontWords = words.take(6).joinToString(" ")
        val front = "$frontWords ?"

        // Back: the whole sentence
        val back = trimmed

        return GeneratedCard(
            front = front,
            back = back
        )
    }
}
