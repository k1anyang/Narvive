package com.narvive.app.service.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 覆盖 FontStatRepair：合成字体逻辑验证 + 真实字体往返（供外部 Chrome 端到端校验）。 */
class FontStatRepairTest {

    @Test
    fun dropsOutOfRangeAxisValueAndFixesChecksum() {
        val font = buildSyntheticFont(bad = true)
        val repaired = FontStatRepair.repairIfNeeded(font)
        assertNotSame("损坏字体应返回新实例", font, repaired)

        val statInfo = parseStat(repaired)
        assertEquals(2, statInfo.axisValueCount)
        assertTrue("所有 AxisIndex 必须 < axisCount", statInfo.axisIndexes.all { it < statInfo.axisCount })

        assertEquals("表目录里 STAT 长度应等于新表大小", statInfo.tableSize, tableLength(repaired, "STAT"))
        assertTrue("整表校验和必须合法", isWholeFontChecksumValid(repaired))
    }

    @Test
    fun healthyFontReturnedUnchanged() {
        val font = buildSyntheticFont(bad = false)
        assertSame("健康字体应原样返回", font, FontStatRepair.repairIfNeeded(font))
    }

    @Test
    fun nonFontBytesReturnedUnchanged() {
        val junk = byteArrayOf(1, 2, 3)
        assertSame(junk, FontStatRepair.repairIfNeeded(junk))
    }

    @Test
    fun realBadFontRepairsAndDumps() {
        val path = System.getenv("FONT_REAL_FILE") ?: return
        val file = File(path)
        if (!file.isFile) return
        val bytes = file.readBytes()
        val repaired = FontStatRepair.repairIfNeeded(bytes)
        assertNotSame("真实坏字体应被修复", bytes, repaired)
        assertTrue(isWholeFontChecksumValid(repaired))
        assertEquals(6, parseStat(repaired).axisValueCount)
        val out = System.getenv("FONT_REAL_OUT")
        val target = if (!out.isNullOrBlank()) File(out) else File.createTempFile("font-fixed", ".ttf")
        target.writeBytes(repaired)
        println("REPAIRED_TO=$target size=${repaired.size}")
    }

    // ───────────────────────── 合成字体构造（与真实布局一致） ─────────────────────────

    private fun buildSyntheticFont(bad: Boolean): ByteArray {
        val head = ByteArray(54) // checkSumAdjustment 在 +8，由修复器回填

        val axes = ByteArray(16)
        writeAscii(axes, 0, "wght"); writeU16(axes, 4, 256); writeU16(axes, 6, 0)
        writeAscii(axes, 8, "BEVL"); writeU16(axes, 12, 257); writeU16(axes, 14, 1)

        val rec0 = format1Record(0, 17, 200)
        val rec1 = format1Record(1, 3, 1)
        val recBad = if (bad) format1Record(2, 4, 0) else null
        val records = if (bad) listOf(rec0, rec1, recBad!!) else listOf(rec0, rec1)

        val headerSize = 20 // v1.1：含 offsetToAxisValueOffsets + elidedFallbackNameID
        val offsetsOffset = headerSize + axes.size // 36
        val offsetsSize = records.size * 2 // uint16
        val recordsStart = ((offsetsOffset + offsetsSize + 3) / 4) * 4
        val statSize = recordsStart + records.sumOf { it.size }
        val stat = ByteArray(statSize)
        writeU16(stat, 0, 1); writeU16(stat, 2, 1)
        writeU16(stat, 4, 8); writeU16(stat, 6, 2)
        writeU32(stat, 8, headerSize.toLong())
        writeU16(stat, 12, records.size)
        writeU32(stat, 14, offsetsOffset.toLong())
        writeU16(stat, 18, 2) // elidedFallbackNameID
        axes.copyInto(stat, headerSize)
        var recStart = recordsStart
        for (i in records.indices) {
            writeU16(stat, offsetsOffset + i * 2, (recStart - offsetsOffset))
            records[i].copyInto(stat, recStart)
            recStart += records[i].size
        }

        val numTables = 2
        val headOffset = 12 + numTables * 16
        val statOffset = headOffset + head.size
        val font = ByteArray(statOffset + stat.size)
        writeU32(font, 0, 0x00010000L)
        writeU16(font, 4, numTables)
        writeAscii(font, 12, "head"); writeU32(font, 20, headOffset.toLong()); writeU32(font, 24, head.size.toLong())
        writeAscii(font, 28, "STAT"); writeU32(font, 36, statOffset.toLong()); writeU32(font, 40, stat.size.toLong())
        head.copyInto(font, headOffset)
        stat.copyInto(font, statOffset)
        return font
    }

