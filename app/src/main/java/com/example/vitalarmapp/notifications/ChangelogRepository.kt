package com.example.vitalarmapp.notifications

import android.content.Context
import com.example.vitalarmapp.R
import androidx.annotation.VisibleForTesting

internal class ChangelogRepository {

    fun loadEntries(context: Context): List<ChangelogEntry> {
        val changelog = runCatching {
            context.assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        return parseEntries(context, changelog)
    }

    @VisibleForTesting
    internal fun parseEntries(context: Context, changelog: String): List<ChangelogEntry> {
        val entryRegex =
            Regex("""## \[(.+?)] - (.+?)\n(.*?)(?=## \[|\z)""", RegexOption.DOT_MATCHES_ALL)
        val matches = entryRegex.findAll(changelog)

        return matches.map { matchResult ->
            val version = matchResult.groupValues[1].trim()
            val date = matchResult.groupValues[2].trim()
            val body = matchResult.groupValues[3]
            val summary = formatRawBody(body).ifBlank {
                context.getString(R.string.notifications_summary_fallback)
            }

            ChangelogEntry(
                headline = context.getString(
                    R.string.notifications_headline_format,
                    version,
                    date
                ),
                subhead = summary,
            )
        }
            .toList()
    }

    private fun formatRawBody(rawSection: String): String {
        val normalizedLines = rawSection.lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .map { line ->
                when {
                    line.startsWith("### ") -> line.removePrefix("### ").trim()
                    line.startsWith("- ") -> "• " + line.removePrefix("- ").trim()
                    else -> line.trim()
                }
            }

        return normalizedLines.joinToString("\n").trim()
    }

    companion object {
        private const val CHANGELOG_ASSET = "Changelog.md"
    }
}
