package com.example.astroxplore.core.util

object AdsTexSanitizer {

    /**
     * Sanitizes raw NASA ADS TeX/HTML strings into clean, human-readable UTF-8 text.
     */
    fun sanitize(input: String): String {
        if (input.isBlank()) return ""
        
        var result = input
        
        // 1. Normalize tags with spaces (e.g., < SUB > -> <SUB>)
        result = result
            .replace(Regex("<\\s*(SUB|SUP|B|I|STRONG|EM)\\s*>", RegexOption.IGNORE_CASE)) { "<${it.groupValues[1].uppercase()}>" }
            .replace(Regex("<\\s*/\\s*(SUB|SUP|B|I|STRONG|EM)\\s*>", RegexOption.IGNORE_CASE)) { "</${it.groupValues[1].uppercase()}>" }
        
        // 2. Specific tag conversion for common symbols before general stripping
        result = result
            .replace(Regex("<SUB>(.*?)</SUB>", RegexOption.IGNORE_CASE)) { toSubscript(it.groupValues[1]) }
            .replace(Regex("<SUP>(.*?)</SUP>", RegexOption.IGNORE_CASE)) { toSuperscript(it.groupValues[1]) }
            .replace(Regex("<B>(.*?)</B>", RegexOption.IGNORE_CASE)) { it.groupValues[1] }
            .replace(Regex("<I>(.*?)</I>", RegexOption.IGNORE_CASE)) { it.groupValues[1] }
        
        // 3. Remove all remaining XML/HTML/MathML tags
        result = result.replace(Regex("<[^>]*>"), "")
        
        // 4. Parse Astronomical Ions (\ion{Fe}{II} -> Fe II)
        result = parseAstroIons(result)

        // 4b. Remove TeX text formatting macros (\textit{...} -> ..., \textbf{...} -> ...)
        result = result.replace(Regex("""\\(?:textit|textbf|emph|textrm|text)\{([^}]*)\}""")) { it.groupValues[1] }
        
        // 5. Common TeX symbols to Unicode
        result = result
            .replace("\\aa", "Å")
            .replace("\\deg", "°")
            .replace("\\pm", "±")
            .replace("\\sim", "~")
            .replace("\\odot", "☉")
            .replace("\\oplus", "⊕")
            .replace("\\jupiter", "♃")
            .replace("\\times", "×")
            .replace("\\nu", "ν")
            .replace("\\mu", "μ")
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\lambda", "λ")
            .replace("\\sigma", "σ")
            .replace("\\pi", "π")
            .replace("\\Delta", "Δ")
            
        // 6. Basic math mode sub/superscripts to Unicode
        result = result.replace(Regex("""\$([^$]*)\$""")) { match ->
            val content = match.groupValues[1]
            content
                .replace(Regex("""\^\{([^}]*)\}""")) { toSuperscript(it.groupValues[1]) }
                .replace(Regex("""_\{([^}]*)\}""")) { toSubscript(it.groupValues[1]) }
                .replace(Regex("""\^([0-9a-z])""", RegexOption.IGNORE_CASE)) { toSuperscript(it.groupValues[1]) }
                .replace(Regex("""_([0-9a-z])""", RegexOption.IGNORE_CASE)) { toSubscript(it.groupValues[1]) }
        }

        // 7. Final cleanup
        return result
            .replace("\\$", "$")
            .replace("\\%", "%")
            .replace("~", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun formatForLatex(input: String): String {
        if (input.isBlank()) return ""
        return input
            .replace(Regex("<SUB>(.*?)</SUB>", RegexOption.IGNORE_CASE)) { "_{${it.groupValues[1]}}" }
            .replace(Regex("<SUP>(.*?)</SUP>", RegexOption.IGNORE_CASE)) { "^{${it.groupValues[1]}}" }
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseAstroIons(input: String): String =
        input.replace(Regex("""\\ion\{([^}]+)\}\{([^}]+)\}""")) { match ->
            "${match.groupValues[1]} ${match.groupValues[2]}"
        }

    private fun toSuperscript(input: String): String {
        val map = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
            'n' to 'ⁿ', 'x' to 'ˣ', 'i' to 'ⁱ'
        )
        return input.map { map[it] ?: it }.joinToString("")
    }

    private fun toSubscript(input: String): String {
        val map = mapOf(
            '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
            '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
            '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
            'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ'
        )
        return input.map { map[it] ?: it }.joinToString("")
    }
}
