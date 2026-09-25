package ru.zilisnik.mobile.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("api/v1/app-update")
    suspend fun appUpdate(
        @Query("version_code") versionCode: Int,
    ): AppUpdateInfo

    @GET("api/v1/weather")
    suspend fun weather(
        @Header("Authorization") authorization: String,
    ): WeatherSnapshot

    @GET("api/v1/documents")
    suspend fun documents(
        @Header("Authorization") authorization: String,
    ): List<DocumentationItem>

    @POST("api/v1/documents")
    suspend fun addDocument(
        @Header("Authorization") authorization: String,
        @Body request: DocumentationCreate,
    ): DocumentationItem

    @GET("api/v1/documents/{id}/content")
    suspend fun documentContent(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    ): DocumentationContent

    @DELETE("api/v1/documents/{id}")
    suspend fun deleteDocument(
        @Header("Authorization") authorization: String,
        @Path("id") id: Long,
    )

    @GET("api/v1/reports/period")
    suspend fun periodReport(
        @Header("Authorization") authorization: String,
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
    ): PeriodReport

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): TokenResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("api/v1/auth/change-password")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body request: ChangePasswordRequest,
    ): TokenResponse

    @GET("api/v1/admin/users")
    suspend fun adminUsers(
        @Header("Authorization") authorization: String,
    ): List<AdminUser>

    @POST("api/v1/admin/users")
    suspend fun createAdminUser(
        @Header("Authorization") authorization: String,
        @Body request: AdminUserCreateRequest,
    ): AdminUserCreated

    @POST("api/v1/admin/users/{email}/reset-password")
    suspend fun resetAdminUserPassword(
        @Header("Authorization") authorization: String,
        @Path("email") email: String,
    ): AdminUserCreated

    @retrofit2.http.PATCH("api/v1/admin/users/{email}/active")
    suspend fun setAdminUserActive(
        @Header("Authorization") authorization: String,
        @Path("email") email: String,
        @Body request: AdminUserActiveRequest,
    ): AdminUser

    @GET("api/v1/admin/users/{email}/workspace")
    suspend fun adminEmployeeWorkspace(
        @Header("Authorization") authorization: String,
        @Path("email") email: String,
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
    ): AdminEmployeeWorkspace

    @GET("api/v1/admin/users/{email}/applications/{id}")
    suspend fun adminEmployeeApplication(
        @Header("Authorization") authorization: String,
        @Path("email") email: String,
        @Path("id") id: Long,
    ): ApplicationDetails

    @POST("api/v1/admin/users/{email}/documents")
    suspend fun addAdminEmployeeDocument(
        @Header("Authorization") authorization: String,
        @Path("email") email: String,
        @Body request: DocumentationCreate,
    ): DocumentationItem

    @PUT("api/v1/admin/users/{email}/documents/{id}")
    suspend fun replaceAdminEmployeeDocument(
        @Header("Authorization") authorization: String,
        @Path("email") email: String,
        @Path("id") id: Long,
        @Body request: DocumentationCreate,
    ): DocumentationItem

    @GET("api/v1/admin/users/{email}/documents/{id}/content")
    suspend fun adminEmployeeDocumentContent(
        @Header("Authorization") authorization: String,
        @Path("email") email: String,
        @Path("id") id: Long,
    ): DocumentationContent

    @DELETE("api/v1/admin/users/{email}/documents/{id}")
    suspend fun deleteAdminEmployeeDocument(
        @Header("Authorization") authorization: String,
        @Path("email") email: String,
        @Path("id") id: Long,
    )

    @GET("api/v1/admin/registry")
    suspend fun registry(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 200,
    ): List<RegistryAddress>

    @GET("api/v1/admin/registry/stats")
    suspend fun registryStats(
        @Header("Authorization") authorization: String,
    ): RegistryStats

    @POST("api/v1/admin/registry/sync")
    suspend fun syncRegistry(
        @Header("Authorization") authorization: String,
    ): RegistryStats

    @GET("api/v1/applications")
    suspend fun applications(
        @Header("Authorization") authorization: String,
        @Query("status_filter") status: String?,
        @Query("work_date") workDate: String,
    ): List<ApplicationSummary>

    @GET("api/v1/application-status-counts")
    suspend fun applicationStatusCounts(
        @Header("Authorization") authorization: String,
        @Query("work_date") workDate: String,
    ): List<ApplicationStatusCount>

    @GET("api/v1/daily-statistics")
    suspend fun dailyStatistics(
        @Header("Authorization") authorization: String,
        @Query("days") days: Int = 31,
    ): List<DailyStatistics>

    @GET("api/v1/today-statistics")
    suspend fun todayStatistics(
        @Header("Authorization") authorization: String,
        @Query("work_date") workDate: String,
    ): TodayStatistics

    @GET("api/v1/today-statistics/history")
    suspend fun todayStatisticsHistory(
        @Header("Authorization") authorization: String,
        @Query("work_date") workDate: String,
    ): List<TodayStatistics>

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

    @POST("api/v1/profile/administrative-expenses")
    suspend fun saveAdministrativeExpenses(
        @Header("Authorization") authorization: String,
        @Body request: AdministrativeExpensesRequest,
    ): UserProfile

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
        @Query("refresh") refresh: Boolean = false,
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
