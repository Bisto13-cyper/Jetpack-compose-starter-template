package com.superapp.app.features.editor.syntax

import com.superapp.app.features.editor.data.EditorSettings
import com.superapp.app.features.editor.model.Token
import com.superapp.app.features.editor.model.TokenStyle

/**
 * Regex/state-machine based tokenizer. Not a full parser — but it produces
 * accurate highlighting for keywords, strings, numbers, comments, operators,
 * function calls, and HTML/CSS structure. Runs on a background dispatcher.
 *
 * If a proper editor library (e.g. Sora Editor) is added later, replace
 * [Highlighter.highlight] with a call into that engine and keep the same
 * signature returning List<Token>.
 */
object Highlighter {

    fun highlight(text: String, languageId: String, settings: EditorSettings): List<Token> {
        if (text.length > settings.largeFileLimitBytes) return emptyList()
        val lang = LanguageRegistry.all.firstOrNull { it.id == languageId } ?: return emptyList()
        return when (lang.mode) {
            LanguageMode.HTML -> highlightHtml(text)
            LanguageMode.CSS -> highlightCss(text)
            LanguageMode.PYTHON -> highlightPython(text, lang)
            LanguageMode.C_LIKE -> highlightCLike(text, lang)
        }
    }

    // ----------------------------------------------------------------------
    // C-like (C, C++, JS, Kotlin)
    // ----------------------------------------------------------------------

    private fun highlightCLike(text: String, lang: Language): List<Token> {
        val tokens = ArrayList<Token>(text.length / 8)
        var i = 0
        val n = text.length

        while (i < n) {
            val c = text[i]

            // Line comment
            if (lang.lineComment != null && text.startsWith(lang.lineComment, i)) {
                var j = i + lang.lineComment.length
                while (j < n && text[j] != '\n') j++
                tokens.add(Token(i, j, TokenStyle.COMMENT)); i = j; continue
            }

            // Block comment
            if (lang.blockCommentStart != null && text.startsWith(lang.blockCommentStart, i)) {
                val end = text.indexOf(lang.blockCommentEnd ?: "", i + lang.blockCommentStart.length)
                val j = if (end < 0) n else end + (lang.blockCommentEnd?.length ?: 0)
                tokens.add(Token(i, j, TokenStyle.COMMENT)); i = j; continue
            }

            // Strings (longer delimiters first)
            val delim = lang.stringDelimiters.sortedByDescending { it.length }
                .firstOrNull { text.startsWith(it, i) }
            if (delim != null) {
                val j = findStringEnd(text, i + delim.length, delim)
                tokens.add(Token(i, j, TokenStyle.STRING)); i = j; continue
            }

            // Number
            if (c.isDigit() || (c == '.' && i + 1 < n && text[i + 1].isDigit())) {
                var j = i
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == '_')) j++
                tokens.add(Token(i, j, TokenStyle.NUMBER)); i = j; continue
            }

            // Identifier / keyword / function
            if (c.isLetter() || c == '_' || c == '@' || c == '#') {
                var j = i + 1
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                val word = text.substring(i, j)
                val style = when {
                    word in lang.keywords -> TokenStyle.KEYWORD
                    word in lang.types -> TokenStyle.TYPE
                    word in lang.constants -> TokenStyle.CONSTANT
                    isFollowedByCall(text, j) -> TokenStyle.FUNCTION
                    else -> null
                }
                if (style != null) tokens.add(Token(i, j, style))
                i = j; continue
            }

            // Operator
            if (c in "=+-*/%<>!&|^~?:.") {
                var j = i + 1
                while (j < n && text[j] in "=+-*/%<>!&|^~?:.") j++
                tokens.add(Token(i, j, TokenStyle.OPERATOR)); i = j; continue
            }

            i++
        }
        return tokens
    }

    // ----------------------------------------------------------------------
    // Python
    // ----------------------------------------------------------------------

    private fun highlightPython(text: String, lang: Language): List<Token> {
        // Reuse C-like logic; Python delimiters and keywords already set
        return highlightCLike(text, lang)
    }

    // ----------------------------------------------------------------------
    // HTML
    // ----------------------------------------------------------------------

    private fun highlightHtml(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (text.startsWith("<!--", i)) {
                val end = text.indexOf("-->", i + 4)
                val j = if (end < 0) n else end + 3
                tokens.add(Token(i, j, TokenStyle.COMMENT)); i = j; continue
            }
            if (text[i] == '<') {
                var j = i + 1
                while (j < n && text[j] != '>') j++
                if (j < n) j++
                // crude: whole tag as TAG, attributes later refined
                tokens.add(Token(i, j, TokenStyle.TAG))
                // scan for attr= inside
                var k = i + 1
                while (k < j) {
                    if (text[k].isLetter()) {
                        var a = k
                        while (a < j && (text[a].isLetterOrDigit() || text[a] == '-' || text[a] == ':')) a++
                        if (a < j && text[a] == '=') {
                            tokens.add(Token(k, a, TokenStyle.ATTRIBUTE))
                            // string value
                            a++
                            if (a < j && (text[a] == '"' || text[a] == '\'')) {
                                val q = text[a]
                                val end = text.indexOf(q, a + 1)
                                if (end in (a + 1) until j) {
                                    tokens.add(Token(a, end + 1, TokenStyle.STRING))
                                    k = end + 1; continue
                                }
                            }
                        }
                        k = a
                    } else k++
                }
                i = j; continue
            }
            if (text[i] == '"' || text[i] == '\'') {
                val q = text[i]
                val end = text.indexOf(q, i + 1)
                val j = if (end < 0) n else end + 1
                tokens.add(Token(i, j, TokenStyle.STRING)); i = j; continue
            }
            i++
        }
        return tokens
    }

    // ----------------------------------------------------------------------
    // CSS
    // ----------------------------------------------------------------------

    private fun highlightCss(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (text.startsWith("/*", i)) {
                val end = text.indexOf("*/", i + 2)
                val j = if (end < 0) n else end + 2
                tokens.add(Token(i, j, TokenStyle.COMMENT)); i = j; continue
            }
            if (text[i] == '"' || text[i] == '\'') {
                val q = text[i]
                val end = text.indexOf(q, i + 1)
                val j = if (end < 0) n else end + 1
                tokens.add(Token(i, j, TokenStyle.STRING)); i = j; continue
            }
            if (text[i].isDigit() || (text[i] == '#' && i + 1 < n && text[i + 1].isLetterOrDigit())) {
                var j = i + 1
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == '%' || text[j] == '#')) j++
                tokens.add(Token(i, j, TokenStyle.NUMBER)); i = j; continue
            }
            if (text[i].isLetter() || text[i] == '-' || text[i] == '_') {
                var j = i + 1
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '-' || text[j] == '_')) j++
                val word = text.substring(i, j)
                // property if followed by :
                var k = j
                while (k < n && text[k].isWhitespace()) k++
                val style = if (k < n && text[k] == ':') TokenStyle.PROPERTY else TokenStyle.TAG
                tokens.add(Token(i, j, style)); i = j; continue
            }
            i++
        }
        return tokens
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private fun findStringEnd(text: String, from: Int, delim: String): Int {
        var i = from
        val n = text.length
        while (i < n) {
            if (text[i] == '\\' && i + 1 < n) { i += 2; continue }
            if (text.startsWith(delim, i)) return i + delim.length
            i++
        }
        return n
    }

    private fun isFollowedByCall(text: String, afterIdent: Int): Boolean {
        var i = afterIdent
        while (i < text.length && text[i].isWhitespace()) i++
        return i < text.length && text[i] == '('
    }
}
