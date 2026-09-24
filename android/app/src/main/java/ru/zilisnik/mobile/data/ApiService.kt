package ru.zilisnik.mobile.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @GET("api/v1/applications")
    suspend fun applications(
        @Header("Authorization") authorization: String,
        @Query("status_filter") status: String,
        @Query("work_date") workDate: String,
    ): List<ApplicationSummary>

    @GET("api/v1/application-status-counts")
    suspend fun applicationStatusCounts(
        @Header("Authorization") authorization: String,
        @Query("work_date") workDate: String,
    ): List<ApplicationStatusCount>

    @GET("api/v1/profile")
    suspend fun profile(
        @Header("Authorization") authorization: String,
    ): UserProfile

    @GET("api/v1/applications/{id}")
    suspend fun application(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    ): ApplicationDetails

    @POST("api/v1/applications/{id}/close")
    suspend fun closeApplication(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("id") id: Long,
        @Body request: CloseApplicationRequest,
    ): OperationResult

    @POST("api/v1/applications/{id}/rework")
    suspend fun sendToRework(
        @Header("Authorization") authorization: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("id") id: Long,
    ): OperationResult
}
