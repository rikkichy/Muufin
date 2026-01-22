package com.muufin.compose.core

import com.muufin.compose.data.JellyfinApi
import com.muufin.compose.model.dto.BaseItemDto
import com.muufin.compose.model.dto.UserDto

class JellyfinRepository {
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
        return api().getItems(qp).items
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
        return api().getPlaylistItems(playlistId, qp).items
    }

    suspend fun getItem(itemId: String): BaseItemDto {
        val s = AuthManager.state.value
        return api().getItem(itemId = itemId, userId = s.userId)
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
}
