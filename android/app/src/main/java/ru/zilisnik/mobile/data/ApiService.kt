package ru.zilisnik.mobile.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.DELETE
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

    @POST("api/v1/applications/{id}/photos")
    suspend fun uploadPhoto(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Body request: UploadPhotoRequest,
    )

    @GET("api/v1/applications/{id}/photos/content")
    suspend fun photoContent(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Query("field") field: String,
        @Query("filename") filename: String,
    ): PhotoContent

    @GET("api/v1/applications/{id}/nomenclature")
    suspend fun nomenclature(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    ): List<NomenclatureItem>

    @GET("api/v1/price-list")
    suspend fun priceList(
        @Header("Authorization") authorization: String,
    ): List<PriceListItem>

    @POST("api/v1/applications/{id}/nomenclature")
    suspend fun addNomenclature(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Body request: AddNomenclatureRequest,
    )

    @POST("api/v1/applications/{id}/meters")
    suspend fun addMeter(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Body request: AddMeterRequest,
    )

    @DELETE("api/v1/applications/{id}/photos")
    suspend fun deletePhoto(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Query("field") field: String,
        @Query("filename") filename: String,
    )
}
