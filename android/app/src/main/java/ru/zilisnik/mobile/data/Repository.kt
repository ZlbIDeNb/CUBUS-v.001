package ru.zilisnik.mobile.data

import com.google.gson.JsonParser
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.zilisnik.mobile.BuildConfig
import java.util.UUID

class Repository {
    private val api: ApiService
    private var token: String? = null

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
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
    }

    suspend fun profile(): UserProfile = api.profile(auth())

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

    suspend fun metrologWarehouse(): List<WarehouseItem> =
        api.metrologWarehouse(auth())

    suspend fun applications(status: String, workDate: String): List<ApplicationSummary> =
        api.applications(auth(), status, workDate)

    suspend fun applicationStatusCounts(workDate: String): List<ApplicationStatusCount> =
        api.applicationStatusCounts(auth(), workDate)

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
