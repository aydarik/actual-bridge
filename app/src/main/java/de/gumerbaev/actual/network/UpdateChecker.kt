package de.gumerbaev.actual.network

import com.google.gson.Gson
import de.gumerbaev.actual.R
import de.gumerbaev.actual.model.GitHubRelease
import de.gumerbaev.actual.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object UpdateChecker {

    const val RELEASES_WEB_URL = "https://github.com/aydarik/actual-bridge/releases"
    private const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/aydarik/actual-bridge/releases/latest"
    val AUTO_CHECK_INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(1)

    /**
     * Returns true if at least 24 hours have passed since [lastCheckTimestamp].
     */
    fun shouldAutoCheck(lastCheckTimestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (lastCheckTimestamp <= 0L) return true
        return (now - lastCheckTimestamp) >= AUTO_CHECK_INTERVAL_MS
    }

    sealed class CheckResult {
        data class NewUpdate(val release: GitHubRelease, val currentVersion: String) : CheckResult()
        data class UpToDate(val release: GitHubRelease, val currentVersion: String) : CheckResult()
        data class Error(val error: UiText) : CheckResult()
    }

    data class ChangelogSection(
        val title: String?,
        val items: List<String>
    )

    data class ParsedChangelog(
        val sections: List<ChangelogSection>,
        val compareUrl: String? = null
    )

    private val FULL_CHANGELOG_REGEX = Regex("""(?i)^\s*\*{0,2}Full Changelog\*{0,2}:\s*(https?://\S+)\s*$""")
    private val HEADER_REGEX = Regex("""^\s*#{1,6}\s+(.+?)\s*$""")
    private val BULLET_REGEX = Regex("""^\s*[-*•]\s+(.+?)\s*$""")
    private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val CODE_REGEX = Regex("""`([^`]+)`""")
    private val LINK_REGEX = Regex("""\[([^\]]+)]\((https?://[^)]+)\)""")

    fun cleanInlineMarkdown(text: String): String {
        return text
            .replace(LINK_REGEX, "$1")
            .replace(BOLD_REGEX) { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }
            .replace(CODE_REGEX, "$1")
            .trim()
    }

    fun parseChangelog(rawBody: String?): ParsedChangelog {
        if (rawBody.isNullOrBlank()) return ParsedChangelog(emptyList(), null)

        val lines = rawBody.replace("\r\n", "\n").replace('\r', '\n').lines()
        val sections = mutableListOf<ChangelogSection>()
        var currentTitle: String? = null
        var currentItems = mutableListOf<String>()
        var compareUrl: String? = null

        fun flushSection() {
            if (currentTitle != null || currentItems.isNotEmpty()) {
                sections.add(ChangelogSection(currentTitle, currentItems.toList()))
                currentTitle = null
                currentItems = mutableListOf()
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val fullChangelogMatch = FULL_CHANGELOG_REGEX.matchEntire(line)
            if (fullChangelogMatch != null) {
                compareUrl = fullChangelogMatch.groupValues[1]
                continue
            }

            val headerMatch = HEADER_REGEX.matchEntire(line)
            if (headerMatch != null) {
                flushSection()
                currentTitle = cleanInlineMarkdown(headerMatch.groupValues[1])
                continue
            }

            val bulletMatch = BULLET_REGEX.matchEntire(line)
            if (bulletMatch != null) {
                val cleaned = cleanInlineMarkdown(bulletMatch.groupValues[1])
                if (cleaned.isNotEmpty()) {
                    currentItems.add(cleaned)
                }
                continue
            }

            val cleanedLine = cleanInlineMarkdown(line)
            if (cleanedLine.isNotEmpty()) {
                currentItems.add(cleanedLine)
            }
        }

        flushSection()
        return ParsedChangelog(sections, compareUrl)
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    /**
     * Checks the latest release from the repository on GitHub.
     */
    suspend fun checkForUpdates(currentVersion: String): CheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GITHUB_API_LATEST_RELEASE)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .addHeader("User-Agent", "ActualBudgetBridge-Android")
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    return@withContext CheckResult.Error(
                        UiText.res(R.string.err_server_http, response.code, body)
                    )
                }

                val body = response.body.string()
                val release = gson.fromJson(body, GitHubRelease::class.java)
                if (release == null || release.tagName.isBlank()) {
                    return@withContext CheckResult.Error(
                        UiText.Raw("Invalid release data received from GitHub.")
                    )
                }

                if (isNewerVersion(release.cleanVersion, currentVersion)) {
                    CheckResult.NewUpdate(release, currentVersion)
                } else {
                    CheckResult.UpToDate(release, currentVersion)
                }
            }
        } catch (e: Exception) {
            CheckResult.Error(
                UiText.Raw(e.localizedMessage ?: e.javaClass.simpleName)
            )
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.0.3" and "1.0.0").
     * Returns true if [remoteVersion] is strictly newer than [currentVersion].
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteVersion)
        val currentParts = parseVersionParts(currentVersion)

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Extracts numeric segments from a version string (e.g., "v1.2.3" -> [1, 2, 3]).
     */
    fun parseVersionParts(version: String): List<Int> {
        val cleaned = version.trim().trimStart('v', 'V')
        if (cleaned.isEmpty()) return emptyList()

        return cleaned.split('.')
            .map { segment ->
                segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
    }
}
