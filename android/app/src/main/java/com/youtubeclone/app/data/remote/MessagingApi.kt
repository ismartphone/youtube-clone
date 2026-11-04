package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.remote.dto.ConversationDto
import com.youtubeclone.app.data.remote.dto.ConversationPageDto
import com.youtubeclone.app.data.remote.dto.MessageDto
import com.youtubeclone.app.data.remote.dto.MessagePageDto
import com.youtubeclone.app.data.remote.dto.NotificationSettingsDto
import com.youtubeclone.app.data.remote.dto.PeopleDto
import com.youtubeclone.app.data.remote.dto.SendMessageRequestDto
import com.youtubeclone.app.data.remote.dto.StartConversationRequestDto
import com.youtubeclone.app.data.remote.dto.UnreadCountDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Messagerie (ADR-025) : toutes ces routes exigent une session.
interface MessagingApi {
    @GET("me/conversations")
    suspend fun getConversations(@Query("page") page: Int): ConversationPageDto

    @POST("me/conversations")
    suspend fun startConversation(@Body body: StartConversationRequestDto): ConversationDto

    @GET("me/conversations/unread-count")
    suspend fun getUnreadCount(): UnreadCountDto

    @GET("conversations/{id}")
    suspend fun getConversation(@Path("id") id: String): ConversationDto

    @GET("conversations/{id}/messages")
    suspend fun getMessages(@Path("id") id: String, @Query("page") page: Int): MessagePageDto

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") id: String, @Body body: SendMessageRequestDto): MessageDto

    // Réponse vide (204).
    @POST("conversations/{id}/read")
    suspend fun markRead(@Path("id") id: String)

    @GET("users/search")
    suspend fun searchPeople(@Query("q") query: String): PeopleDto

    @GET("me/notification-settings")
    suspend fun getNotificationSettings(): NotificationSettingsDto

    @PATCH("me/notification-settings")
    suspend fun setNotificationSettings(@Body body: NotificationSettingsDto): NotificationSettingsDto
}
