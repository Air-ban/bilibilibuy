package com.hg.bilibilibuy

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

object LocalQrCode {
    private val ECC_CODEWORDS_PER_BLOCK = intArrayOf(
        -1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28,
        30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30,
        30, 30, 30, 30, 30, 30
    )

    private val NUM_ERROR_CORRECTION_BLOCKS = intArrayOf(
        -1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8,
        9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25
    )

    fun render(text: String, pixelSize: Int = 720): Bitmap {
        val data = text.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        val version = chooseVersion(data.size)
        val qr = Qr(version)
        val dataCodewords = addEccAndInterleave(makeDataCodewords(data, version), version)
        qr.drawCodewords(dataCodewords)
        return qr.toBitmap(pixelSize)
    }

    private fun chooseVersion(byteLength: Int): Int {
        for (version in 1..40) {
            val capacityBits = getNumDataCodewords(version) * 8
            val countBits = if (version <= 9) 8 else 16
            val usedBits = 4 + countBits + byteLength * 8
            if (usedBits <= capacityBits) return version
        }
        throw IllegalArgumentException("QR content is too long")
    }

    private fun makeDataCodewords(data: List<Int>, version: Int): IntArray {
        val capacityBits = getNumDataCodewords(version) * 8
        val bits = mutableListOf<Int>()
        appendBits(bits, 0x4, 4)
        appendBits(bits, data.size, if (version <= 9) 8 else 16)
        for (b in data) appendBits(bits, b, 8)
        val terminator = min(4, capacityBits - bits.size)
        appendBits(bits, 0, terminator)
        while (bits.size % 8 != 0) bits.add(0)
        val result = mutableListOf<Int>()
        for (i in bits.indices step 8) {
            var value = 0
            for (j in 0 until 8) value = (value shl 1) or bits[i + j]
            result.add(value)
        }
        var pad = 0xEC
        while (result.size < getNumDataCodewords(version)) {
            result.add(pad)
            pad = pad xor 0xEC xor 0x11
        }
        return result.toIntArray()
    }

    private fun appendBits(bits: MutableList<Int>, value: Int, length: Int) {
        for (i in length - 1 downTo 0) bits.add((value ushr i) and 1)
    }

    private fun addEccAndInterleave(data: IntArray, version: Int): IntArray {
        val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[version]
        val blockEccLen = ECC_CODEWORDS_PER_BLOCK[version]
        val rawCodewords = getNumRawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockDataLen = rawCodewords / numBlocks - blockEccLen
        val rsDiv = reedSolomonComputeDivisor(blockEccLen)
        val blocks = mutableListOf<IntArray>()
        var offset = 0
        for (i in 0 until numBlocks) {
            val dataLen = shortBlockDataLen + if (i < numShortBlocks) 0 else 1
            val dat = data.copyOfRange(offset, offset + dataLen)
            offset += dataLen
            val ecc = reedSolomonComputeRemainder(dat, rsDiv)
            val block = IntArray(dataLen + blockEccLen + if (i < numShortBlocks) 1 else 0)
            dat.copyInto(block, 0)
            ecc.copyInto(block, block.size - blockEccLen)
            blocks.add(block)
        }
        val result = mutableListOf<Int>()
        val maxLen = blocks.maxOf { it.size }
        for (i in 0 until maxLen) {
            for (block in blocks) {
                val isPadding = i == shortBlockDataLen && block.size == shortBlockDataLen + blockEccLen + 1
                if (i < block.size && !isPadding) result.add(block[i])
            }
        }
        return result.toIntArray()
    }

