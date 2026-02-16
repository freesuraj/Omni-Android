package com.suraj.apps.omni.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.suraj.apps.omni.core.data.provider.AiProviderId
import com.suraj.apps.omni.core.data.provider.ProviderSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProviderOptionUiState(
    val providerId: AiProviderId,
    val title: String,
    val isSelected: Boolean,
    val hasSavedApiKey: Boolean
)

data class SettingsUiState(
    val providerOptions: List<ProviderOptionUiState> = emptyList(),
    val selectedProvider: AiProviderId = AiProviderId.OMNI,
    val apiKeyInput: String = "",
    val hasSavedApiKeyForSelectedProvider: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val appVersion: String = "1.0.0"
)

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository = ProviderSettingsRepository(application.applicationContext)
    private val appVersionName = resolveAppVersion(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(appVersion = appVersionName)
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectProvider(providerId: AiProviderId) {
        repository.selectProvider(providerId)
        refresh(clearInput = true)
    }

    fun updateApiKeyInput(input: String) {
        _uiState.update { it.copy(apiKeyInput = input) }
    }

    fun saveApiKey() {
        val selected = _uiState.value.selectedProvider
        val result = repository.saveApiKey(
            providerId = selected,
            rawApiKey = _uiState.value.apiKeyInput
        )

        if (result.isValid) {
            refresh(clearInput = true)
            _uiState.update { it.copy(infoMessage = result.message) }
        } else {
            _uiState.update { it.copy(errorMessage = result.message) }
        }
    }

    fun clearApiKey() {
        val selected = _uiState.value.selectedProvider
        repository.clearApiKey(selected)
        refresh(clearInput = true)
        _uiState.update {
            it.copy(infoMessage = "Cleared saved key for ${selected.displayName}.")
        }
    }

    fun consumeInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun refresh(clearInput: Boolean = false) {
        val snapshot = repository.loadSnapshot()
        val selectedProvider = snapshot.selectedProvider
        val options = snapshot.options.map { option ->
            ProviderOptionUiState(
                providerId = option.providerId,
                title = option.providerId.displayName,
                isSelected = option.providerId == selectedProvider,
                hasSavedApiKey = option.hasSavedApiKey
            )
        }
        val selectedHasSavedKey = options
            .firstOrNull { it.providerId == selectedProvider }
            ?.hasSavedApiKey
            ?: false

        _uiState.update {
            it.copy(
                providerOptions = options,
                selectedProvider = selectedProvider,
                hasSavedApiKeyForSelectedProvider = selectedHasSavedKey,
                apiKeyInput = if (clearInput) "" else it.apiKeyInput,
                appVersion = appVersionName
            )
        }
    }

    private fun resolveAppVersion(application: Application): String {
        return runCatching {
            @Suppress("DEPRECATION")
            application.packageManager
                .getPackageInfo(application.packageName, 0)
                .versionName
                .orEmpty()
                .ifBlank { "1.0.0" }
        }.getOrDefault("1.0.0")
    }
}
