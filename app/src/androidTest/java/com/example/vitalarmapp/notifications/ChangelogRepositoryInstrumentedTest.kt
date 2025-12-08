package com.example.vitalarmapp.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChangelogRepositoryInstrumentedTest {

    @Test
    fun loadEntriesReturnRawMarkdownContent() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repository = ChangelogRepository()

        val entries = repository.loadEntries(context)

        assertFalse(entries.isEmpty())
        val firstSummary = entries.first().subhead

        assertTrue(firstSummary.contains("Centro de Novedades", ignoreCase = true))
        assertTrue(firstSummary.contains("NotificationsActivity"))
    }

    @Test
    fun parseEntriesKeepsHeadingsAndBullets() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repository = ChangelogRepository()
        val changelogText = """
            ## [0.9.99] - 2024-12-01
            ### Added
            - Registro de pacientes con validación estricta
            - Listados y navegación más ágiles
            ### Changed
            - Interfaz de usuario con temas Material Design 3
            - Librerias y dependencias actualizadas
        """.trimIndent()

        val entries = repository.parseEntries(context, changelogText)

        assertEquals(1, entries.size)
        val entry = entries.first()
        val expected = """
            Added
            • Registro de pacientes con validación estricta
            • Listados y navegación más ágiles
            Changed
            • Interfaz de usuario con temas Material Design 3
            • Librerias y dependencias actualizadas
        """.trimIndent()

        assertTrue(entry.headline.contains("0.9.99"))
        assertEquals(expected, entry.subhead)
    }
}
