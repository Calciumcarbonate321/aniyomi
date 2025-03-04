package eu.kanade.tachiyomi.util.episode

import eu.kanade.tachiyomi.data.database.AnimeDatabaseHelper
import eu.kanade.tachiyomi.data.database.models.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.Episode
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.util.lang.launchIO
import timber.log.Timber

/**
 * Helper method for syncing a remote track with the local episodes, and back
 *
 * @param db the database.
 * @param episodes a list of episodes from the source.
 * @param remoteTrack the remote Track object.
 * @param service the tracker service.
 */
fun syncEpisodesWithTrackServiceTwoWay(db: AnimeDatabaseHelper, episodes: List<Episode>, remoteTrack: AnimeTrack, service: TrackService) {
    val sortedEpisodes = episodes.sortedBy { it.episode_number }
    sortedEpisodes
        .filterIndexed { index, episode -> index < remoteTrack.last_episode_seen && !episode.seen }
        .forEach { it.seen = true }
    db.updateEpisodesProgress(sortedEpisodes).executeAsBlocking()

    // this uses the ordinal index of chapters instead of the chapter_number
    // it was done that way because Track.last_chapter_read was an Int at the time, and Komga
    // could have Float for the chapter number
    // this will be addressed later on
    val localLastRead = when {
        sortedEpisodes.all { it.seen } -> sortedEpisodes.size
        sortedEpisodes.any { !it.seen } -> sortedEpisodes.indexOfFirst { !it.seen }
        else -> 0
    }

    // update remote
    remoteTrack.last_episode_seen = localLastRead.toFloat()

    launchIO {
        try {
            service.update(remoteTrack)
            db.insertTrack(remoteTrack).executeAsBlocking()
        } catch (e: Throwable) {
            Timber.w(e)
        }
    }
}
