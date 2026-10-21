package vn.esstar.ainovel.data.source.parser

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Turns a chapter content element into clean paragraphs. */
object ContentCleaner {

    private val BLOCK_TAGS = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "section", "article", "pre", "tr")
    private val HIDDEN_STYLE = Regex("display\\s*:\\s*none|visibility\\s*:\\s*hidden|font-size\\s*:\\s*0", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("[ \\t\\u00A0\\u2000-\\u200B\\u3000]+")

    fun toParagraphs(
        content: Element,
        removeSelectors: List<String> = emptyList(),
        removeLinePatterns: List<Regex> = emptyList(),
    ): List<String> {
        val root = content.clone()
        removeSelectors.forEach { sel -> root.select(sel).remove() }
        root.select("[style]").filter { HIDDEN_STYLE.containsMatchIn(it.attr("style")) }.forEach { it.remove() }
        root.select("[hidden]").remove()

        root.select("br").forEach { it.replaceWith(TextNode("\n")) }
        root.allElements.filter { it !== root && it.tagName() in BLOCK_TAGS }.forEach {
            it.prependChild(TextNode("\n"))
            it.appendChild(TextNode("\n"))
        }

        return root.wholeText()
            .split('\n')
            .asSequence()
            .map { WHITESPACE.replace(it, " ").trim() }
            .filter { it.isNotEmpty() }
            .filter { line -> removeLinePatterns.none { it.containsMatchIn(line) } }
            .toList()
    }
}
