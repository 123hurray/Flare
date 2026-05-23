package dev.dimension.flare.data.network.xiaohongshu

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Clock
import okio.ByteString.Companion.encodeUtf8

internal object XhsSigning {
    private const val APP_ID = "xhs-pc-web"
    private const val SDK_VERSION = "4.3.5"
    private const val PLATFORM = "Mac OS"
    private const val WEB_VERSION = "6.12.1"
    private const val XYS_PREFIX = "XYS_"
    private const val X3_PREFIX = "mns0301_"
    private const val XHS_COMMON_VERSION = 3
    private const val B1_SECRET_KEY = "xhswebmplfbt"
    private const val CUSTOM_BASE64_ALPHABET = "ZmserbBoHQtNP+wOcza/LpngG8yJq42KWYj0DSfdikx3VT16IlUAFM97hECvuRX5"
    private const val X3_BASE64_ALPHABET = "MfgqrsbcyzPQRStuvC7mn501HIJBo2DEFTKdeNOwxWXYZap89+/A4UVLhijkl63G"
    private const val STANDARD_BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val HEX_KEY =
        "71a302257793271ddd273bcee3e4b98d9d7935e1da33f5765e2ea8afb6dc77a51a499d23b67c20660025860cbf13d4540d92497f58686c574e508f46e1956344f39139bf4faf22a3eef120b79258145b2feb5193b6478669961298e79bedca646e1a693a926154a5a7a1bd1cf0dedb742f917a747a1e388b234f2277516db7116035439730fa61e9822a0eca7bff72d8"

    val IS_MAIN_API_SIGNING_VERIFIED: Boolean
        get() = XhsSigningRuntime.isAvailable

    private const val IS_OFFLINE_MAIN_API_SIGNING_VERIFIED = false

    private val versionBytes = intArrayOf(121, 104, 96, 41)
    private val envTable = intArrayOf(115, 248, 83, 102, 103, 201, 181, 131, 99, 94, 4, 68, 250, 132, 21)
    private val envChecksDefault = intArrayOf(0, 1, 18, 1, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0)
    private val a3Prefix = intArrayOf(2, 97, 51, 16)
    private val hashIv = longArrayOf(1831565813L, 461845907L, 2246822507L, 3266489909L)
    private val crc32Table = IntArray(256) { index ->
        var r = index
        repeat(8) {
            r = if ((r and 1) == 1) {
                (r ushr 1) xor 0xEDB88320.toInt()
            } else {
                r ushr 1
            }
        }
        r
    }

    private var pageLoadTimestamp = Clock.System.now().toEpochMilliseconds()
    private var sequenceValue = Random.nextInt(15, 18)
    private var windowPropsLength = Random.nextInt(1000, 2001)

    suspend fun sign(
        method: String,
        path: String,
        body: String,
        cookies: Map<String, String>,
    ): Map<String, String> {
        val a1 = cookies["a1"].orEmpty()
        require(a1.isNotBlank()) { "Xiaohongshu cookie a1 is required for signing" }

        XhsSigningRuntime
            .sign(
                XhsSigningRequest(
                    method = method,
                    path = path,
                    body = body,
                    cookies = cookies,
                ),
            )?.let { return validateRuntimeHeaders(path, it) }

        require(IS_OFFLINE_MAIN_API_SIGNING_VERIFIED) {
            "Xiaohongshu main API signing runtime is not installed"
        }

        val timestampMs = Clock.System.now().toEpochMilliseconds()
        val content = buildContentString(method, path, body)
        val xS = signXS(path = path, content = content, a1 = a1, timestampMs = timestampMs)
        return mapOf(
            "x-s" to xS,
            "x-s-common" to signXSCommon(cookies),
            "x-t" to timestampMs.toString(),
            "x-b3-traceid" to randomHex(16),
            "x-xray-traceid" to xrayTraceId(timestampMs),
        )
    }

