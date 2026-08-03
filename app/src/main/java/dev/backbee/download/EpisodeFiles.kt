package dev.backbee.download

import android.content.Context
import java.io.File

/**
 * Where episode audio lives on disk.
 *
 * App-private internal storage: no permissions to ask for, and uninstalling
 * takes the (potentially many gigabytes of) audio with it.
 */
class EpisodeFiles(context: Context) {

    private val root: File = File(context.filesDir, "episodes")

    fun fileFor(showId: Long, episodeId: Long, enclosureUrl: String?): File {
        val dir = File(root, showId.toString()).apply { mkdirs() }
        return File(dir, "$episodeId.${extensionOf(enclosureUrl)}")
    }

    fun partialFor(target: File): File = File(target.parentFile, "${target.name}.part")

    fun delete(path: String?): Boolean {
        val file = path?.let(::File) ?: return false
        val deleted = file.delete()
        // Leave no half-finished body behind either.
        File(file.parentFile, "${file.name}.part").delete()
        return deleted
    }

    fun bytesOnDisk(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Removes audio for a show that has been deleted from the shelf. */
    fun deleteShow(showId: Long) {
        File(root, showId.toString()).deleteRecursively()
    }

    /**
     * Clears files the database no longer references. Downloads and DB rows can
     * drift apart if the app is killed between writing the file and committing
     * the row; this is the sweep that reconciles them.
     */
    fun deleteOrphans(knownPaths: Set<String>): Int {
        if (!root.exists()) return 0
        var removed = 0
        root.walkTopDown()
            .filter { it.isFile }
            .filter { it.absolutePath !in knownPaths }
            .forEach { if (it.delete()) removed++ }
        return removed
    }

    private fun extensionOf(url: String?): String {
        val candidate = url
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it.length <= 4 && it.all(Char::isLetterOrDigit) }
        return candidate ?: "mp3"
    }
}
