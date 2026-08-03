package dev.backbee.playback

import dev.backbee.download.DownloadRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Which episodes are on disk, in memory.
 *
 * The player resolves every media URI through this on the loading thread, so it
 * has to answer without touching the database. Keeping a mirror of the download
 * table in a plain map is what makes "downloaded episodes play offline" free
 * rather than a special case in the queue-building code.
 */
class LocalFileIndex(
    scope: CoroutineScope,
    downloads: DownloadRepository,
) {
    @Volatile
    private var byEpisodeId: Map<Long, String> = emptyMap()

    @Volatile
    private var byRemoteUrl: Map<String, String> = emptyMap()

    init {
        scope.launch {
            downloads.observeLocalFiles().collectLatest { rows ->
                byEpisodeId = rows.associate { it.episodeId to it.filePath }
                byRemoteUrl = rows.mapNotNull { row ->
                    row.enclosureUrl?.let { it to row.filePath }
                }.toMap()
            }
        }
    }

    /** Absolute path of the local audio, or null if it should stream. */
    fun pathFor(episodeId: Long): String? = existing(byEpisodeId[episodeId])

    /**
     * The lookup the data-source resolver uses. Queued items carry their remote
     * URL, so a download that finishes while an earlier episode is still playing
     * is still picked up when its turn comes.
     */
    fun pathForUrl(url: String): String? = existing(byRemoteUrl[url])

    fun isDownloaded(episodeId: Long): Boolean = pathFor(episodeId) != null

    /**
     * A row can outlive its file if storage was cleared behind our back. Falling
     * through to streaming beats failing playback outright.
     */
    private fun existing(path: String?): String? = path?.takeIf { File(it).isFile }
}