    private fun validateRuntimeHeaders(
        path: String,
        headers: Map<String, String>,
    ): Map<String, String> {
        require(headers["x-s"].orEmpty().startsWith(XYS_PREFIX)) {
            "Xiaohongshu runtime signer returned invalid x-s for $path"
        }
        require(headers["x-s-common"].orEmpty().isNotBlank()) {
            "Xiaohongshu runtime signer returned empty x-s-common for $path"
        }
        require(headers["x-t"].orEmpty().all { it.isDigit() }) {
            "Xiaohongshu runtime signer returned invalid x-t for $path"
        }
        return headers
    }

    private fun buildContentString(
        method: String,
        path: String,
        body: String,
    ): String =
        if (method.uppercase() == "POST") {
            path + body
        } else {
            path
        }

    private fun signXS(
        path: String,
        content: String,
        a1: String,
        timestampMs: Long,
    ): String {
        val dValue = content.encodeUtf8().md5().hex()
        val payload = buildPayloadArray(dValue, a1, content, timestampMs, nextSignState(path))
        val x3 = encodeX3(xorTransform(payload).take(144).toByteArray())
        val signatureData =
            """{"x0":"$SDK_VERSION","x1":"$APP_ID","x2":"$PLATFORM","x3":"$X3_PREFIX$x3","x4":""}"""
        return XYS_PREFIX + encodeCustomBase64(signatureData.encodeToByteArray())
    }

    private fun nextSignState(path: String): SignState {
        sequenceValue += Random.nextInt(0, 2)
        windowPropsLength += Random.nextInt(1, 11)
        return SignState(
            pageLoadTimestamp = pageLoadTimestamp,
            sequenceValue = sequenceValue,
            windowPropsLength = windowPropsLength,
            uriLength = path.length,
        )
    }

    private fun buildPayloadArray(
        md5Hex: String,
        a1: String,
        content: String,
        timestampMs: Long,
        signState: SignState,
    ): IntArray {
        val seed = Random.nextLong(0L, 0x1_0000_0000L)
        val seedByte = (seed and 0xff).toInt()
        val tsBytes = intToLeBytes(timestampMs, 8)
        val output = ArrayList<Int>(144)
        output.addAll(versionBytes.toList())
        output.addAll(intToLeBytes(seed, 4).toList())
        output.addAll(tsBytes.toList())
        output.addAll(intToLeBytes(signState.pageLoadTimestamp, 8).toList())
        output.addAll(intToLeBytes(signState.sequenceValue.toLong(), 4).toList())
        output.addAll(intToLeBytes(signState.windowPropsLength.toLong(), 4).toList())
        output.addAll(intToLeBytes(signState.uriLength.toLong(), 4).toList())

        val md5Bytes = md5Hex.hexToBytes()
        repeat(8) { index -> output += md5Bytes[index].toInt() xor seedByte }

        val a1Bytes = a1.encodeToByteArray().copyOfPadded(52)
        output += a1Bytes.size
        a1Bytes.forEach { output += it.toInt() and 0xff }

        val appBytes = APP_ID.encodeToByteArray().copyOfPadded(10)
        output += appBytes.size
        appBytes.forEach { output += it.toInt() and 0xff }

        output += 1
        output += seedByte xor envTable[0]
        for (index in 1 until 15) {
            output += envTable[index] xor envChecksDefault[index]
        }

        val apiPath = content.substringBefore("{").substringBefore("?")
        val apiPathMd5Bytes = apiPath.encodeUtf8().md5().hex().hexToBytes().map { it.toInt() and 0xff }
        output.addAll(a3Prefix.toList())
        customHashV2(tsBytes.toList() + apiPathMd5Bytes).forEach { output += it xor seedByte }

        return output.toIntArray()
    }

