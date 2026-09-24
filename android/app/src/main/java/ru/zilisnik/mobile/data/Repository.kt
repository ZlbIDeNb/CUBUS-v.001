package ru.zilisnik.mobile.data

import okhttp3.logging.HttpLoggingInterceptor
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
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
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

    suspend fun applications(status: String, workDate: String): List<ApplicationSummary> =
        api.applications(auth(), status, workDate)

    suspend fun applicationStatusCounts(workDate: String): List<ApplicationStatusCount> =
        api.applicationStatusCounts(auth(), workDate)

    suspend fun application(id: Long): ApplicationDetails =
        api.application(auth(), id)

    suspend fun close(id: Long, request: CloseApplicationRequest): OperationResult =
        api.closeApplication(auth(), UUID.randomUUID().toString(), id, request)

    suspend fun sendToRework(id: Long): OperationResult =
        api.sendToRework(auth(), UUID.randomUUID().toString(), id)

    private fun auth(): String = "Bearer ${requireNotNull(token) { "Требуется вход" }}"
}
