package ru.zilisnik.mobile.data

import android.content.Context
import com.google.gson.JsonParser
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.zilisnik.mobile.BuildConfig
import java.util.UUID

class Repository(context: Context) {
    private val api: ApiService
    private val tokenStore = SecureTokenStore(context.applicationContext)
    private var token: String? = tokenStore.load()

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    suspend fun register(code: String, deviceName: String) {
        val response = api.register(RegisterRequest(code.trim(), deviceName))
        token = response.access_token
        tokenStore.save(response.access_token)
    }

    suspend fun login(email: String, password: String, deviceName: String): Boolean {
        val response = try {
            api.login(LoginRequest(email.trim(), password, deviceName))
        } catch (error: HttpException) {
            throw IllegalStateException(apiErrorDetail(error))
        }
        token = response.access_token
        tokenStore.save(response.access_token, response.must_change_password)
        return response.must_change_password
    }

    fun hasSavedSession(): Boolean = !token.isNullOrBlank()
    fun requiresPasswordChange(): Boolean = tokenStore.requiresPasswordChange()

    suspend fun appUpdate(versionCode: Int): AppUpdateInfo? = try {
        api.appUpdate(versionCode).takeIf { it.update_available }
    } catch (_: Exception) {
        null
    }

    fun logout() {
        token = null
        tokenStore.clear()
    }

    suspend fun changePassword(newPassword: String) {
        val response = try {
            api.changePassword(auth(), ChangePasswordRequest(newPassword))
        } catch (error: HttpException) {
            throw IllegalStateException(apiErrorDetail(error))
        }
        token = response.access_token
        tokenStore.save(response.access_token, false)
    }

    suspend fun adminUsers(): List<AdminUser> = try {
        api.adminUsers(auth())
    } catch (error: HttpException) {
        throw IllegalStateException(apiErrorDetail(error))
    }

    suspend fun createAdminUser(request: AdminUserCreateRequest): AdminUserCreated = try {
        api.createAdminUser(auth(), request)
    } catch (error: HttpException) {
        throw IllegalStateException(apiErrorDetail(error))
    }

    suspend fun resetAdminUserPassword(email: String): AdminUserCreated = try {
        api.resetAdminUserPassword(auth(), email)
    } catch (error: HttpException) {
        throw IllegalStateException(apiErrorDetail(error))
    }

    suspend fun setAdminUserActive(email: String, active: Boolean): AdminUser = try {
        api.setAdminUserActive(auth(), email, AdminUserActiveRequest(active))
    } catch (error: HttpException) {
        throw IllegalStateException(apiErrorDetail(error))
    }

    suspend fun adminEmployeeWorkspace(
        email: String, dateFrom: String, dateTo: String,
    ): AdminEmployeeWorkspace = try {
        api.adminEmployeeWorkspace(auth(), email, dateFrom, dateTo)
    } catch (error: HttpException) {
        throw IllegalStateException(apiErrorDetail(error))
    }

    suspend fun adminEmployeeApplication(email: String, id: Long): ApplicationDetails = try {
        api.adminEmployeeApplication(auth(), email, id)
    } catch (error: HttpException) {
        throw IllegalStateException(apiErrorDetail(error))
    }

    suspend fun addAdminEmployeeDocument(
        email: String, request: DocumentationCreate,
    ): DocumentationItem = api.addAdminEmployeeDocument(auth(), email, request)

    suspend fun replaceAdminEmployeeDocument(
        email: String, id: Long, request: DocumentationCreate,
    ): DocumentationItem = api.replaceAdminEmployeeDocument(auth(), email, id, request)

    suspend fun adminEmployeeDocumentContent(
        email: String, id: Long,
    ): DocumentationContent = api.adminEmployeeDocumentContent(auth(), email, id)

    suspend fun deleteAdminEmployeeDocument(email: String, id: Long) =
        api.deleteAdminEmployeeDocument(auth(), email, id)

    suspend fun registry(query: String): List<RegistryAddress> = try {
        api.registry(auth(), query)
    } catch (error: HttpException) {
        throw IllegalStateException(apiErrorDetail(error))
    }

    suspend fun registryStats(): RegistryStats = api.registryStats(auth())
    suspend fun syncRegistry(): RegistryStats = api.syncRegistry(auth())

    suspend fun profile(): UserProfile = api.profile(auth())

    suspend fun weather(): WeatherSnapshot = api.weather(auth())

    suspend fun saveAdministrativeExpenses(value: String): UserProfile =
        api.saveAdministrativeExpenses(auth(), AdministrativeExpensesRequest(value))

    suspend fun documents(): List<DocumentationItem> = api.documents(auth())

    suspend fun addDocument(request: DocumentationCreate): DocumentationItem =
        api.addDocument(auth(), request)

    suspend fun documentContent(id: Long): DocumentationContent =
        api.documentContent(auth(), id)