    private fun customHashV2(inputBytes: List<Int>): List<Int> {
        var s0 = hashIv[0]
        var s1 = hashIv[1]
        var s2 = hashIv[2]
        var s3 = hashIv[3]
        val length = inputBytes.size.toLong()
        s0 = (s0 xor length).u32()
        s1 = (s1 xor (length shl 8)).u32()
        s2 = (s2 xor (length shl 16)).u32()
        s3 = (s3 xor (length shl 24)).u32()

        for (index in 0 until inputBytes.size / 8) {
            val offset = index * 8
            val v0 = leBytesToU32(inputBytes, offset)
            val v1 = leBytesToU32(inputBytes, offset + 4)
            s0 = rotateLeft(((s0 + v0).u32() xor s2).u32(), 7)
            s1 = rotateLeft(((v0 xor s1) + s3).u32(), 11)
            s2 = rotateLeft(((s2 + v1).u32() xor s0).u32(), 13)
            s3 = rotateLeft(((s3 xor v1) + s1).u32(), 17)
        }

        val t0 = (s0 xor length).u32()
        val t1 = (s1 xor t0).u32()
        val t2 = (s2 + t1).u32()
        val t3 = (s3 xor t2).u32()
        val rotT0 = rotateLeft(t0, 9)
        val rotT1 = rotateLeft(t1, 13)
        val rotT2 = rotateLeft(t2, 17)
        val rotT3 = rotateLeft(t3, 19)

        s0 = (rotT0 + rotT2).u32()
        s1 = (rotT1 xor rotT3).u32()
        s2 = (rotT2 + s0).u32()
        s3 = (rotT3 xor s1).u32()

        return listOf(s0, s1, s2, s3).flatMap { intToLeBytes(it, 4).toList() }
    }

    private fun signXSCommon(cookies: Map<String, String>): String {
        val a1 = requireNotNull(cookies["a1"]?.takeIf { it.isNotBlank() }) {
            "Xiaohongshu cookie a1 is required for x-s-common"
        }
        val b1 = generateB1()
        val x9 = crc32JsInt(b1)
        val x12 = "${cookies["dsllt"].orEmpty()};${cookies["_dsl"].orEmpty()}"
        val signJson =
            """{"s0":$XHS_COMMON_VERSION,"s1":"","x0":"1","x1":"$SDK_VERSION","x2":"$PLATFORM","x3":"$APP_ID","x4":"$WEB_VERSION","x5":"$a1","x6":"","x7":"","x8":"$b1","x9":$x9,"x10":0,"x11":"normal","x12":"$x12"}"""
        return encodeCustomBase64(signJson.encodeToByteArray())
    }

    private fun generateB1(): String {
        val fingerprintJson = buildB1FingerprintJson()
        val encrypted = rc4(B1_SECRET_KEY.encodeToByteArray(), fingerprintJson.encodeToByteArray())
        val encodedUrl = quoteLatin1AsUtf8(encrypted)
        val bytes = ArrayList<Int>()
        encodedUrl.split("%").drop(1).forEach { part ->
            if (part.length >= 2) {
                bytes += part.take(2).toInt(16)
                part.drop(2).forEach { bytes += it.code and 0xff }
            }
        }
        return encodeCustomBase64(bytes.map { it.toByte() }.toByteArray())
    }

    private fun buildB1FingerprintJson(): String {
        val now = Clock.System.now().toEpochMilliseconds().toString()
        return buildString {
            append("""{"x33":"0","x34":"0","x35":"0","x36":"${Random.nextInt(1, 21)}",""")
            append(""""x37":"0|0|0|0|0|0|0|0|0|1|0|0|0|0|0|0|0|0|1|0|0|0|0|0",""")
            append(""""x38":"0|0|1|0|1|0|0|0|0|0|1|0|1|0|1|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0|0",""")
            append(""""x39":0,"x42":"3.4.4","x43":"742cc32c","x44":"$now",""")
            append(""""x45":"__SEC_CAV__1-1-1-1-1|__SEC_WSA__|","x46":"false","x48":"",""")
            append(""""x49":"{list:[],type:}","x50":"","x51":"","x52":"",""")
            append(""""x82":"_0x17a2|_0x1954"}""")
        }
    }

