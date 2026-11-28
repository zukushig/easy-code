package com.codetree.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 秘密存储抽象（docs/specs/ai-provider.md §5）：
 * 只在内存中出现明文，持久化只保存密文；不可用时 [put] 返回 false，由 UI 明确告知“未保存”，
 * 不允许降级为明文存储。
 */
interface SecretStore {
    val available: Boolean
    suspend fun put(secret: String): Boolean
    suspend fun get(): String?
    suspend fun clear(): Boolean
}

/**
 * Android Keystore 实现：AES-256-GCM 不可导出密钥（alias `codetree.ai.key`），
 * 密文格式 `Base64(ivSize(1) + iv + cipherText)` 存 DataStore（`codetree_secrets`）。
 * 设备密钥库不可用（模拟器异常 / 密钥被系统清除）时 [available] = false，不崩溃。
 */
class AndroidKeystoreSecretStore(context: Context) : SecretStore {

    private val dataStore: DataStore<Preferences> = context.applicationContext.secretsDataStore

    override val available: Boolean by lazy {
        runCatching {
            loadOrCreateKey()
            true
        }.getOrDefault(false)
    }

    override suspend fun put(secret: String): Boolean = withContext(Dispatchers.IO) {
        if (secret.isBlank()) return@withContext clear()
        if (!available) return@withContext false
        val encrypted = runCatching { encrypt(secret) }.getOrNull() ?: return@withContext false
        runCatching { dataStore.edit { prefs -> prefs[KEY_SECRET] = encrypted } }.isSuccess
    }

    override suspend fun get(): String? = withContext(Dispatchers.IO) {
        val stored = runCatching { dataStore.data.first()[KEY_SECRET] }.getOrNull()
        if (stored.isNullOrBlank()) null else runCatching { decrypt(stored) }.getOrNull()
    }

    override suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        runCatching { dataStore.edit { prefs -> prefs.remove(KEY_SECRET) } }.isSuccess
    }

    // ------------------------------------------------------------------ 加解密

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + cipherText.size)
        combined[0] = iv.size.toByte()
        iv.copyInto(combined, destinationOffset = 1)
        cipherText.copyInto(combined, destinationOffset = 1 + iv.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(encoded: String): String? {
        val combined = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return null
        if (combined.size < 2) return null
        val ivSize = combined[0].toInt() and 0xFF
        if (ivSize <= 0 || combined.size < 1 + ivSize + 1) return null
        val iv = combined.copyOfRange(1, 1 + ivSize)
        val cipherText = combined.copyOfRange(1 + ivSize, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return runCatching { String(cipher.doFinal(cipherText), Charsets.UTF_8) }.getOrNull()
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val KEY_ALIAS = "codetree.ai.key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private val KEY_SECRET = stringPreferencesKey("ai.key.encrypted")
    }
}

private val Context.secretsDataStore by preferencesDataStore(name = "codetree_secrets")
