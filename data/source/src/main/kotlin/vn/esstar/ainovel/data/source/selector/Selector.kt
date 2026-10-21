package vn.esstar.ainovel.data.source.selector

import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Tiny selector language on top of Jsoup CSS:
 *
 * ```
 * css@attr|regex:pattern
 * ```
 * - `css` – a Jsoup CSS query; empty means "the element itself".
 * - `@attr` – what to extract: `text` (default), `ownText`, `html`, or any attribute name.
 *   URL-like attributes (`href`, `src`, `data-*`) are resolved to absolute URLs.
 * - `|regex:pattern` – optional post-processing; keeps capturing group 1 (or the whole match).
 */
data class Selector(
    val css: String,
    val attr: String,
    val regex: Regex?,
) {
    fun select(root: Element): Elements = if (css.isBlank()) Elements(root) else root.select(css)

    fun first(root: Element): String? = select(root).firstOrNull()?.let { extract(it) }?.takeIf { it.isNotBlank() }

    fun all(root: Element): List<String> = select(root).mapNotNull { extract(it).takeIf { v -> v.isNotBlank() } }

    fun extract(element: Element): String {
        val raw = when (attr) {
            "text" -> element.text()
            "ownText" -> element.ownText()
            "html" -> element.html()
            else -> {
                val isUrl = attr in URL_ATTRS || attr.startsWith("data-")
                val raw = element.attr(attr).trim()
                // `javascript:` and bare `#` links are not navigable (disabled prev/next buttons); Jsoup would
                // otherwise resolve `#` to `<page url>#`.
                if (isUrl && (raw.startsWith("javascript:", ignoreCase = true) || raw == "#")) {
                    ""
                } else {
                    val abs = if (isUrl) element.absUrl(attr) else ""
                    abs.ifBlank { raw }
                }
            }
        }
        val value = raw.replace(' ', ' ').trim()
        val rx = regex ?: return value
        val m = rx.find(value) ?: return ""
        return (m.groupValues.getOrNull(1) ?: m.value).trim()
    }

    companion object {
        private val URL_ATTRS = setOf("href", "src", "poster", "action")

        fun parse(spec: String): Selector {
            var rest = spec.trim()
            var regex: Regex? = null
            val pipe = rest.indexOf("|regex:")
            if (pipe >= 0) {
                regex = Regex(rest.substring(pipe + "|regex:".length))
                rest = rest.substring(0, pipe)
            }
            val at = rest.lastIndexOf('@')
            // An '@' inside a CSS attribute value like [data-x="a@b"] is not supported; keep it simple.
            val css = if (at >= 0) rest.substring(0, at) else rest
            val attr = if (at >= 0) rest.substring(at + 1).ifBlank { "text" } else "text"
            return Selector(css.trim(), attr.trim(), regex)
        }
    }
}

fun String.toSelector(): Selector = Selector.parse(this)

fun Element.first(spec: String?): String? = spec?.let { Selector.parse(it).first(this) }

fun Element.all(spec: String?): List<String> = spec?.let { Selector.parse(it).all(this) } ?: emptyList()
