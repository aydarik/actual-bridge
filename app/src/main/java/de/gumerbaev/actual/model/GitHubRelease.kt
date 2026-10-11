package de.gumerbaev.actual.model

import com.google.gson.annotations.SerializedName

/**
 * Data model representing a GitHub release from https://github.com/aydarik/actual-bridge/releases
 */
data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("html_url")
    val htmlUrl: String = "",
    @SerializedName("body")
    val body: String? = null,
    @SerializedName("published_at")
    val publishedAt: String? = null,
    @SerializedName("assets")
    val assets: List<ReleaseAsset> = emptyList()
) {
    val apkDownloadUrl: String?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.browserDownloadUrl
            ?: assets.firstOrNull()?.browserDownloadUrl

    val cleanVersion: String
        get() = tagName.trim().trimStart('v', 'V')
}

data class ReleaseAsset(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String = "",
    @SerializedName("size")
    val size: Long = 0L
)
