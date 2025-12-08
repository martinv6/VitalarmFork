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
            val summary = buildFriendlySummary(body, context)

            ChangelogEntry(
                headline = context.getString(
                    R.string.notifications_headline_format,
                    version,
                    date
                ),
                subhead = summary,
            )
        }
            .filter { it.subhead.isNotBlank() }
            .toList()
    }

    private fun buildFriendlySummary(rawSection: String, context: Context): String {
        val added = collectSectionBullets(rawSection, "### Added")
        val changed = collectSectionBullets(rawSection, "### Changed")

        val highlights = mutableListOf<String>()
        if (added.isNotEmpty()) {
            highlights += context.getString(
                R.string.notifications_added_prefix,
                summarizeBullets(added)
            )
        }
        if (changed.isNotEmpty()) {
            highlights += context.getString(
                R.string.notifications_changed_prefix,
                summarizeBullets(changed)
            )
        }

        val summary = highlights.joinToString(" ")
        return summary.ifBlank { fallbackSummary(rawSection, context) }
    }

    private fun fallbackSummary(rawSection: String, context: Context): String {
        val firstBullet = rawSection.lines()
            .map { it.trim() }
            .firstOrNull { it.startsWith("-") }
            ?.removePrefix("-")
            ?.trim()
            ?.let { simplifyText(it) }
            ?.takeIf { it.isNotBlank() }

        return firstBullet ?: context.getString(R.string.notifications_summary_fallback)
    }

    private fun collectSectionBullets(body: String, heading: String): List<String> {
        val lines = body.lines()
        var inSection = false
        val bullets = mutableListOf<String>()

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("### ")) {
                inSection = line.equals(heading, ignoreCase = true)
            } else if (inSection && line.startsWith("-")) {
                bullets += simplifyText(line.removePrefix("-").trim())
            }
        }

        return bullets.filter { it.isNotBlank() }.take(3)
    }

    private fun summarizeBullets(bullets: List<String>): String {
        if (bullets.isEmpty()) return ""
        val first = bullets.first()
        val extras = bullets.drop(1).take(1)
        return (listOf(first) + extras).joinToString("; ")
    }

    private fun simplifyText(text: String): String {
        var friendlyText = text
            .replace("`", "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("[:\\s]+"), " ")
            .trim()

        friendlyMappings.forEach { (keyword, replacement) ->
            val regex = Regex(keyword, RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(friendlyText)) {
                friendlyText = friendlyText.replace(regex, replacement)
            }
        }

        return friendlyText.trim()
    }

    companion object {
        private const val CHANGELOG_ASSET = "Changelog.md"

        private val friendlyMappings = mapOf(
            "registro de pacientes" to "Cada paciente guarda y muestra quién lo registró para mayor claridad.",
            "validación de formularios" to "Los formularios ahora piden todos los datos con mensajes claros antes de guardar.",
            "versionado de la app" to "Actualizamos la versión para reflejar las mejoras más recientes.",
            "listados y navegación" to "Nuevas pantallas de alarmas, medicamentos y pacientes con navegación más ágil.",
            "componentes reutilizables" to "Componentes compartidos simplifican la gestión y limpieza de datos.",
            "interfaz de usuario" to "La interfaz luce más limpia y coherente con Material 3 Expressive.",
            "librerias y dependencias" to "Actualizamos dependencias para mantener la app estable y segura.",
            "estructura del modelo de datos" to "Se reforzó la base de datos para mayor confiabilidad al guardar información.",
            "estructura del proyecto" to "Ordenamos el proyecto para que funcione más rápido y sea fácil de mantener.",
            "compilación del proyecto" to "Mejoramos el rendimiento de compilación para lanzamientos más rápidos.",
            "temas material design 3" to "La app adopta colores y estilos actualizados de Material Design 3.",
            "funciones de autenticación" to "El inicio de sesión es más seguro y sencillo, con recordatorios y autocompletado.",
            "temas y estilos" to "La experiencia visual es más uniforme en toda la app.",
            "androidmanifest" to "El arranque de la app se ajustó para usar los nuevos temas y permisos.",
        )
    }
}
