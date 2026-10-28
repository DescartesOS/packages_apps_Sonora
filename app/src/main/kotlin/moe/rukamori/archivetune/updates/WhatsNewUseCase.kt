/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.updates

import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.Updater
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import java.util.Locale
import javax.inject.Inject

enum class WhatsNewCategory { FEATURES, FIXES, IMPROVEMENTS }

data class WhatsNewSection(
    val category: WhatsNewCategory,
    val entries: ImmutableList<String>,
)

data class WhatsNewRelease(
    val url: String,
    val sections: ImmutableList<WhatsNewSection>,
)

class WhatsNewUseCase @Inject constructor(
    private val repository: WhatsNewRepository,
) {
    suspend fun shouldShow(version: String): Boolean = repository.registerVersion(version)

    suspend fun acknowledge(version: String) = repository.acknowledge(version)

    fun releasePageUrl(version: String? = null): String = Updater.getReleasePageUrl(version)

    suspend fun load(version: String): WhatsNewRelease? = withContext(Dispatchers.IO) {
        val release = repository.getRelease(version) ?: return@withContext null
        val url = release.htmlUrl.takeIf { it.startsWith("${releasePageUrl()}/tag/") } ?: releasePageUrl(version)
        withContext(Dispatchers.Default) {
            WhatsNewRelease(url, extractHighlights(release.body.orEmpty()))
        }
    }

    private fun extractHighlights(markdown: String): ImmutableList<WhatsNewSection> {
        val headings = mutableListOf<ReleaseHeading>()
        val candidates = mutableListOf<Highlight>()
        val seen = mutableSetOf<String>()
        var sectionId = 0

        Parser.builder().build().parse(markdown).accept(object : AbstractVisitor() {
            override fun visit(heading: Heading) {
                while (headings.isNotEmpty() && headings.last().level >= heading.level) headings.removeAt(headings.lastIndex)
                val title = plainText(heading).lowercase(Locale.ROOT)
                headings.add(
                    ReleaseHeading(
                        level = heading.level,
                        category = headingCategory(title) ?: headings.lastOrNull()?.category,
                        excluded = headings.lastOrNull()?.excluded == true || excludedSection.containsMatchIn(title),
                    ),
                )
                sectionId++
            }

            override fun visit(item: ListItem) {
                if (headings.lastOrNull()?.excluded != true) {
                    var child = item.firstChild
                    while (child != null && child !is Paragraph) child = child.next
                    val text = child?.let(::plainText).orEmpty()
                    val category = headings.lastOrNull()?.category ?: changeCategory(text)
                    val preview = abbreviate(text)
                    if (preview.isNotBlank() && category != null && !metadata.containsMatchIn(text) && seen.add(preview.lowercase(Locale.ROOT))) {
                        candidates.add(Highlight(category, sectionId, candidates.size, preview))
                    }
                }
                visitChildren(item)
            }
        })

        return ImmutableList.copyOf(
            WhatsNewCategory.entries.mapNotNull { category ->
                val changes = candidates.filter { it.category == category }
                val selected = changes.distinctBy { it.section }.take(2).toMutableList()
                changes.forEach { change ->
                    if (selected.size < 2 && change !in selected) selected.add(change)
                }
                if (selected.isEmpty()) null else WhatsNewSection(
                    category,
                    ImmutableList.copyOf(selected.sortedBy { it.order }.map { it.text }),
                )
            },
        )
    }

    private fun plainText(node: Node): String {
        val text = StringBuilder()
        node.accept(object : AbstractVisitor() {
            override fun visit(node: Text) { text.append(node.literal) }
            override fun visit(node: Code) { text.append(node.literal) }
            override fun visit(node: SoftLineBreak) { text.append(' ') }
            override fun visit(node: HardLineBreak) { text.append(' ') }
            override fun visit(node: Image) = Unit
        })
        return whitespace.replace(text, " ").trim()
    }

    private fun headingCategory(title: String): WhatsNewCategory? = when {
        fixHeading.containsMatchIn(title) -> WhatsNewCategory.FIXES
        improvementHeading.containsMatchIn(title) -> WhatsNewCategory.IMPROVEMENTS
        featureHeading.containsMatchIn(title) -> WhatsNewCategory.FEATURES
        else -> null
    }

    private fun changeCategory(text: String): WhatsNewCategory? = when {
        fixChange.containsMatchIn(text) -> WhatsNewCategory.FIXES
        improvementChange.containsMatchIn(text) -> WhatsNewCategory.IMPROVEMENTS
        featureChange.containsMatchIn(text) -> WhatsNewCategory.FEATURES
        else -> null
    }

    private fun abbreviate(text: String): String {
        if (text.length <= 180) return text
        val boundary = text.lastIndexOf(' ', 179)
        val end = if (boundary > 0) boundary else if (text[178].isHighSurrogate()) 178 else 179
        return text.take(end).trimEnd() + "…"
    }

    private data class ReleaseHeading(val level: Int, val category: WhatsNewCategory?, val excluded: Boolean)
    private data class Highlight(val category: WhatsNewCategory, val section: Int, val order: Int, val text: String)

    private companion object {
        val whitespace = Regex("\\s+")
        val excludedSection = Regex("\\b(contributors?|credits?|acknowledg\\w*|dependencies|internal|refactors?|maintenance|workflows?|assets|pull requests?|referenced issues|resolved and referenced issues)\\b|full changelog|download links?")
        val metadata = Regex("^(contributors?|related|pull requests?|issues?|https?://|#\\d+\\b)", RegexOption.IGNORE_CASE)
        val fixHeading = Regex("\\b(fix(?:es|ed)?|bugs?|resolved)\\b")
        val improvementHeading = Regex("\\b(improvements?|enhancements?|performance|stability|optimizations?)\\b")
        val featureHeading = Regex("\\b(features?|additions?)\\b")
        val fixChange = Regex("^(fix(?:ed|es)?|correct(?:ed|s)?|resolve[ds]?|prevent(?:ed|s)?|eliminate[ds]?|repair(?:ed|s)?|restore[ds]?)\\b", RegexOption.IGNORE_CASE)
        val improvementChange = Regex("^(improve[ds]?|enhance[ds]?|optimize[ds]?|refine[ds]?|update[ds]?|reduce[ds]?|simplif(?:y|ied)|unif(?:y|ied)|harden(?:ed)?|rework(?:ed)?)\\b", RegexOption.IGNORE_CASE)
        val featureChange = Regex("^(add(?:ed|s)?|introduce[ds]?|integrate[ds]?|enable[ds]?|new|support(?:ed|s)?)\\b", RegexOption.IGNORE_CASE)
    }
}
