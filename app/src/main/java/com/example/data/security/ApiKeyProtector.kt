package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ApiKeyProtector {
  fun encrypt(plainText: String): String
  fun decrypt(envelope: String): String
}

internal interface KeystoreKeyStoreBackend {
  fun loadKey(alias: String): SecretKey?
  fun generateKey(alias: String): SecretKey
  fun deleteKey(alias: String)
}

internal class AndroidKeystoreBackend : KeystoreKeyStoreBackend {
  override fun loadKey(alias: String): SecretKey? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    return keyStore.getKey(alias, null) as? SecretKey
  }

  override fun generateKey(alias: String): SecretKey {
    val keyGenerator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      ANDROID_KEYSTORE
    )
    val spec = KeyGenParameterSpec.Builder(
      alias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(KEY_SIZE_BITS)
      .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  override fun deleteKey(alias: String) {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (keyStore.containsAlias(alias)) {
      keyStore.deleteEntry(alias)
    }
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_SIZE_BITS = 256
  }
}

class AndroidKeystoreApiKeyProtector internal constructor(
  private val keyAlias: String,
  private val backend: KeystoreKeyStoreBackend
) : ApiKeyProtector {

  constructor(keyAlias: String = KEY_ALIAS) : this(keyAlias, AndroidKeystoreBackend())

  override fun encrypt(plainText: String): String {
    require(plainText.isNotEmpty()) { "API key cannot be empty." }

    return try {
      encryptWithKey(plainText, getOrCreateKey())
    } catch (error: Exception) {
      if (!isRecoverableKeystoreError(error)) throw error
      runCatching { backend.deleteKey(keyAlias) }
      encryptWithKey(plainText, getOrCreateKey())
    }
  }

  override fun decrypt(envelope: String): String {
    val parts = envelope.split(':', limit = 3)
    require(parts.size == 3 && parts[0] == VERSION) {
      "Unsupported encrypted API key format."
    }

    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
    val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)

    val cipher = Cipher.getInstance(TRANSFORMATION)
    try {
      cipher.init(
        Cipher.DECRYPT_MODE,
        getOrCreateKey(),
        GCMParameterSpec(GCM_TAG_BITS, iv)
      )
    } catch (error: Exception) {
      if (isBrokenKeyEntryError(error)) {
        runCatching { backend.deleteKey(keyAlias) }
      }
      throw error
    }
    cipher.updateAAD(AAD)

    return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
  }

  private fun encryptWithKey(plainText: String, key: SecretKey): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    cipher.updateAAD(AAD)

    val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    val payload = Base64.encodeToString(cipherText, Base64.NO_WRAP)

    return "$VERSION:$iv:$payload"
  }

  @Synchronized
  private fun getOrCreateKey(): SecretKey {
    backend.loadKey(keyAlias)?.let { return it }
    return backend.generateKey(keyAlias)
  }

  private fun isBrokenKeyEntryError(error: Throwable): Boolean =
    error is UnrecoverableKeyException ||
      error is KeyPermanentlyInvalidatedException ||
      error is InvalidKeyException

  private fun isRecoverableKeystoreError(error: Throwable): Boolean =
    isBrokenKeyEntryError(error) || error is KeyStoreException

  companion object {
    private const val KEY_ALIAS = "nextdns_api_key_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION = "v1"
    private const val GCM_TAG_BITS = 128
    private val AAD = "nextdns-api-key-v1".toByteArray(Charsets.UTF_8)
  }
}
