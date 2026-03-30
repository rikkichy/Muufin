package cat.ri.muufin.core

import cat.ri.muufin.data.JellyfinApi
import cat.ri.muufin.model.dto.LyricDto
import cat.ri.muufin.model.dto.BaseItemDto
import cat.ri.muufin.model.dto.MediaStreamDto
import cat.ri.muufin.model.dto.UserDto
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

class JellyfinRepository {

    private data class CacheEntry<T>(
        val data: T,
        val insertedAt: Long = System.currentTimeMillis(),
    ) {
        fun isStale(ttlMs: Long): Boolean = System.currentTimeMillis() - insertedAt > ttlMs
    }

    companion object {
        private const val CACHE_TTL_MS = 5L * 60 * 1000 // 5 minutes
    }

    private val itemCache = ConcurrentHashMap<String, CacheEntry<BaseItemDto>>()
    private val playlistTracksCache = ConcurrentHashMap<String, CacheEntry<List<BaseItemDto>>>()
    private val albumTracksCache = ConcurrentHashMap<String, CacheEntry<List<BaseItemDto>>>()

    fun getCachedItem(id: String): BaseItemDto? {
        val entry = itemCache[id] ?: return null
        return if (entry.isStale(CACHE_TTL_MS)) null else entry.data
    }

    fun getCachedPlaylistTracks(id: String): List<BaseItemDto>? {
        val entry = playlistTracksCache[id] ?: return null
        return if (entry.isStale(CACHE_TTL_MS)) null else entry.data
    }

    fun getCachedAlbumTracks(id: String): List<BaseItemDto>? {
        val entry = albumTracksCache[id] ?: return null
        return if (entry.isStale(CACHE_TTL_MS)) null else entry.data
    }

    fun clearAllCaches() {
        itemCache.clear()
        playlistTracksCache.clear()
        albumTracksCache.clear()
    }

    fun invalidateItem(id: String) {
        itemCache.remove(id)
        albumTracksCache.remove(id)
        playlistTracksCache.remove(id)
    }

    private fun api(): JellyfinApi {
        val state = AuthManager.state.value
        return JellyfinApi.create(state)
    }

    suspend fun getAlbums(startIndex: Int = 0, limit: Int = 10, searchTerm: String? = null): List<BaseItemDto> {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("includeItemTypes", "MusicAlbum")
            put("recursive", "true")
            put("startIndex", startIndex.toString())
            put("limit", limit.toString())
            put("sortBy", "SortName")
            put("sortOrder", "Ascending")
            put("enableImages", "true")
            if (!searchTerm.isNullOrBlank()) put("searchTerm", searchTerm)
        }
        return api().getItems(qp).items
    }

    suspend fun getArtists(startIndex: Int = 0, limit: Int = 10, searchTerm: String? = null): List<BaseItemDto> {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("startIndex", startIndex.toString())
            put("limit", limit.toString())
            put("enableImages", "true")
            if (!searchTerm.isNullOrBlank()) put("searchTerm", searchTerm)
        }
        return api().getArtists(qp).items
    }

    suspend fun getPlaylists(startIndex: Int = 0, limit: Int = 10): List<BaseItemDto> {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("includeItemTypes", "Playlist")
            put("recursive", "true")
            put("startIndex", startIndex.toString())
            put("limit", limit.toString())
            put("sortBy", "SortName")
            put("sortOrder", "Ascending")
            put("enableImages", "true")
        }
        return api().getItems(qp).items
    }

    suspend fun getAlbumTracks(albumId: String): List<BaseItemDto> {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("parentId", albumId)
            put("includeItemTypes", "Audio")
            put("recursive", "true")
            put("sortBy", "IndexNumber,SortName")
            put("sortOrder", "Ascending")
            put("enableImages", "true")
        }
        return api().getItems(qp).items.also { albumTracksCache[albumId] = CacheEntry(it) }
    }

    suspend fun getArtistAlbums(artistId: String): List<BaseItemDto> {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("includeItemTypes", "MusicAlbum")
            put("recursive", "true")
            put("artistIds", artistId)
            put("sortBy", "SortName")
            put("sortOrder", "Ascending")
            put("enableImages", "true")
        }
        return api().getItems(qp).items
    }

    suspend fun getPlaylistTracks(playlistId: String): List<BaseItemDto> {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("startIndex", "0")
            put("limit", "1000")
            put("enableImages", "true")
            put("enableImageTypes", "Primary")
            put("imageTypeLimit", "1")
        }
        return api().getPlaylistItems(playlistId, qp).items.also { playlistTracksCache[playlistId] = CacheEntry(it) }
    }

    suspend fun getItem(itemId: String): BaseItemDto? {
        return try {
            val s = AuthManager.state.value
            api().getItem(itemId = itemId, userId = s.userId).also { itemCache[itemId] = CacheEntry(it) }
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }
    }

    // Count-only queries for sync change detection (limit=0, just totalRecordCount)
    suspend fun getAlbumCount(): Int {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("includeItemTypes", "MusicAlbum")
            put("recursive", "true")
            put("limit", "0")
        }
        return api().getItems(qp).totalRecordCount ?: 0
    }

    suspend fun getPlaylistCount(): Int {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("includeItemTypes", "Playlist")
            put("recursive", "true")
            put("limit", "0")
        }
        return api().getItems(qp).totalRecordCount ?: 0
    }

    suspend fun getArtistCount(): Int {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("limit", "0")
        }
        return api().getArtists(qp).totalRecordCount ?: 0
    }

    suspend fun search(term: String): List<BaseItemDto> {
        val s = AuthManager.state.value
        val qp = buildMap {
            put("userId", s.userId)
            put("searchTerm", term)
            put("recursive", "true")
            put("includeItemTypes", "Audio,MusicAlbum,MusicArtist,Playlist")
            put("limit", "50")
            put("startIndex", "0")
            put("enableImages", "true")
        }
        return api().getItems(qp).items
    }

    suspend fun getCurrentUser(): UserDto {
        return api().getCurrentUser()
    }

    suspend fun getPublicSystemInfo(): cat.ri.muufin.model.dto.PublicSystemInfoDto {
        return api().getPublicSystemInfo()
    }

    suspend fun getItemMediaStreams(itemId: String): List<MediaStreamDto>? {
        return try {
            val s = AuthManager.state.value
            api().getItemWithMediaStreams(itemId = itemId, userId = s.userId).mediaStreams
        } catch (e: HttpException) { null }
        catch (e: Throwable) { null }
    }

    suspend fun getLyrics(itemId: String): LyricDto? {
        return try {
            api().getLyrics(itemId)
        } catch (e: HttpException) { null }
        catch (e: Throwable) { null }
    }
}
