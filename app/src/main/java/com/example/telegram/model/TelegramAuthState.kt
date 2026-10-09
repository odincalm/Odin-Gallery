package com.example.telegram.model

sealed interface TelegramAuthState {
    object Uninitialized : TelegramAuthState
    object Connecting : TelegramAuthState
    object WaitPhoneNumber : TelegramAuthState
    data class WaitCode(val phoneNumber: String, val isCodeViaApp: Boolean = true) : TelegramAuthState
    data class WaitPassword(val hint: String? = null) : TelegramAuthState
    data class Ready(val user: TelegramUser) : TelegramAuthState
    object LoggingOut : TelegramAuthState
    data class Error(val message: String) : TelegramAuthState
}
