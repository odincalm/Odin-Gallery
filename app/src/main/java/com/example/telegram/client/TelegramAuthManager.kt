package com.example.telegram.client

import android.content.Context
import com.example.telegram.model.TelegramAuthState
import com.example.telegram.model.TelegramUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

object TelegramAuthManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Uninitialized)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private var currentPhoneNumber: String = ""
    private var updatesJob: Job? = null
    private var isObserving = false
    private var isInitializing = false

    fun init(context: Context) {
        scope.launch {
            try {
                startObservingUpdates(context)
                TelegramClientHolder.getClient(context)

                // Query current state directly from TDLib so we never miss pre-emitted states
                withTimeoutOrNull(4000L) {
                    try {
                        val currentState = TelegramClientHolder.send(context, TdApi.GetAuthorizationState())
                        handleAuthorizationState(context, currentState)
                    } catch (e: Exception) {
                        // Updates will handle subsequent transitions
                    }
                }
            } catch (e: Throwable) {
                _authState.value = TelegramAuthState.Error("TDLib initialization failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private fun startObservingUpdates(context: Context) {
        if (isObserving) return
        isObserving = true

        updatesJob = scope.launch {
            TelegramClientHolder.getUpdates(context).collect { update ->
                if (update is TdApi.UpdateAuthorizationState) {
                    handleAuthorizationState(context, update.authorizationState)
                }
            }
        }
    }

    private suspend fun handleAuthorizationState(context: Context, state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                _authState.value = TelegramAuthState.Connecting
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = TelegramAuthState.WaitPhoneNumber
            }
            is TdApi.AuthorizationStateWaitCode -> {
                _authState.value = TelegramAuthState.WaitCode(
                    phoneNumber = currentPhoneNumber,
                    isCodeViaApp = state.codeInfo?.type is TdApi.AuthenticationCodeTypeTelegramMessage
                )
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _authState.value = TelegramAuthState.WaitPassword(hint = state.passwordHint)
            }
            is TdApi.AuthorizationStateReady -> {
                try {
                    val me = TelegramClientHolder.sendWithTimeout(context, TdApi.GetMe(), 10_000L)
                    val username = me.usernames?.activeUsernames?.firstOrNull() ?: me.usernames?.editableUsername
                    val user = TelegramUser(
                        id = me.id,
                        firstName = me.firstName,
                        lastName = me.lastName,
                        username = username,
                        phoneNumber = me.phoneNumber
                    )
                    _authState.value = TelegramAuthState.Ready(user)
                } catch (e: Exception) {
                    _authState.value = TelegramAuthState.Error(e.message ?: "Failed to retrieve Telegram profile")
                }
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                _authState.value = TelegramAuthState.LoggingOut
            }
            is TdApi.AuthorizationStateClosed -> {
                _authState.value = TelegramAuthState.Uninitialized
                TelegramSavedMessagesHelper.clearCachedChatId()
                TelegramClientHolder.closeClient()
                isObserving = false
            }
            else -> {
                // Other transient states (e.g., AuthorizationStateClosing)
            }
        }
    }

    suspend fun sendPhoneNumber(context: Context, phoneNumber: String): Result<Unit> {
        return try {
            currentPhoneNumber = phoneNumber.trim()
            _authState.value = TelegramAuthState.Connecting
            TelegramClientHolder.sendWithTimeout(
                context,
                TdApi.SetAuthenticationPhoneNumber(currentPhoneNumber, null),
                20_000L
            )
            // Immediately query state to advance without waiting on async update scheduling
            withTimeoutOrNull(3000L) {
                val state = TelegramClientHolder.send(context, TdApi.GetAuthorizationState())
                handleAuthorizationState(context, state)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val safeMessage = formatSafeError(e.message)
            _authState.value = TelegramAuthState.Error(safeMessage)
            Result.failure(Exception(safeMessage))
        }
    }

    suspend fun sendCode(context: Context, code: String): Result<Unit> {
        return try {
            _authState.value = TelegramAuthState.Connecting
            TelegramClientHolder.sendWithTimeout(
                context,
                TdApi.CheckAuthenticationCode(code.trim()),
                20_000L
            )
            withTimeoutOrNull(3000L) {
                val state = TelegramClientHolder.send(context, TdApi.GetAuthorizationState())
                handleAuthorizationState(context, state)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val safeMessage = formatSafeError(e.message)
            _authState.value = TelegramAuthState.Error(safeMessage)
            Result.failure(Exception(safeMessage))
        }
    }

    suspend fun sendPassword(context: Context, password: String): Result<Unit> {
        return try {
            _authState.value = TelegramAuthState.Connecting
            TelegramClientHolder.sendWithTimeout(
                context,
                TdApi.CheckAuthenticationPassword(password),
                20_000L
            )
            withTimeoutOrNull(3000L) {
                val state = TelegramClientHolder.send(context, TdApi.GetAuthorizationState())
                handleAuthorizationState(context, state)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val safeMessage = formatSafeError(e.message)
            _authState.value = TelegramAuthState.Error(safeMessage)
            Result.failure(Exception(safeMessage))
        }
    }

    suspend fun logOut(context: Context): Result<Unit> {
        return try {
            _authState.value = TelegramAuthState.LoggingOut
            TelegramClientHolder.sendWithTimeout(context, TdApi.LogOut(), 15_000L)
            TelegramSavedMessagesHelper.clearCachedChatId()
            _authState.value = TelegramAuthState.Uninitialized
            Result.success(Unit)
        } catch (e: Exception) {
            TelegramSavedMessagesHelper.clearCachedChatId()
            TelegramClientHolder.closeClient()
            _authState.value = TelegramAuthState.Uninitialized
            Result.failure(e)
        }
    }

    fun cancelAuthentication() {
        if (_authState.value is TelegramAuthState.Connecting || _authState.value is TelegramAuthState.Error) {
            _authState.value = if (currentPhoneNumber.isNotBlank()) {
                TelegramAuthState.WaitPhoneNumber
            } else {
                TelegramAuthState.Uninitialized
            }
        }
    }

    fun resetError() {
        if (_authState.value is TelegramAuthState.Error) {
            _authState.value = if (currentPhoneNumber.isNotBlank()) {
                TelegramAuthState.WaitPhoneNumber
            } else {
                TelegramAuthState.Uninitialized
            }
        }
    }

    private fun formatSafeError(raw: String?): String {
        if (raw == null) return "An unexpected error occurred. Please try again."
        return when {
            raw.contains("PHONE_NUMBER_INVALID", ignoreCase = true) ->
                "Invalid phone number. Please enter in full international format (e.g. +1234567890)."
            raw.contains("PHONE_CODE_INVALID", ignoreCase = true) ->
                "Incorrect verification code. Please check and try again."
            raw.contains("PHONE_CODE_EXPIRED", ignoreCase = true) ->
                "The verification code has expired. Please request a new code."
            raw.contains("PASSWORD_HASH_INVALID", ignoreCase = true) ->
                "Incorrect Two-Step Verification password."
            raw.contains("FLOOD_WAIT", ignoreCase = true) ->
                "Too many attempts. Telegram requires waiting before trying again."
            raw.contains("Timed out", ignoreCase = true) ->
                "Connection timed out. Please check your network and try again."
            else -> raw
        }
    }
}
