package com.example.astroxplore.core.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

object AstroTextParser {

    /**
     * Parses academic text containing HTML tags and LaTeX macros into a Compose AnnotatedString.
     */
    fun parse(input: String): AnnotatedString {
        if (input.isBlank()) return AnnotatedString("")

        // 1. Pre-process common TeX symbols and normalize whitespace in tags
        var text = input
            .replace(Regex("<\\s*(SUB|SUP|B|I|STRONG|EM)\\s*>", RegexOption.IGNORE_CASE)) { "<${it.groupValues[1].uppercase()}>" }
            .replace(Regex("<\\s*/\\s*(SUB|SUP|B|I|STRONG|EM)\\s*>", RegexOption.IGNORE_CASE)) { "</${it.groupValues[1].uppercase()}>" }
            .replace("\\rm", "")
            .replace("\\it", "")
            .replace("\\,", " ")
            .replace("\\;", " ")
            .replace("\\!", "")
            .replace("{-}", "-")
            .replace("{+}", "+")
            .replace("_{\\odot}", "☉")
            .replace("^{\\odot}", "☉")
            .replace("_{\\0}", "₀")
            .replace("^{\\,}", "")
            .replace("\\odot", "☉")
            .replace("\\oplus", "⊕")
            .replace("\\aa", "Å")
            .replace("\\deg", "°")
            .replace("\\pm", "±")
            .replace("\\sim", "~")
            .replace("\\times", "×")
            .replace("\\mu", "μ")
            .replace("\\nu", "ν")
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\lambda", "λ")
            .replace("\\sigma", "σ")
            .replace("\\pi", "π")
            .replace("\\Delta", "Δ")
            .replace("&Compound;", "") // Strip some weird ADS entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("~", " ")

        return buildAnnotatedString {
            var currentIndex = 0
            
            // Comprehensive regex for academic formatting
            val combinedRegex = Regex(
                """<(SUB|SUP|B|I|STRONG|EM)>(.*?)</\1>|""" + // Group 1 (Tag), 2 (Content)
                """\\ion\{([^}]*)\}\{([^}]*)\}|""" +          // Group 3, 4
                """\\textit\{([^}]*)\}|""" +                  // Group 5
                """\\textbf\{([^}]*)\}|""" +                  // Group 6
                """\^\{([^}]*)\}|""" +                        // Group 7
                """_\{([^}]*)\}|""" +                         // Group 8
                """\^([0-9a-zA-Z])|""" +                       // Group 9
                """_([0-9a-zA-Z])|""" +                        // Group 10
                """\$([^$]*)\$"""                             // Group 11
            , RegexOption.IGNORE_CASE)
            
            val matches = combinedRegex.findAll(text)
            
            for (match in matches) {
                // Append text before the match, stripping any leftover XML
                if (match.range.first > currentIndex) {
                    append(text.substring(currentIndex, match.range.first).replace(Regex("<[^>]*>"), ""))
                }
                
                when {
                    match.groups[1] != null -> { // HTML Tags
                        applyStyle(match.groupValues[1].uppercase(), match.groupValues[2])
                    }
                    match.groups[3] != null -> { // \ion{H}{II}
                        append("${match.groupValues[3]} ${match.groupValues[4]}")
                    }
                    match.groups[5] != null -> { // \textit
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[5]) }
                    }
                    match.groups[6] != null -> { // \textbf
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[6]) }
                    }
                    match.groups[7] != null -> { // ^{...}
                        withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 9.sp)) { append(match.groupValues[7]) }
                    }
                    match.groups[8] != null -> { // _{...}
                        withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 9.sp)) { append(match.groupValues[8]) }
                    }
                    match.groups[9] != null -> { // ^x
                        withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 9.sp)) { append(match.groupValues[9]) }
                    }
                    match.groups[10] != null -> { // _x
                        withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 9.sp)) { append(match.groupValues[10]) }
                    }
                    match.groups[11] != null -> { // $...$
                        // Inner parse for subscripts within math mode
                        val mathContent = match.groupValues[11]
                        appendMathContent(mathContent)
                    }
                }
                currentIndex = match.range.last + 1
            }
            
            if (currentIndex < text.length) {
                append(text.substring(currentIndex).replace(Regex("<[^>]*>"), ""))
            }
        }
    }

    private fun AnnotatedString.Builder.appendMathContent(math: String) {
        var pos = 0
        val mathRegex = Regex("""\^\{([^}]*)\}|_\{([^}]*)\}|\^([0-9a-zA-Z])|_([0-9a-zA-Z])""")
        val mathMatches = mathRegex.findAll(math)
        
        for (m in mathMatches) {
            if (m.range.first > pos) {
                append(math.substring(pos, m.range.first))
            }
            when {
                m.groups[1] != null -> withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 9.sp)) { append(m.groupValues[1]) }
                m.groups[2] != null -> withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 9.sp)) { append(m.groupValues[2]) }
                m.groups[3] != null -> withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 9.sp)) { append(m.groupValues[3]) }
                m.groups[4] != null -> withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 9.sp)) { append(m.groupValues[4]) }
            }
            pos = m.range.last + 1
        }
        if (pos < math.length) append(math.substring(pos))
    }

    private fun AnnotatedString.Builder.applyStyle(tag: String, content: String) {
        when (tag) {
            "SUB" -> withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 9.sp)) { append(content) }
            "SUP" -> withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 9.sp)) { append(content) }
            "B", "STRONG" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(content) }
            "I", "EM" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(content) }
            else -> append(content)
        }
    }
}
