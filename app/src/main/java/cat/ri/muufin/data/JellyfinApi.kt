package cat.ri.muufin.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import cat.ri.muufin.core.HttpClients
import cat.ri.muufin.core.JellyfinAuthorization
import cat.ri.muufin.model.AuthState
import cat.ri.muufin.model.dto.AuthByNameRequest
import cat.ri.muufin.model.dto.AuthResultDto
import cat.ri.muufin.model.dto.BaseItemDto
import cat.ri.muufin.model.dto.BaseItemQueryResultDto
import cat.ri.muufin.model.dto.PlaybackProgressInfo
import cat.ri.muufin.model.dto.PlaybackStartInfo
import cat.ri.muufin.model.dto.PlaybackStopInfo
import cat.ri.muufin.model.dto.PublicSystemInfoDto
import cat.ri.muufin.model.dto.QuickConnectAuthRequestDto
import cat.ri.muufin.model.dto.QuickConnectInitiateResponseDto
import cat.ri.muufin.model.dto.QuickConnectConnectResponseDto
import cat.ri.muufin.model.dto.LyricDto
import cat.ri.muufin.model.dto.PlaybackInfoResponse
import cat.ri.muufin.model.dto.UserDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.*

interface JellyfinApi {

    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): PublicSystemInfoDto

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

    @POST("Users/AuthenticateWithQuickConnect")
    suspend fun authenticateWithQuickConnect(
        @Body body: QuickConnectAuthRequestDto,
        @Header("Authorization") authorizationOverride: String,
    ): AuthResultDto

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

    @GET("Items/{itemId}")
    suspend fun getItemWithMediaStreams(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
        @Query("fields") fields: String = "MediaStreams",
        @Query("enableImages") enableImages: Boolean = false,
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

    @GET("Audio/{itemId}/Lyrics")
    suspend fun getLyrics(
        @Path("itemId") itemId: String,
    ): LyricDto

    @Streaming
    @GET("Items/{itemId}/Download")
    suspend fun downloadItem(
        @Path("itemId") itemId: String,
    ): ResponseBody

    @GET("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
    ): PlaybackInfoResponse

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        @Volatile private var cachedBaseUrl: String? = null
        @Volatile private var cachedApi: JellyfinApi? = null

        fun create(state: AuthState): JellyfinApi {
            val baseUrl = state.baseUrl.trim().removeSuffix("/") + "/"
            cachedApi?.let { if (cachedBaseUrl == baseUrl) return it }

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(HttpClients.apiOkHttp())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val api = retrofit.create(JellyfinApi::class.java)
            cachedBaseUrl = baseUrl
            cachedApi = api
            return api
        }

        fun invalidate() {
            cachedApi = null
            cachedBaseUrl = null
        }
    }
}
