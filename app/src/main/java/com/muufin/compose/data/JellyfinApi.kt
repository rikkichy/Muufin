package com.muufin.compose.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.muufin.compose.core.HttpClients
import com.muufin.compose.core.JellyfinAuthorization
import com.muufin.compose.model.AuthState
import com.muufin.compose.model.dto.AuthByNameRequest
import com.muufin.compose.model.dto.AuthResultDto
import com.muufin.compose.model.dto.BaseItemDto
import com.muufin.compose.model.dto.BaseItemQueryResultDto
import com.muufin.compose.model.dto.PlaybackProgressInfo
import com.muufin.compose.model.dto.PlaybackStartInfo
import com.muufin.compose.model.dto.PlaybackStopInfo
import com.muufin.compose.model.dto.QuickConnectInitiateResponseDto
import com.muufin.compose.model.dto.QuickConnectConnectResponseDto
import com.muufin.compose.model.dto.UserDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.*

interface JellyfinApi {

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Body body: AuthByNameRequest,
        @Header("Authorization") authorizationOverride: String,
    ): AuthResultDto

    @POST("QuickConnect/Initiate")
    suspend fun initiateQuickConnect(
        @Header("Authorization") authorizationOverride: String,
    ): QuickConnectInitiateResponseDto

    @GET("QuickConnect/Connect")
    suspend fun checkQuickConnect(@Query("secret") secret: String): QuickConnectConnectResponseDto

    @GET("Users/Me")
    suspend fun getCurrentUser(): UserDto

    
    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(@Body body: PlaybackStartInfo)

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(@Body body: PlaybackProgressInfo)

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(@Body body: PlaybackStopInfo)

    @GET("Items")
    suspend fun getItems(@QueryMap encoded: Map<String, String>): BaseItemQueryResultDto

    @GET("Artists")
    suspend fun getArtists(@QueryMap encoded: Map<String, String>): BaseItemQueryResultDto

    @GET("Items/{itemId}")
    suspend fun getItem(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
        @Query("enableImages") enableImages: Boolean = true,
    ): BaseItemDto

    @GET("Users/{userId}/Items")
    suspend fun getUserItems(
        @Path("userId") userId: String,
        @QueryMap encoded: Map<String, String>,
    ): BaseItemQueryResultDto

    @GET("Playlists/{playlistId}/Items")
    suspend fun getPlaylistItems(
        @Path("playlistId") playlistId: String,
        @QueryMap encoded: Map<String, String>,
    ): BaseItemQueryResultDto

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun create(state: AuthState): JellyfinApi {
            val baseUrl = state.baseUrl.trim().removeSuffix("/") + "/"

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(HttpClients.apiOkHttp())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            return retrofit.create(JellyfinApi::class.java)
        }
    }
}
