package com.example.data.security

import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidKeystoreApiKeyProtectorTest {

  private class InMemoryBackend(
    private val failLoadOnce: (() -> Throwable)? = null
  ) : KeystoreKeyStoreBackend {
    var storedKey: SecretKey? = null
    var deleteCalls = 0
    var generateCalls = 0
    private var loadFailed = false

    override fun loadKey(alias: String): SecretKey? {
      if (!loadFailed && failLoadOnce != null) {
        loadFailed = true
        throw failLoadOnce.invoke()
      }
      return storedKey
    }

    override fun generateKey(alias: String): SecretKey {
      generateCalls++
      return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
        storedKey = it
      }
    }

    override fun deleteKey(alias: String) {
      deleteCalls++
      storedKey = null
    }
  }

  @Test
  fun encryptRecoversOnceWhenExistingKeyIsUnrecoverable() {
    val backend = InMemoryBackend(failLoadOnce = { UnrecoverableKeyException("broken") })
    val protector = AndroidKeystoreApiKeyProtector("test_alias", backend)

    val envelope = protector.encrypt("secret-api-key")

    assertEquals(1, backend.deleteCalls)
    assertEquals(1, backend.generateCalls)
    assertEquals("secret-api-key", protector.decrypt(envelope))
  }

  @Test
  fun encryptRecoversWhenCipherInitRejectsInvalidExistingKey() {
    val invalidKey = object : SecretKey {
      override fun getAlgorithm() = "AES"
      override fun getFormat() = "RAW"
      override fun getEncoded() = ByteArray(1)
    }
    val backend = InMemoryBackend().apply { storedKey = invalidKey }
    val protector = AndroidKeystoreApiKeyProtector("test_alias", backend)

    val envelope = protector.encrypt("secret-api-key")

    assertEquals(1, backend.deleteCalls)
    assertEquals(1, backend.generateCalls)
    assertEquals("secret-api-key", protector.decrypt(envelope))
  }

  @Test
  fun decryptDeletesBrokenKeyEntryAndFailsClosed() {
    val backend = InMemoryBackend(failLoadOnce = { UnrecoverableKeyException("broken") })
    val protector = AndroidKeystoreApiKeyProtector("test_alias", backend)

    assertThrows(UnrecoverableKeyException::class.java) {
      protector.decrypt("v1:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAA")
    }
    assertEquals(1, backend.deleteCalls)
  }

  @Test
  fun decryptPreservesValidKeyOnTamperedCiphertext() {
    val backend = InMemoryBackend()
    val protector = AndroidKeystoreApiKeyProtector("test_alias", backend)
    val envelope = protector.encrypt("secret-api-key")
    val tampered = envelope.dropLast(2) + "AA"

    assertThrows(AEADBadTagException::class.java) {
      protector.decrypt(tampered)
    }
    assertEquals(0, backend.deleteCalls)
  }

  @Test
  fun concurrentEncryptAndDecryptOnSharedProtectorAreThreadSafe() {
    val backend = InMemoryBackend()
    val protector = AndroidKeystoreApiKeyProtector("test_alias", backend)
    val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
    val threads = (0 until 6).map { worker ->
      Thread {
        try {
          repeat(20) { i ->
            val plain = "key-$worker-$i"
            val enc = protector.encrypt(plain)
            assertEquals(plain, protector.decrypt(enc))
          }
        } catch (t: Throwable) {
          errors += t
        }
      }.apply { start() }
    }
    threads.forEach { it.join() }
    assertEquals(emptyList<Throwable>(), errors)
  }
}
