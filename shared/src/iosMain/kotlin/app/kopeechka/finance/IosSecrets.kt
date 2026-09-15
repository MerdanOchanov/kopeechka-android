package app.kopeechka.finance

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Ключи ИИ-провайдеров в Keychain — это iOS-аналог Android Keystore.
 * Доступ после первой разблокировки: фоновая копия должна работать и при
 * заблокированном экране, но ключи при этом не лежат в открытом виде.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecrets(private val service: String = "app.kopeechka.finance") : SecretStore {

    override fun get(name: String): String = memScoped {
        val found = alloc<CFTypeRefVar>()
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to name,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val status = SecItemCopyMatching(query, found.ptr)
        if (status != errSecSuccess) return@memScoped ""
        val data = CFBridgingRelease(found.value) as? NSData ?: return@memScoped ""
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as String? ?: ""
    }

    override fun put(name: String, value: String) {
        // в Keychain нет перезаписи: сначала убираем прежнюю запись
        SecItemDelete(
            cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to name,
            ),
        )
        if (value.isEmpty()) return
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        SecItemAdd(
            cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to name,
                kSecValueData to data,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
            ),
            null,
        )
    }

    /** Словарь запроса к Keychain: Kotlin-карта мостится в CFDictionary. */
    private fun cfDictionaryOf(vararg pairs: Pair<Any?, Any?>): CFDictionaryRef? =
        CFBridgingRetain(pairs.toMap() as NSDictionary) as CFDictionaryRef?
}
