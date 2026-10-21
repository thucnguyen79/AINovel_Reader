package vn.esstar.ainovel.domain.model

/** What a backup file contained or what an import changed. */
data class BackupSummary(
    val books: Int,
    val chapters: Int,
    val glossaryEntries: Int,
    /** Chapters whose text was included (ebooks; web chapters are re-fetched instead). */
    val textChapters: Int,
    val exportedAt: Long,
    val appVersion: String,
)

/** App version as seen by the UI; provided by the app module from BuildConfig. */
data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    val isDebug: Boolean,
    val repositoryUrl: String,
)

/** A newer build published on GitHub Releases. */
data class AppUpdate(
    val versionName: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val notes: String?,
)
