package com.suraj.apps.omni.core.data.provider

enum class AiProviderId(
    val storageKey: String,
    val displayName: String,
    val requiresApiKey: Boolean,
    val keyHelpUrl: String?
) {
    OMNI(
        storageKey = "omni",
        displayName = "Omni AI",
        requiresApiKey = false,
        keyHelpUrl = null
    ),
    OPENAI(
        storageKey = "openai",
        displayName = "OpenAI",
        requiresApiKey = true,
        keyHelpUrl = "https://platform.openai.com/api-keys"
    ),
    GEMINI(
        storageKey = "gemini",
        displayName = "Google Gemini",
        requiresApiKey = true,
        keyHelpUrl = "https://aistudio.google.com/app/apikey"
    ),
    ANTHROPIC(
        storageKey = "anthropic",
        displayName = "Anthropic Claude",
        requiresApiKey = true,
        keyHelpUrl = "https://console.anthropic.com/settings/keys"
    ),
    DEEPSEEK(
        storageKey = "deepseek",
        displayName = "DeepSeek",
        requiresApiKey = true,
        keyHelpUrl = "https://platform.deepseek.com/api_keys"
    ),
    QWEN(
        storageKey = "qwen",
        displayName = "Qwen (Alibaba)",
        requiresApiKey = true,
        keyHelpUrl = "https://bailian.console.aliyun.com/"
    );

    companion object {
        fun fromStorageKey(rawValue: String?): AiProviderId {
            if (rawValue.isNullOrBlank()) return OMNI
            return entries.firstOrNull { it.storageKey == rawValue } ?: OMNI
        }
    }
}

data class AiProviderOption(
    val providerId: AiProviderId,
    val hasSavedApiKey: Boolean
)

data class AiProviderSettingsSnapshot(
    val selectedProvider: AiProviderId,
    val options: List<AiProviderOption>
)

data class ApiKeyValidationResult(
    val isValid: Boolean,
    val message: String
)

data class ProviderResolution(
    val providerId: AiProviderId,
    val apiKey: String?
)