    private fun reedSolomonComputeDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in result.indices) {
                result[j] = reedSolomonMultiply(result[j], root)
                if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
            }
            root = reedSolomonMultiply(root, 2)
        }
        return result
    }

    private fun reedSolomonComputeRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            for (i in 0 until result.size - 1) {
                result[i] = result[i + 1] xor reedSolomonMultiply(divisor[i], factor)
            }
            result[result.size - 1] = reedSolomonMultiply(divisor.last(), factor)
        }
        return result
    }

    private fun reedSolomonMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun getNumDataCodewords(version: Int): Int {
        return getNumRawDataModules(version) / 8 -
            ECC_CODEWORDS_PER_BLOCK[version] * NUM_ERROR_CORRECTION_BLOCKS[version]
    }

    private fun getNumRawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val numAlign = version / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private class Qr(private val version: Int) {
        private val size = version * 4 + 17
        private val modules = Array(size) { BooleanArray(size) }
        private val isFunction = Array(size) { BooleanArray(size) }

        init {
            drawFunctionPatterns()
        }

        fun drawCodewords(data: IntArray) {
            var bitIndex = 0
            var upward = true
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right--
                for (vert in 0 until size) {
                    val y = if (upward) size - 1 - vert else vert
                    for (x in right downTo right - 1) {
                        if (!isFunction[y][x]) {
                            var bit = false
                            if (bitIndex < data.size * 8) {
                                bit = ((data[bitIndex ushr 3] ushr (7 - (bitIndex and 7))) and 1) != 0
                                bitIndex++
                            }
                            if ((x + y) % 2 == 0) bit = !bit
                            modules[y][x] = bit
                        }
                    }
                }
                upward = !upward
                right -= 2
            }
            drawFormatBits(0)
        }

        fun toBitmap(pixelSize: Int): Bitmap {
            val border = 4
            val scale = max(1, pixelSize / (size + border * 2))
            val actualSize = (size + border * 2) * scale
            return Bitmap.createBitmap(actualSize, actualSize, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        if (modules[y][x]) {
                            val left = (x + border) * scale
                            val top = (y + border) * scale
                            for (dy in 0 until scale) {
                                for (dx in 0 until scale) setPixel(left + dx, top + dy, Color.BLACK)
                            }
                        }
                    }
                }
            }
        }

        private fun drawFunctionPatterns() {
            drawFinderPattern(3, 3)
            drawFinderPattern(size - 4, 3)
            drawFinderPattern(3, size - 4)
            for (i in 0 until size) {
                setFunctionModule(6, i, i % 2 == 0)
                setFunctionModule(i, 6, i % 2 == 0)
            }
            val align = alignmentPatternPositions()
            for (y in align) {
                for (x in align) {
                    if (!((x == 6 && y == 6) || (x == 6 && y == size - 7) || (x == size - 7 && y == 6))) {
                        drawAlignmentPattern(x, y)
                    }
                }
            }
            drawFormatBits(0)
            drawVersion()
        }

        private fun drawFinderPattern(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until size && y in 0 until size) {
                        val dist = max(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        setFunctionModule(x, y, dist != 2 && dist != 4)
                    }
                }
            }
        }

        private fun drawAlignmentPattern(cx: Int, cy: Int) {
            for (dy in -2..2) {
                for (dx in -2..2) {
                    setFunctionModule(cx + dx, cy + dy, max(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
                }
            }
        }

        private fun alignmentPatternPositions(): IntArray {
            if (version == 1) return intArrayOf()
            val numAlign = version / 7 + 2
            val step = if (version == 32) 26 else ((version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2)) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            for (i in numAlign - 1 downTo 1) result[i] = size - 7 - (numAlign - 1 - i) * step
            return result
        }

        private fun drawFormatBits(mask: Int) {
            val data = (1 shl 3) or mask
            var rem = data
            for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            val bits = ((data shl 10) or rem) xor 0x5412
            for (i in 0..14) {
                val bit = ((bits ushr i) and 1) != 0
                if (i < 6) setFunctionModule(8, i, bit)
                else if (i < 8) setFunctionModule(8, i + 1, bit)
                else setFunctionModule(8, size - 15 + i, bit)
                if (i < 8) setFunctionModule(size - 1 - i, 8, bit)
                else if (i < 9) setFunctionModule(15 - i, 8, bit)
                else setFunctionModule(14 - i, 8, bit)
            }
            setFunctionModule(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val bit = ((bits ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val b = i / 3
                setFunctionModule(a, b, bit)
                setFunctionModule(b, a, bit)
            }
        }

        private fun setFunctionModule(x: Int, y: Int, black: Boolean) {
            modules[y][x] = black
            isFunction[y][x] = true
        }
    }
}
