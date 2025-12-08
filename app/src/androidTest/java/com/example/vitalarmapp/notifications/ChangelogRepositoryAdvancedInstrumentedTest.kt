package com.example.vitalarmapp.notifications

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.vitalarmapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChangelogRepositoryAdvancedInstrumentedTest {

    private val repository = ChangelogRepository()

    @Test
    fun emptyChangelogYieldsNoEntries() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val entries = repository.parseEntries(context, "")

        assertTrue(entries.isEmpty())
    }

    @Test
    fun emptyBodyUsesFallbackSummary() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val changelog = """
            ## [1.2.3] - 2024-05-05
            ### Added

        """.trimIndent()

        val entries = repository.parseEntries(context, changelog)

        assertEquals(1, entries.size)
        val summary = entries.first().subhead
        assertEquals(context.getString(R.string.notifications_summary_fallback), summary)
    }

    @Test
    fun preservesMarkdownHeadingsAndBulletsInOrder() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val changelog = """
            ## [2.0.0] - 2024-07-07
            ### Added
            - Mejoras en registro de pacientes y validación de formularios
            ### Changed
            - Estructura del proyecto y compilación del proyecto optimizadas
        """.trimIndent()

        val entries = repository.parseEntries(context, changelog)

        assertEquals(1, entries.size)
        val subhead = entries.first().subhead
        val expected = """
            Added
            • Mejoras en registro de pacientes y validación de formularios
            Changed
            • Estructura del proyecto y compilación del proyecto optimizadas
        """.trimIndent()

        assertEquals(expected, subhead)
    }

    @Test
    fun parsesMultipleVersionsWithoutDiscardingContent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val changelog = """
            ## [3.1.0] - 2024-08-08
            ### Added
            - Primera mejora
            - Segunda mejora
            ## [3.0.0] - 2024-08-01
            ### Changed
            - Primera refactorización
        """.trimIndent()

        val entries = repository.parseEntries(context, changelog)

        assertEquals(2, entries.size)
        assertTrue(entries[0].headline.contains("3.1.0"))
        assertTrue(entries[0].subhead.contains("Primera mejora"))
        assertTrue(entries[1].headline.contains("3.0.0"))
        assertTrue(entries[1].subhead.contains("Primera refactorización"))
    }
}