    private fun format1Record(axisIndex: Int, valueNameId: Int, value: Int): ByteArray {
        val rec = ByteArray(12)
        writeU16(rec, 0, 1) // format
        writeU16(rec, 2, 0) // flags
        writeU16(rec, 4, axisIndex)
        writeU16(rec, 6, valueNameId)
        writeU32(rec, 8, value.toLong())
        return rec
    }

    // ───────────────────────── 校验用解析 ─────────────────────────

    private class StatInfo(val axisValueCount: Int, val axisCount: Int, val axisIndexes: List<Int>, val tableSize: Int)

    private fun parseStat(font: ByteArray): StatInfo {
        val entry = findEntry(font, "STAT") ?: error("STAT missing")
        val off = u32(font, entry + 8).toInt()
        val len = u32(font, entry + 12).toInt()
        val axisCount = u16(font, off + 6)
        val valueCount = u16(font, off + 12)
        val minor = u16(font, off + 2)
        val offsetsOffset = if (minor >= 1) u32(font, off + 14).toInt() else u16(font, off + 6)
        val indexes = mutableListOf<Int>()
        for (i in 0 until valueCount) {
            val recOff = u16(font, off + offsetsOffset + i * 2)
            val rec = off + offsetsOffset + recOff
            val format = u16(font, rec)
            val ax = when (format) {
                1 -> u16(font, rec + 4)
                2 -> u16(font, rec + 4)
                3 -> u16(font, rec + 4)
                4 -> 0
                else -> error("unknown format $format")
            }
            indexes += ax
        }
        return StatInfo(valueCount, axisCount, indexes, len)
    }

    private fun isWholeFontChecksumValid(font: ByteArray): Boolean {
        val headEntry = findEntry(font, "head") ?: return false
        val headOff = u32(font, headEntry + 8).toInt()
        val adj = u32(font, headOff + 8)
        val copy = font.copyOf()
        writeU32(copy, headOff + 8, 0)
        val sum = checksum(copy)
        return ((sum + adj) and 0xFFFFFFFFL) == 0xB1B0AFBAL
    }

    private fun tableLength(font: ByteArray, tag: String): Int {
        val entry = findEntry(font, tag) ?: error("$tag missing")
        return u32(font, entry + 12).toInt()
    }

    private fun findEntry(b: ByteArray, tag: String): Int? {
        val n = u16(b, 4)
        for (i in 0 until n) {
            val e = 12 + i * 16
            if (e + 16 > b.size) break
            if (String(b, e, 4, Charsets.ISO_8859_1) == tag) return e
        }
        return null
    }

    private fun checksum(b: ByteArray): Long {
        var sum = 0L
        var i = 0
        while (i + 4 <= b.size) {
            sum = (sum + u32(b, i)) and 0xFFFFFFFFL
            i += 4
        }
        if (i < b.size) {
            var last = 0L
            for (j in i until b.size) last = (last shl 8) or (b[j].toLong() and 0xFF)
            sum = (sum + last) and 0xFFFFFFFFL
        }
        return sum
    }

    private fun u16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun writeU16(b: ByteArray, o: Int, v: Int) {
        b[o] = ((v ushr 8) and 0xFF).toByte(); b[o + 1] = (v and 0xFF).toByte()
    }

    private fun writeU32(b: ByteArray, o: Int, v: Long) {
        b[o] = ((v ushr 24) and 0xFF).toByte(); b[o + 1] = ((v ushr 16) and 0xFF).toByte()
        b[o + 2] = ((v ushr 8) and 0xFF).toByte(); b[o + 3] = (v and 0xFF).toByte()
    }

    private fun writeAscii(b: ByteArray, o: Int, s: String) {
        for (i in s.indices) b[o + i] = s[i].code.toByte()
    }
}
