package com.codetree.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codetree.data.platform.toAppError
import com.codetree.domain.ai.AiConfig
import com.codetree.domain.common.AppError
import com.codetree.domain.common.AppResult
import com.codetree.domain.content.AppSettings
import com.codetree.domain.content.BilingualMode
import com.codetree.domain.content.SettingsRepository
import com.codetree.domain.content.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * 设置仓库（DataStore Preferences + Keystore 秘密存储，docs/specs/ai-provider.md §5）。
 *
 * 持久化字段：onboardingDone / activeDirection / bilingualMode / themeMode / fontScale /
 * reduceMotion / aiConfig（baseUrl/model/temperature/maxTokens）。API Key 只保存
 * “是否已保存”标志，密文由 [SecretStore]（Keystore AES-GCM）持有，DataStore 无明文。
 *
 * 密钥库不可用时：`setApiKey` 返回失败且不保存，`hasApiKey` 保持 false；
 * `secretStoreAvailable` 供设置页提示“本机无法安全保存密钥”。
 */
class SettingsRepositoryImpl(
    context: Context,
    private val secretStore: SecretStore = AndroidKeystoreSecretStore(context),
) : SettingsRepository {

    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = appContext.settingsDataStore

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** 首次从 DataStore 读到设置前为 false；App 根用它避免闪现错误主题 / 引导。 */
    val isLoaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 设备密钥库是否可用（设置页据此提示）。 */
    val secretStoreAvailable: Boolean get() = secretStore.available

    init {
        scope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .collect { prefs ->
                    _settings.value = decode(prefs)
                    _loaded.value = true
                }
        }
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings): AppResult<Unit> = try {
        val next = transform(_settings.value)
        dataStore.edit { prefs -> writeAll(prefs, next) }
        AppResult.Success(Unit)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        AppResult.Failure(t.toAppError("保存设置"))
    }

    override suspend fun setApiKey(apiKey: String?): AppResult<Unit> {
        if (apiKey.isNullOrBlank()) {
            secretStore.clear()
            return runCatching {
                dataStore.edit { prefs -> prefs[KEY_HAS_API_KEY] = false }
                AppResult.Success(Unit)
            }.getOrElse { AppResult.Failure(it.toAppError("清除密钥")) }
        }
        val trimmed = apiKey.trim()
        val saved = secretStore.put(trimmed)
        if (!saved) {
            return AppResult.Failure(
                AppError.Internal("设备密钥库不可用，密钥未保存（不会降级为明文存储）"),
            )
        }
        return runCatching {
            dataStore.edit { prefs -> prefs[KEY_HAS_API_KEY] = true }
            AppResult.Success(Unit)
        }.getOrElse { AppResult.Failure(it.toAppError("保存密钥状态")) }
    }

    override suspend fun apiKey(): String? = secretStore.get()

    // ------------------------------------------------------------------ 编解码

    private fun decode(prefs: Preferences): AppSettings {
        val aiConfig = AiConfig(
            baseUrl = prefs[KEY_AI_BASE_URL].orEmpty(),
            model = prefs[KEY_AI_MODEL].orEmpty(),
            temperature = prefs[KEY_AI_TEMPERATURE] ?: 0.3,
            maxTokens = prefs[KEY_AI_MAX_TOKENS],
        )
        return AppSettings(
            onboardingDone = prefs[KEY_ONBOARDING_DONE] ?: false,
            activeDirection = prefs[KEY_ACTIVE_DIRECTION]?.takeIf { it.isNotBlank() },
            bilingualMode = decodeEnum(prefs[KEY_BILINGUAL_MODE], BilingualMode.SIDE_BY_SIDE),
            themeMode = decodeEnum(prefs[KEY_THEME_MODE], ThemeMode.SYSTEM),
            fontScale = (prefs[KEY_FONT_SCALE] ?: 1f).coerceIn(0.8f, 2f),
            reduceMotion = prefs[KEY_REDUCE_MOTION] ?: false,
            aiConfig = aiConfig,
            hasApiKey = prefs[KEY_HAS_API_KEY] ?: false,
        )
    }

    private fun writeAll(prefs: androidx.datastore.preferences.core.MutablePreferences, value: AppSettings) {
        prefs[KEY_ONBOARDING_DONE] = value.onboardingDone
        val direction = value.activeDirection
        if (direction.isNullOrBlank()) prefs.remove(KEY_ACTIVE_DIRECTION) else prefs[KEY_ACTIVE_DIRECTION] = direction
        prefs[KEY_BILINGUAL_MODE] = value.bilingualMode.name
        prefs[KEY_THEME_MODE] = value.themeMode.name
        prefs[KEY_FONT_SCALE] = value.fontScale
        prefs[KEY_REDUCE_MOTION] = value.reduceMotion
        prefs[KEY_AI_BASE_URL] = value.aiConfig.baseUrl
        prefs[KEY_AI_MODEL] = value.aiConfig.model
        prefs[KEY_AI_TEMPERATURE] = value.aiConfig.temperature
        val maxTokens = value.aiConfig.maxTokens
        if (maxTokens == null) prefs.remove(KEY_AI_MAX_TOKENS) else prefs[KEY_AI_MAX_TOKENS] = maxTokens
    }

    private inline fun <reified T : Enum<T>> decodeEnum(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }

    companion object {
        const val DATA_STORE_NAME = "codetree_settings"

        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding.done")
        private val KEY_ACTIVE_DIRECTION = stringPreferencesKey("learning.activeDirection")
        private val KEY_BILINGUAL_MODE = stringPreferencesKey("display.bilingualMode")
        private val KEY_THEME_MODE = stringPreferencesKey("display.themeMode")
        private val KEY_FONT_SCALE = floatPreferencesKey("display.fontScale")
        private val KEY_REDUCE_MOTION = booleanPreferencesKey("display.reduceMotion")
        private val KEY_AI_BASE_URL = stringPreferencesKey("ai.baseUrl")
        private val KEY_AI_MODEL = stringPreferencesKey("ai.model")
        private val KEY_AI_TEMPERATURE = doublePreferencesKey("ai.temperature")
        private val KEY_AI_MAX_TOKENS = intPreferencesKey("ai.maxTokens")
        private val KEY_HAS_API_KEY = booleanPreferencesKey("ai.hasApiKey")
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = SettingsRepositoryImpl.DATA_STORE_NAME)
