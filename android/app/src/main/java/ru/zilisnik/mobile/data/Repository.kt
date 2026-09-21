package ru.zilisnik.mobile.data

import com.squareup.okhttp.logging.HttpLoggingInterceptor
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
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    suspend fun register(code: String, deviceName: String) {
        token = api.register(RegisterRequest(code.trim(), deviceName)).access_token
    }

    suspend fun applications(): List<ApplicationSummary> =
        api.applications(auth())

    suspend fun application(id: Long): ApplicationDetails =
        api.application(auth(), id)

    suspend fun close(id: Long, request: CloseApplicationRequest): OperationResult =
        api.closeApplication(auth(), UUID.randomUUID().toString(), id, request)

    private fun auth(): String = "Bearer ${requireNotNull(token) { "Требуется вход" }}"
}

