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

    @GET("api/v1/application-map-points")
    suspend fun applicationMapPoints(
        @Header("Authorization") authorization: String,
        @Query("work_date") workDate: String,
    ): List<ApplicationMapPoint>

    @GET("api/v1/profile")
    suspend fun profile(
        @Header("Authorization") authorization: String,
    ): UserProfile

    @POST("api/v1/profile/home-address")
    suspend fun saveHomeAddress(
        @Header("Authorization") authorization: String,
        @Body request: HomeAddressRequest,
    ): HomeAddress

    @GET("api/v1/schedule")
    suspend fun schedule(
        @Header("Authorization") authorization: String,
        @Query("year") year: Int,
        @Query("month") month: Int,
        @Query("refresh") refresh: Boolean = false,
    ): List<ScheduleDay>

    @GET("api/v1/material-usage")
    suspend fun materialUsage(
        @Header("Authorization") authorization: String,
        @Query("work_date") workDate: String,
    ): List<MaterialUsageItem>

    @GET("api/v1/metrolog-warehouse")
    suspend fun metrologWarehouse(
        @Header("Authorization") authorization: String,
    ): List<WarehouseItem>

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
        @Body request: ReworkRequest,
    ): OperationResult

    @GET("api/v1/rework-reasons")
    suspend fun reworkReasons(
        @Header("Authorization") authorization: String,
    ): List<String>

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
        @Query("refresh") refresh: Boolean = false,
    ): List<PriceListItem>

    @POST("api/v1/applications/{id}/nomenclature")
    suspend fun addNomenclature(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Body request: AddNomenclatureRequest,
    )

    @DELETE("api/v1/applications/{id}/nomenclature/{nomenclatureId}")
    suspend fun deleteNomenclature(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Path("nomenclatureId") nomenclatureId: Long,
    )

    @GET("api/v1/meter-catalog")
    suspend fun meterCatalog(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("refresh") refresh: Boolean = false,
    ): List<MeterCatalogItem>

    @POST("api/v1/applications/{id}/meters")
    suspend fun addMeter(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Body request: AddMeterRequest,
    ): WaterMeter

    @retrofit2.http.PATCH("api/v1/applications/{id}/meters/{meterId}")
    suspend fun updateMeter(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Path("meterId") meterId: Long,
        @Body request: AddMeterRequest,
    ): WaterMeter

    @DELETE("api/v1/applications/{id}/meters/{meterId}")
    suspend fun deleteMeter(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Path("meterId") meterId: Long,
    )

    @DELETE("api/v1/applications/{id}/photos")
    suspend fun deletePhoto(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
        @Query("field") field: String,
        @Query("filename") filename: String,
    )
}
