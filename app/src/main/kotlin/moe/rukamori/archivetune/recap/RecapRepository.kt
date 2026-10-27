/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.recap

import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelScoped
class RecapRepository @Inject constructor(private val database: MusicDatabase) {
    private val connection = MutableStateFlow<PlayerConnection?>(null)

    internal fun attach(player: PlayerConnection?) { connection.value = player }

    internal fun playback() = connection.flatMapLatest { player ->
        if (player == null) flowOf(RecapPlayback()) else combine(player.mediaMetadata, player.isPlaying) { metadata, playing ->
            RecapPlayback(true, metadata?.id, metadata?.title.orEmpty(), playing)
        }
    }

    internal suspend fun play(songs: List<Song>, startIndex: Int, title: String, toggleCurrent: Boolean) {
        val items = songs.map { it.toMediaItem() }
        withContext(Dispatchers.Main.immediate) {
            val player = checkNotNull(connection.value)
            if (toggleCurrent && player.mediaMetadata.value?.id == songs[startIndex].id) {
                player.player.togglePlayPause()
            } else {
                player.playQueue(ListQueue(title, items, startIndex))
            }
        }
    }

    internal suspend fun togglePlayback() = withContext(Dispatchers.Main.immediate) {
        checkNotNull(connection.value).player.togglePlayPause()
    }

    internal fun changes() = database.recapChanges()

    internal suspend fun load(start: LocalDateTime, end: LocalDateTime): RecapSnapshot =
        withContext(Dispatchers.IO) {
            val from = start.toInstant(ZoneOffset.UTC).toEpochMilli()
            val until = end.toInstant(ZoneOffset.UTC).toEpochMilli() + if (end.nano % 1_000_000 == 0) 0L else 1L
            database.withTransaction {
                val dao = recapDao
                RecapSnapshot(
                    firstTimestamp = dao.firstTimestamp(),
                    totals = dao.totals(from, until),
                    songs = dao.songs(from, until),
                    artists = dao.artists(from, until),
                    albums = dao.albums(from, until),
                    dayHours = dao.dayHours(from, until),
                )
            }
        }

    internal suspend fun songs(ids: List<String>): List<Song> = withContext(Dispatchers.IO) {
        ids.mapNotNull { database.song(it).first() }
    }

    internal suspend fun song(id: String): Song? = withContext(Dispatchers.IO) { database.song(id).first() }
    internal suspend fun artist(id: String): Artist? = withContext(Dispatchers.IO) { database.artist(id).first() }
    internal suspend fun album(id: String): Album? = withContext(Dispatchers.IO) { database.album(id).first() }
}