    suspend fun deleteDocument(id: Long) = api.deleteDocument(auth(), id)

    suspend fun periodReport(dateFrom: String, dateTo: String): PeriodReport =
        api.periodReport(auth(), dateFrom, dateTo)

    suspend fun saveHomeAddress(address: String): HomeAddress =
        try {
            api.saveHomeAddress(auth(), HomeAddressRequest(address.trim()))
        } catch (error: HttpException) {
            throw IllegalStateException(apiErrorDetail(error))
        }

    suspend fun schedule(year: Int, month: Int, refresh: Boolean = false): List<ScheduleDay> =
        api.schedule(auth(), year, month, refresh)

    suspend fun materialUsage(workDate: String): List<MaterialUsageItem> =
        api.materialUsage(auth(), workDate)

    suspend fun metrologWarehouse(refresh: Boolean = false): List<WarehouseItem> =
        api.metrologWarehouse(auth(), refresh)

    suspend fun applications(status: String?, workDate: String): List<ApplicationSummary> =
        api.applications(auth(), status, workDate)

    suspend fun applicationStatusCounts(workDate: String): List<ApplicationStatusCount> =
        api.applicationStatusCounts(auth(), workDate)

    suspend fun dailyStatistics(days: Int = 31): List<DailyStatistics> =
        api.dailyStatistics(auth(), days)

    suspend fun todayStatistics(workDate: String): TodayStatistics =
        api.todayStatistics(auth(), workDate)

    suspend fun todayStatisticsHistory(workDate: String): List<TodayStatistics> =
        api.todayStatisticsHistory(auth(), workDate)

    suspend fun applicationMapPoints(workDate: String): List<ApplicationMapPoint> =
        try {
            api.applicationMapPoints(auth(), workDate)
        } catch (error: HttpException) {
            throw IllegalStateException(apiErrorDetail(error))
        }

    suspend fun application(id: Long): ApplicationDetails =
        api.application(auth(), id)

    suspend fun close(id: Long, request: CloseApplicationRequest): OperationResult =
        api.closeApplication(auth(), UUID.randomUUID().toString(), id, request)

    suspend fun reworkReasons(): List<String> = api.reworkReasons(auth())

    suspend fun sendToRework(id: Long, request: ReworkRequest): OperationResult =
        api.sendToRework(auth(), UUID.randomUUID().toString(), id, request)

    suspend fun uploadPhoto(id: Long, request: UploadPhotoRequest) {
        api.uploadPhoto(auth(), id, request)
    }

    suspend fun photoContent(id: Long, field: String, filename: String): PhotoContent =
        try {
            api.photoContent(auth(), id, field, filename)
        } catch (error: HttpException) {
            val body = error.response()?.errorBody()?.string().orEmpty()
            val detail = runCatching {
                JsonParser.parseString(body).asJsonObject["detail"].asString
            }.getOrNull()
            throw IllegalStateException(detail ?: "HTTP ${error.code()}")
        }

    suspend fun nomenclature(id: Long): List<NomenclatureItem> =
        api.nomenclature(auth(), id)

    suspend fun priceList(refresh: Boolean = false): List<PriceListItem> =
        api.priceList(auth(), refresh)

    suspend fun addNomenclature(id: Long, request: AddNomenclatureRequest) {
        api.addNomenclature(auth(), id, request)
    }

    suspend fun deleteNomenclature(id: Long, nomenclatureId: Long) {
        api.deleteNomenclature(auth(), id, nomenclatureId)
    }

    suspend fun meterCatalog(query: String, refresh: Boolean = false): List<MeterCatalogItem> =
        api.meterCatalog(auth(), query, refresh)

    suspend fun addMeter(id: Long, request: AddMeterRequest): WaterMeter =
        try {
            api.addMeter(auth(), id, request)
        } catch (error: HttpException) {
            throw IllegalStateException(apiErrorDetail(error))
        }

    suspend fun updateMeter(id: Long, meterId: Long, request: AddMeterRequest): WaterMeter =
        try {
            api.updateMeter(auth(), id, meterId, request)
        } catch (error: HttpException) {
            throw IllegalStateException(apiErrorDetail(error))
        }

    suspend fun deleteMeter(id: Long, meterId: Long) {
        try {
            api.deleteMeter(auth(), id, meterId)
        } catch (error: HttpException) {
            throw IllegalStateException(apiErrorDetail(error))
        }
    }

    suspend fun deletePhoto(id: Long, field: String, filename: String) {
        api.deletePhoto(auth(), id, field, filename)
    }

    private fun auth(): String = "Bearer ${requireNotNull(token) { "Требуется вход" }}"

    private fun apiErrorDetail(error: HttpException): String {
        val body = error.response()?.errorBody()?.string().orEmpty()
        return runCatching {
            JsonParser.parseString(body).asJsonObject["detail"].asString
        }.getOrNull() ?: "HTTP ${error.code()}"
    }
}
