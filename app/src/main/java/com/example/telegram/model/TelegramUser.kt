package com.example.telegram.model

data class TelegramUser(
    val id: Long,
    val firstName: String,
    val lastName: String = "",
    val username: String? = null,
    val phoneNumber: String? = null
) {
    val fullName: String
        get() = if (lastName.isNotBlank()) "$firstName $lastName".trim() else firstName

    val displayHandle: String
        get() = username?.let { "@$it" } ?: (phoneNumber ?: "ID: $id")

    val maskedPhoneNumber: String?
        get() = phoneNumber?.let { phone ->
            if (phone.length > 6) {
                "${phone.take(3)} *** *** ${phone.takeLast(2)}"
            } else {
                phone
            }
        }
}
