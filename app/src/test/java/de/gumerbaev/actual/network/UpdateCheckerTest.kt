package de.gumerbaev.actual.network

import com.google.gson.Gson
import de.gumerbaev.actual.model.GitHubRelease
import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun testIsNewerVersion() {
        // Newer patch version
        assertTrue(UpdateChecker.isNewerVersion("1.0.3", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.0.3", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.3", "v1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("v1.0.3", "v1.0.0"))

        // Newer minor/major version
        assertTrue(UpdateChecker.isNewerVersion("1.1.0", "1.0.9"))
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.99.99"))

        // Same version
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.3", "1.0.3"))
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.0"))

        // Older version
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(UpdateChecker.isNewerVersion("0.9.5", "1.0.0"))

        // Suffixes like -beta
        assertFalse(UpdateChecker.isNewerVersion("1.0.0-beta", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.1-beta", "1.0.0"))
    }

    @Test
    fun testParseVersionParts() {
        assertEquals(listOf(1, 0, 3), UpdateChecker.parseVersionParts("v1.0.3"))
        assertEquals(listOf(2, 4, 1), UpdateChecker.parseVersionParts("2.4.1"))
        assertEquals(listOf(1, 0), UpdateChecker.parseVersionParts("1.0"))
        assertEquals(listOf(1, 2, 3), UpdateChecker.parseVersionParts("1.2.3-rc1"))
    }

    @Test
    fun testGitHubReleaseJsonParsing() {
        val sampleJson = """
            {
              "tag_name": "v1.0.3",
              "name": "Release v1.0.3",
              "html_url": "https://github.com/aydarik/actual-bridge/releases/tag/v1.0.3",
              "body": "Fixed some issues",
              "assets": [
                {
                  "name": "actual-budget-bridge-1.0.3.apk",
                  "browser_download_url": "https://github.com/aydarik/actual-bridge/releases/download/v1.0.3/actual-budget-bridge-1.0.3.apk",
                  "size": 14404483
                }
              ]
            }
        """.trimIndent()

        val release = Gson().fromJson(sampleJson, GitHubRelease::class.java)
        assertNotNull(release)
        assertEquals("v1.0.3", release.tagName)
        assertEquals("1.0.3", release.cleanVersion)
        assertEquals("Release v1.0.3", release.name)
        assertEquals("https://github.com/aydarik/actual-bridge/releases/tag/v1.0.3", release.htmlUrl)
        assertEquals("Fixed some issues", release.body)
        assertEquals(1, release.assets.size)
        assertEquals(
            "https://github.com/aydarik/actual-bridge/releases/download/v1.0.3/actual-budget-bridge-1.0.3.apk",
            release.apkDownloadUrl
        )
    }
}