    private fun rc4(
        key: ByteArray,
        input: ByteArray,
    ): ByteArray {
        val state = IntArray(256) { it }
        var j = 0
        for (i in 0..255) {
            j = (j + state[i] + (key[i % key.size].toInt() and 0xff)) and 0xff
            state.swap(i, j)
        }
        val output = ByteArray(input.size)
        var i = 0
        j = 0
        input.indices.forEach { index ->
            i = (i + 1) and 0xff
            j = (j + state[i]) and 0xff
            state.swap(i, j)
            val k = state[(state[i] + state[j]) and 0xff]
            output[index] = ((input[index].toInt() and 0xff) xor k).toByte()
        }
        return output
    }

    private fun quoteLatin1AsUtf8(bytes: ByteArray): String =
        buildString {
            bytes.forEach { byte ->
                val code = byte.toInt() and 0xff
                when {
                    code in 'A'.code..'Z'.code ||
                        code in 'a'.code..'z'.code ||
                        code in '0'.code..'9'.code ||
                        code.toChar() in setOf('_', '.', '-', '~', '!', '*', '\'', '(', ')') -> append(code.toChar())
                    code < 0x80 -> appendPercent(code)
                    else -> code.toChar().toString().encodeToByteArray().forEach { appendPercent(it.toInt() and 0xff) }
                }
            }
        }

    private fun StringBuilder.appendPercent(value: Int) {
        append('%')
        append(value.toString(16).uppercase().padStart(2, '0'))
    }

    private fun crc32JsInt(data: String): Int {
        var c = -1
        data.forEach { char ->
            c = crc32Table[((c and 0xff) xor (char.code and 0xff)) and 0xff] xor (c ushr 8)
        }
        val unsigned = ((-1 xor c) xor 0xEDB88320.toInt())
        return unsigned
    }

    private fun xorTransform(source: IntArray): ByteArray {
        val key = HEX_KEY.hexToBytes()
        return ByteArray(source.size) { index ->
            val value = if (index < key.size) source[index] xor (key[index].toInt() and 0xff) else source[index]
            (value and 0xff).toByte()
        }
    }

    private fun intToLeBytes(
        value: Long,
        length: Int,
    ): IntArray {
        var remaining = value
        return IntArray(length) {
            val byte = (remaining and 0xff).toInt()
            remaining = remaining shr 8
            byte
        }
    }

    private fun leBytesToU32(
        bytes: List<Int>,
        offset: Int,
    ): Long =
        ((bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)).u32()

    private fun rotateLeft(
        value: Long,
        bits: Int,
    ): Long = (((value shl bits) or (value ushr (32 - bits))) and 0xffff_ffffL)

    private fun Long.u32(): Long = this and 0xffff_ffffL

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeCustomBase64(bytes: ByteArray): String = Base64.encode(bytes).translateBase64(CUSTOM_BASE64_ALPHABET)

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeX3(bytes: ByteArray): String = Base64.encode(bytes).translateBase64(X3_BASE64_ALPHABET)

    private fun String.translateBase64(alphabet: String): String =
        map { char ->
            val index = STANDARD_BASE64_ALPHABET.indexOf(char)
            if (index >= 0) alphabet[index] else char
        }.joinToString("")

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.copyOfPadded(size: Int): ByteArray {
        val result = ByteArray(size)
        copyInto(result, endIndex = min(this.size, size))
        return result
    }

    private fun IntArray.swap(
        first: Int,
        second: Int,
    ) {
        val tmp = this[first]
        this[first] = this[second]
        this[second] = tmp
    }

    private fun randomHex(length: Int): String =
        buildString(length) {
            repeat(length) {
                append("abcdef0123456789"[Random.nextInt(16)])
            }
        }

    private fun xrayTraceId(timestampMs: Long): String {
        val seq = Random.nextInt(0, 8_388_608).toULong()
        val part1 = ((timestampMs.toULong() shl 23) or seq).toString(16).padStart(16, '0')
        return part1 + randomHex(16)
    }

    private data class SignState(
        val pageLoadTimestamp: Long,
        val sequenceValue: Int,
        val windowPropsLength: Int,
        val uriLength: Int,
    )
}
