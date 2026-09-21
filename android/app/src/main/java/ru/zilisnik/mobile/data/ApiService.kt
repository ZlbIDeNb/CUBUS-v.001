package ru.zilisnik.mobile.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @GET("api/v1/applications")
    suspend fun applications(@Header("Authorization") authorization: String): List<ApplicationSummary>

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
}

