package com.succh.unifeed.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.succh.unifeed.data.search.EntrySearch

/**
 * 把关键词命中段标成背景高亮，供搜索结果列表使用。
 */
fun highlightAnnotated(text: String, query: String, background: Color): AnnotatedString {
    val ranges = EntrySearch.findRanges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { range ->
            addStyle(
                SpanStyle(background = background, fontWeight = FontWeight.SemiBold),
                range.first,
                range.last + 1
            )
        }
    }
}

@Composable
fun rememberHighlightedText(text: String, query: String): AnnotatedString {
    val background = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    return remember(text, query, background) {
        highlightAnnotated(text, query, background)
    }
}
