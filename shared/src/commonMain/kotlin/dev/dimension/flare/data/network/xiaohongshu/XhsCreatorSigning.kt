package dev.dimension.flare.data.network.xiaohongshu

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import okio.ByteString.Companion.encodeUtf8

@OptIn(DelicateCryptographyApi::class, ExperimentalEncodingApi::class)
internal object XhsCreatorSigning {
    private const val PREFIX = "XYW_"
    private const val AES_KEY = "7cc4adla5ay0701v"
    private const val AES_IV = "4uzjr7mbsibcaldp"
    private const val X2 = "0|0|0|1|0|0|1|0|0|0|1|0|0|0|0|1|0|0|0"

    fun sign(
        path: String,
        body: String,
        cookies: Map<String, String>,
    ): Map<String, String> {
        val a1 = cookies["a1"].orEmpty()
        require(a1.isNotBlank()) { "Xiaohongshu cookie a1 is required for creator signing" }

        val timestampMs = Clock.System.now().toEpochMilliseconds()
        val contentMd5 = "url=$path$body".encodeUtf8().md5().hex()
        val plaintext = "x1=$contentMd5;x2=$X2;x3=$a1;x4=$timestampMs;"
        val payload =
            aesCbcPkcs7Encrypt(
                data = Base64.encode(plaintext.encodeToByteArray()).encodeToByteArray(),
                key = AES_KEY.encodeToByteArray(),
                iv = AES_IV.encodeToByteArray(),
            ).toHex()
        val envelope =
            """{"signSvn":"56","signType":"x2","appId":"ugc","signVersion":"1","payload":"$payload"}"""
        return mapOf(
            "x-s" to PREFIX + Base64.encode(envelope.encodeToByteArray()),
            "x-t" to timestampMs.toString(),
        )
    }

    private fun aesCbcPkcs7Encrypt(
        data: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val aes = CryptographyProvider.Default.get(AES.CBC)
        val decodedKey = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        return decodedKey.cipher().encryptWithIvBlocking(iv, data)
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
}
