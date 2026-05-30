package dev.dimension.flare.data.network.zhihu

import kotlin.random.Random
import okio.ByteString.Companion.encodeUtf8

// Ported from RSSHub's maintained Zhihu x-zse-96 v3 implementation:
// https://github.com/DIYgod/RSSHub/blob/master/lib/routes/zhihu/execlib/x-zse-96-v3.ts
internal object ZhihuZse96 {
    private const val SALT = "6fpLRqJO8M/c3jnYxFkUVC4ZIG12SiH=5v0mXDazWBTsuw7QetbKdoPyAl+hN9rgE"
    private val zk =
        intArrayOf(
            1_170_614_578, 1_024_848_638, 1_413_669_199, -343_334_464, -766_094_290,
            -1_373_058_082, -143_119_608, -297_228_157, 1_933_479_194, -971_186_181,
            -406_453_910, 460_404_854, -547_427_574, -1_891_326_262, -1_679_095_901,
            2_119_585_428, -2_029_270_069, 2_035_090_028, -1_521_520_070, -5_587_175,
            -77_751_101, -2_094_365_853, -1_243_052_806, 1_579_901_135, 1_321_810_770,
            456_816_404, -1_391_643_889, -229_302_305, 330_002_838, -788_960_546,
            363_569_021, -1_947_871_109,
        )
    private val zb =
        intArrayOf(
            20, 223, 245, 7, 248, 2, 194, 209, 87, 6, 227, 253, 240, 128, 222, 91,
            237, 9, 125, 157, 230, 93, 252, 205, 90, 79, 144, 199, 159, 197, 186,
            167, 39, 37, 156, 198, 38, 42, 43, 168, 217, 153, 15, 103, 80, 189, 71,
            191, 97, 84, 247, 95, 36, 69, 14, 35, 12, 171, 28, 114, 178, 148, 86,
            182, 32, 83, 158, 109, 22, 255, 94, 238, 151, 85, 77, 124, 254, 18, 4,
            26, 123, 176, 232, 193, 131, 172, 143, 142, 150, 30, 10, 146, 162, 62,
            224, 218, 196, 229, 1, 192, 213, 27, 110, 56, 231, 180, 138, 107, 242,
            187, 54, 120, 19, 44, 117, 228, 215, 203, 53, 239, 251, 127, 81, 11,
            133, 96, 204, 132, 41, 115, 73, 55, 249, 147, 102, 48, 122, 145, 106,
            118, 74, 190, 29, 16, 174, 5, 177, 129, 63, 113, 99, 31, 161, 76, 246,
            34, 211, 13, 60, 68, 207, 160, 65, 111, 82, 165, 67, 169, 225, 57, 112,
            244, 155, 51, 236, 200, 233, 58, 61, 47, 100, 137, 185, 64, 17, 70, 234,
            163, 219, 108, 170, 166, 59, 149, 52, 105, 24, 212, 78, 173, 45, 0, 116,
            226, 119, 136, 206, 135, 175, 195, 25, 92, 121, 208, 126, 139, 3, 75,
            141, 21, 130, 98, 241, 40, 154, 66, 184, 49, 181, 46, 243, 88, 101, 183,
            8, 23, 72, 188, 104, 179, 210, 134, 250, 201, 164, 89, 216, 202, 220,
            50, 221, 152, 140, 33, 235, 214,
        )

    fun sign(apiPath: String, dC0: String): String {
        val xZse93 = ZHIHU_X_ZSE_93
        val md5 = "$xZse93+$apiPath+$dC0".encodeUtf8().md5().hex()
        return "2.0_${encrypt(md5)}"
    }

    private fun encrypt(md5: String): String {
        val processed = preProcess(md5)
        var current = 0
        val result = StringBuilder()
        processed.indices.forEach { i ->
            val pop = processed[processed.size - i - 1]
            val iMod4 = i % 4
            val iMod3 = i % 3
            val c = (58 ushr (8 * iMod4)) and 255
            current = current or ((pop xor c) shl (8 * iMod3))
            if (iMod3 == 2) {
                result.append(encode(current))
                current = 0
            }
        }
        return result.toString()
    }

    private fun preProcess(md5: String): IntArray {
        val md5Chars = mutableListOf<Int>()
        md5.forEach { md5Chars.add(it.code) }
        md5Chars.add(0, 0)
        md5Chars.add(0, Random.nextInt(127))
        repeat(15) { md5Chars.add(14) }

        val fixArr = intArrayOf(48, 53, 57, 48, 53, 51, 102, 55, 100, 49, 53, 101, 48, 49, 100, 55)
        val front =
            IntArray(16) { index ->
                md5Chars[index] xor fixArr[index] xor 42
            }
        val gR = r(front)
        val back = md5Chars.subList(16, 48).toIntArray()
        return gR + x(back, gR)
    }

    private fun x(
        input: IntArray,
        init: IntArray,
    ): IntArray {
        var t = init
        val result = mutableListOf<Int>()
        var offset = 0
        while (offset < input.size) {
            val block = IntArray(16)
            for (index in 0 until 16) {
                block[index] = input[offset + index] xor t[index]
            }
            t = r(block)
            result.addAll(t.toList())
            offset += 16
        }
        return result.toIntArray()
    }

    private fun r(input: IntArray): IntArray {
        val output = IntArray(16)
        val n = IntArray(36)
        n[0] = b(input, 0)
        n[1] = b(input, 4)
        n[2] = b(input, 8)
        n[3] = b(input, 12)
        for (index in 0 until 32) {
            n[index + 4] = n[index] xor g(n[index + 1] xor n[index + 2] xor n[index + 3] xor zk[index])
        }
        writeInt(n[35], output, 0)
        writeInt(n[34], output, 4)
        writeInt(n[33], output, 8)
        writeInt(n[32], output, 12)
        return output
    }

    private fun g(value: Int): Int {
        val bytes = IntArray(4)
        writeInt(value, bytes, 0)
        val mapped =
            intArrayOf(
                zb[bytes[0] and 255],
                zb[bytes[1] and 255],
                zb[bytes[2] and 255],
                zb[bytes[3] and 255],
            )
        val r = b(mapped, 0)
        return r xor rotateLeft(r, 2) xor rotateLeft(r, 10) xor rotateLeft(r, 18) xor rotateLeft(r, 24)
    }

    private fun encode(param: Int): String =
        buildString {
            intArrayOf(0, 6, 12, 18).forEach { offset ->
                append(SALT[(param ushr offset) and 63])
            }
        }

    private fun writeInt(
        value: Int,
        output: IntArray,
        offset: Int,
    ) {
        output[offset] = (value ushr 24) and 255
        output[offset + 1] = (value ushr 16) and 255
        output[offset + 2] = (value ushr 8) and 255
        output[offset + 3] = value and 255
    }

    private fun b(
        input: IntArray,
        offset: Int,
    ): Int =
        ((input[offset] and 255) shl 24) or
            ((input[offset + 1] and 255) shl 16) or
            ((input[offset + 2] and 255) shl 8) or
            (input[offset + 3] and 255)

    private fun rotateLeft(
        value: Int,
        bitCount: Int,
    ): Int = (value shl bitCount) or (value ushr (32 - bitCount))
}

internal const val ZHIHU_X_ZSE_93: String = "101_3_3.0"
