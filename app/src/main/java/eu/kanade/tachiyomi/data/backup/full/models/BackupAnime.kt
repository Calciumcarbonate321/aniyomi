package eu.kanade.tachiyomi.data.backup.full.models

import eu.kanade.tachiyomi.data.database.models.Anime
import eu.kanade.tachiyomi.data.database.models.AnimeImpl
import eu.kanade.tachiyomi.data.database.models.AnimeTrackImpl
import eu.kanade.tachiyomi.data.database.models.EpisodeImpl
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupAnime(
    // in 1.x some of these values have different names
    @ProtoNumber(1) var source: Long,
    // url is called key in 1.x
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    // thumbnailUrl is called cover in 1.x
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    // @ProtoNumber(10) val customCover: String = "", 1.x value, not used in 0.x
    // @ProtoNumber(11) val lastUpdate: Long = 0, 1.x value, not used in 0.x
    // @ProtoNumber(12) val lastInit: Long = 0, 1.x value, not used in 0.x
    @ProtoNumber(13) var dateAdded: Long = 0,
    // @ProtoNumber(15) val flags: Int = 0, 1.x value, not used in 0.x
    @ProtoNumber(16) var episodes: List<BackupEpisode> = emptyList(),
    @ProtoNumber(17) var categories: List<Int> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupAnimeTracking> = emptyList(),
    // Bump by 100 for values that are not saved/implemented in 1.x but are used in 0.x
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var episodeFlags: Int = 0,
    @ProtoNumber(102) var history: List<BackupAnimeHistory> = emptyList(),
    @ProtoNumber(103) var viewer_flags: Int = 0
) {
    fun getAnimeImpl(): AnimeImpl {
        return AnimeImpl().apply {
            url = this@BackupAnime.url
            title = this@BackupAnime.title
            artist = this@BackupAnime.artist
            author = this@BackupAnime.author
            description = this@BackupAnime.description
            genre = this@BackupAnime.genre.joinToString()
            status = this@BackupAnime.status
            thumbnail_url = this@BackupAnime.thumbnailUrl
            favorite = this@BackupAnime.favorite
            source = this@BackupAnime.source
            date_added = this@BackupAnime.dateAdded
            viewer_flags = this@BackupAnime.viewer_flags
            episode_flags = this@BackupAnime.episodeFlags
        }
    }

    fun getEpisodesImpl(): List<EpisodeImpl> {
        return episodes.map {
            it.toEpisodeImpl()
        }
    }

    fun getTrackingImpl(): List<AnimeTrackImpl> {
        return tracking.map {
            it.getTrackingImpl()
        }
    }

    companion object {
        fun copyFrom(anime: Anime): BackupAnime {
            return BackupAnime(
                url = anime.url,
                title = anime.title,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                genre = anime.getGenres() ?: emptyList(),
                status = anime.status,
                thumbnailUrl = anime.thumbnail_url,
                favorite = anime.favorite,
                source = anime.source,
                dateAdded = anime.date_added,
                viewer_flags = anime.viewer_flags,
                episodeFlags = anime.episode_flags
            )
        }
    }
}
