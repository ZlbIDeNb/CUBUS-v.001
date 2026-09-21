package ru.zilisnik.mobile.data

data class RegisterRequest(val code: String, val device_name: String)
data class TokenResponse(val access_token: String, val token_type: String, val role: String)

data class ApplicationSummary(
    val id: Long,
    val number: String,
    val work_date: String?,
    val address: String,
    val client: String,
    val interval: String,
    val status: String,
)

data class ApplicationDetails(
    val id: Long,
    val number: String,
    val work_date: String?,
    val address: String,
    val client: String,
    val interval: String,
    val status: String,
    val phone_number: String,
    val floor: String,
    val entrance: String,
    val entrance_code: String,
    val barrier: String,
    val comments: String,
)

data class CloseApplicationRequest(
    val payment_type: String,
    val cash_sum: Int = 0,
    val card_sum: Int = 0,
)

data class OperationResult(val success: Boolean, val application_id: Long, val status: String)

