package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.data.database.models.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

data class ALManga(
    val media_id: Int,
    val title_user_pref: String,
    val image_url_lge: String,
    val description: String?,
    val format: String,
    val publishing_status: String,
    val start_date_fuzzy: Long,
    val total_chapters: Int
) {

    fun toTrack() = TrackSearch.create(TrackManager.ANILIST).apply {
        media_id = this@ALManga.media_id
        title = title_user_pref
        total_chapters = this@ALManga.total_chapters
        cover_url = image_url_lge
        summary = description ?: ""
        tracking_url = AnilistApi.mangaUrl(media_id)
        publishing_status = this@ALManga.publishing_status
        publishing_type = format
        if (start_date_fuzzy != 0L) {
            start_date = try {
                val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                outputDf.format(start_date_fuzzy)
            } catch (e: Exception) {
                ""
            }
        }
    }
}

data class ALAnime(
    val media_id: Int,
    val title_user_pref: String,
    val image_url_lge: String,
    val description: String?,
    val format: String,
    val publishing_status: String,
    val start_date_fuzzy: Long,
    val total_episodes: Int
) {

    fun toTrack() = AnimeTrackSearch.create(TrackManager.ANILIST).apply {
        media_id = this@ALAnime.media_id
        title = title_user_pref
        total_episodes = this@ALAnime.total_episodes
        cover_url = image_url_lge
        summary = description ?: ""
        tracking_url = AnilistApi.animeUrl(media_id)
        publishing_status = this@ALAnime.publishing_status
        publishing_type = format
        if (start_date_fuzzy != 0L) {
            start_date = try {
                val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                outputDf.format(start_date_fuzzy)
            } catch (e: Exception) {
                ""
            }
        }
    }
}

data class ALUserManga(
    val library_id: Long,
    val list_status: String,
    val score_raw: Int,
    val chapters_read: Int,
    val start_date_fuzzy: Long,
    val completed_date_fuzzy: Long,
    val manga: ALManga
) {

    fun toTrack() = Track.create(TrackManager.ANILIST).apply {
        media_id = manga.media_id
        title = manga.title_user_pref
        status = toTrackStatus()
        score = score_raw.toFloat()
        started_reading_date = start_date_fuzzy
        finished_reading_date = completed_date_fuzzy
        last_chapter_read = chapters_read.toFloat()
        library_id = this@ALUserManga.library_id
        total_chapters = manga.total_chapters
    }

    fun toTrackStatus() = when (list_status) {
        "CURRENT" -> Anilist.READING
        "COMPLETED" -> Anilist.COMPLETED
        "PAUSED" -> Anilist.PAUSED
        "DROPPED" -> Anilist.DROPPED
        "PLANNING" -> Anilist.PLANNING
        "REPEATING" -> Anilist.REPEATING
        else -> throw NotImplementedError("Unknown status: $list_status")
    }
}

data class ALUserAnime(
    val library_id: Long,
    val list_status: String,
    val score_raw: Int,
    val episodes_seen: Int,
    val start_date_fuzzy: Long,
    val completed_date_fuzzy: Long,
    val manga: ALManga
) {

    fun toTrack() = AnimeTrack.create(TrackManager.ANILIST).apply {
        media_id = manga.media_id
        title = manga.title_user_pref
        status = toTrackStatus()
        score = score_raw.toFloat()
        started_watching_date = start_date_fuzzy
        finished_watching_date = completed_date_fuzzy
        last_episode_seen = episodes_seen.toFloat()
        library_id = this@ALUserAnime.library_id
        total_episodes = manga.total_chapters
    }

    fun toTrackStatus() = when (list_status) {
        "CURRENT" -> Anilist.WATCHING
        "COMPLETED" -> Anilist.COMPLETED
        "PAUSED" -> Anilist.PAUSED
        "DROPPED" -> Anilist.DROPPED
        "PLANNING" -> Anilist.PLANNING
        "REPEATING" -> Anilist.REPEATING
        else -> throw NotImplementedError("Unknown status: $list_status")
    }
}

fun Track.toAnilistStatus() = when (status) {
    Anilist.READING -> "CURRENT"
    Anilist.COMPLETED -> "COMPLETED"
    Anilist.PAUSED -> "PAUSED"
    Anilist.DROPPED -> "DROPPED"
    Anilist.PLANNING -> "PLANNING"
    Anilist.REPEATING -> "REPEATING"
    else -> throw NotImplementedError("Unknown status: $status")
}

fun AnimeTrack.toAnilistStatus() = when (status) {
    Anilist.WATCHING -> "CURRENT"
    Anilist.COMPLETED -> "COMPLETED"
    Anilist.PAUSED -> "PAUSED"
    Anilist.DROPPED -> "DROPPED"
    Anilist.PLANNING -> "PLANNING"
    Anilist.REPEATING -> "REPEATING"
    else -> throw NotImplementedError("Unknown status: $status")
}

private val preferences: PreferencesHelper by injectLazy()

fun Track.toAnilistScore(): String = when (preferences.anilistScoreType().get()) {
// 10 point
    "POINT_10" -> (score.toInt() / 10).toString()
// 100 point
    "POINT_100" -> score.toInt().toString()
// 5 stars
    "POINT_5" -> when {
        score == 0f -> "0"
        score < 30 -> "1"
        score < 50 -> "2"
        score < 70 -> "3"
        score < 90 -> "4"
        else -> "5"
    }
// Smiley
    "POINT_3" -> when {
        score == 0f -> "0"
        score <= 35 -> ":("
        score <= 60 -> ":|"
        else -> ":)"
    }
// 10 point decimal
    "POINT_10_DECIMAL" -> (score / 10).toString()
    else -> throw NotImplementedError("Unknown score type")
}

fun AnimeTrack.toAnilistScore(): String = when (preferences.anilistScoreType().get()) {
// 10 point
    "POINT_10" -> (score.toInt() / 10).toString()
// 100 point
    "POINT_100" -> score.toInt().toString()
// 5 stars
    "POINT_5" -> when {
        score == 0f -> "0"
        score < 30 -> "1"
        score < 50 -> "2"
        score < 70 -> "3"
        score < 90 -> "4"
        else -> "5"
    }
// Smiley
    "POINT_3" -> when {
        score == 0f -> "0"
        score <= 35 -> ":("
        score <= 60 -> ":|"
        else -> ":)"
    }
// 10 point decimal
    "POINT_10_DECIMAL" -> (score / 10).toString()
    else -> throw NotImplementedError("Unknown score type")
}
