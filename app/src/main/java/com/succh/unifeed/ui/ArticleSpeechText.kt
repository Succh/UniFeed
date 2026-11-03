package com.succh.unifeed.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import kotlin.math.min

/**
 * 把文章 HTML 收成 TTS 可朗读的纯文本，并按段落/句子切成短 utterance。
 * 绝不能把标签名读出来。
 */
object ArticleSpeechText {

    /** 低于系统 4000 上限，给引擎留余量 */
    const val DEFAULT_MAX_UTTERANCE = 3500

    /**
     * 标题 + 正文纯文本切段。正文空则只读标题；两者都空则返回空列表。
     */
    fun speakableChunks(
        title: String,
        html: String,
        maxLen: Int = DEFAULT_MAX_UTTERANCE
    ): List<String> {
        val heading = title.trim()
        val body = htmlToPlain(html)
        val source = buildString {
            if (heading.isNotEmpty()) append(heading)
            if (body.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append(body)
            }
        }
        return splitUtterances(source, maxLen)
    }

    /** HTML 去标签、去脚本样式、解码实体，得到纯文本 */
    fun htmlToPlain(html: String): String {
        if (html.isBlank()) return ""
        return try {
            val doc = Jsoup.parse(html)
            doc.select("script, style, noscript, iframe, svg").remove()
            for (el in doc.select("br, hr")) {
                el.replaceWith(TextNode("\n"))
            }
            for (el in doc.select("p, h1, h2, h3, h4, h5, h6, li, blockquote, pre, tr")) {
                el.prepend("\n")
                el.append("\n")
            }
            val raw = (doc.body() ?: doc).wholeText()
            normalizePlain(raw)
        } catch (_: Exception) {
            stripTagsFallback(html)
        }
    }

    fun splitUtterances(text: String, maxLen: Int = DEFAULT_MAX_UTTERANCE): List<String> {
        val limit = maxLen.coerceAtLeast(1)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val paragraphs = trimmed
            .split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val result = ArrayList<String>()
        for (paragraph in paragraphs) {
            if (paragraph.length <= limit) {
                result.add(paragraph)
            } else {
                result.addAll(splitLongParagraph(paragraph, limit))
            }
        }
        return result
    }

    private fun splitLongParagraph(paragraph: String, maxLen: Int): List<String> {
        return pack(splitSentences(paragraph), maxLen)
    }

    private fun splitSentences(paragraph: String): List<String> {
        val sentences = ArrayList<String>()
        val buf = StringBuilder()
        var i = 0
        while (i < paragraph.length) {
            buf.append(paragraph[i])
            if (isSentenceEnd(paragraph, i)) {
                val piece = buf.toString().trim()
                if (piece.isNotEmpty()) sentences.add(piece)
                buf.clear()
            }
            i++
        }
        val tail = buf.toString().trim()
        if (tail.isNotEmpty()) sentences.add(tail)
        return sentences
    }

    private fun isSentenceEnd(text: String, index: Int): Boolean {
        val c = text[index]
        if (c == '。' || c == '！' || c == '？' || c == '；' || c == '…') return true
        if (c != '.' && c != '!' && c != '?') return false
        val next = text.getOrNull(index + 1)
        return next == null || next.isWhitespace()
    }

    private fun pack(parts: List<String>, maxLen: Int): List<String> {
        val out = ArrayList<String>()
        val buf = StringBuilder()
        for (part in parts) {
            if (part.length > maxLen) {
                if (buf.isNotEmpty()) {
                    out.add(buf.toString())
                    buf.clear()
                }
                out.addAll(hardSplit(part, maxLen))
                continue
            }
            val extra = if (buf.isEmpty()) 0 else 1
            if (buf.isNotEmpty() && buf.length + extra + part.length > maxLen) {
                out.add(buf.toString())
                buf.clear()
            }
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(part)
        }
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
    }

    private fun hardSplit(s: String, maxLen: Int): List<String> {
        val out = ArrayList<String>()
        var start = 0
        val n = s.length
        while (start < n) {
            var end = min(start + maxLen, n)
            if (end < n) {
                val space = s.lastIndexOf(' ', end - 1)
                if (space > start) end = space
            }
            val piece = s.substring(start, end).trim()
            if (piece.isNotEmpty()) out.add(piece)
            start = end
            while (start < n && s[start].isWhitespace()) start++
        }
        return out
    }

    private fun normalizePlain(raw: String): String {
        return raw
            .replace('\u00a0', ' ')
            .replace(Regex("[\\t\\x0B\\f\\r]+"), " ")
            .split('\n')
            .joinToString("\n") { it.trim().replace(Regex(" {2,}"), " ") }
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun stripTagsFallback(html: String): String {
        return html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("&amp;", RegexOption.IGNORE_CASE), "&")
            .replace(Regex("&lt;", RegexOption.IGNORE_CASE), "<")
            .replace(Regex("&gt;", RegexOption.IGNORE_CASE), ">")
            .replace(Regex("&quot;", RegexOption.IGNORE_CASE), "\"")
            .replace(Regex("&#39;"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
