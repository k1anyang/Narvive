package com.narvive.app.service.font

/**
 * 轻量 sfnt / STAT 修复器（纯 JVM，无 Android 依赖，可单测）。
 *
 * 背景：Chromium/WebView 加载网络字体时经 OTS 严格校验；若 STAT 表存在
 * `AxisIndex >= DesignAxisCount` 的 AxisValue 记录（违反 OpenType 规范），
 * 整份字体被拒绝，导致 EPUB 的 `@font-face` 进入 error 状态并回退系统字体
 * （TXT 走 Android 原生 Typeface，不校验 STAT，因此磁盘文件无需改动）。
 *
 * STAT 布局要点（OpenType 规范）：
 * - 头部含 major/minor、designAxisSize/Count、designAxesOffset(Offset32)、
 *   axisValueCount、offsetToAxisValueOffsets(Offset32, v1.1+)、
 *   elidedFallbackNameID(v1.1+，因此头部通常 20 字节)；
 * - AxisValueArray 的 axisValueOffsets 为 **uint16** 数组，偏移基准是
 *   AxisValueArray 起点（即 offsetToAxisValueOffsets 处）。
 *
 * 本修复器在内存中剔除越界 AxisValue 记录，重算 STAT 表校验和与
 * head.checkSumAdjustment，返回 OTS 可接受的字节副本；无需修复或无法安全
 * 解析时原样返回输入数组（同一实例）。
 */
object FontStatRepair {

    private const val MAGIC_HEAD_CHECKSUM: Long = 0xB1B0AFBAL

    /** STAT 表定位/解析结果。 */
    private class StatTable(
        val tableOffset: Int,
        val length: Int,
        val axisCount: Int,
        val axisSize: Int,
        val axesOffset: Int,
        val minorVersion: Int,
        val headerRaw: ByteArray,
        val axisRaw: ByteArray,
        val records: List<AxisValueRecord>,
    )

    /** 单条 AxisValue 记录：keep=false 表示应被剔除。 */
    private class AxisValueRecord(
        val keep: Boolean,
        val raw: ByteArray,
    )

    /**
     * 返回修复后的字节副本；无需修复或解析失败时返回原数组（同一实例）。
     */
    fun repairIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size < 12) return bytes
        val stat = try {
            StatTableParser.find(bytes)
        } catch (_: Exception) {
            return bytes
        } ?: return bytes
        val kept = stat.records.filter { it.keep }
        if (kept.size == stat.records.size) return bytes
        return try {
            rebuild(bytes, stat, kept)
        } catch (_: Exception) {
            bytes
        }
    }

    // ───────────────────────── 解析 ─────────────────────────

    private object StatTableParser {
        fun find(bytes: ByteArray): StatTable? {
            val entry = findEntry(bytes, "STAT") ?: return null
            val tableOffset = u32(bytes, entry + 8).toInt()
            val length = u32(bytes, entry + 12).toInt()
            if (tableOffset < 0 || length < 18 || tableOffset + length > bytes.size) {
                throw IllegalArgumentException("bad STAT bounds")
            }
            val stat = tableOffset
            val minor = u16(bytes, stat + 2)
            val axisSize = u16(bytes, stat + 4)
            val axisCount = u16(bytes, stat + 6)
            val axesOffset = u32(bytes, stat + 8).toInt()
            val valueCount = u16(bytes, stat + 12)
            // v1.1+ 头部含 offsetToAxisValueOffsets(Offset32)；v1.0 时数组紧随设计轴
            val offsetsOffset = if (minor >= 1) {
                val v = u32(bytes, stat + 14).toInt()
                if (v == 0) axesOffset + axisCount * axisSize else v
            } else {
                axesOffset + axisCount * axisSize
            }
            if (axisCount <= 0 || axisSize < 8) throw IllegalArgumentException("bad STAT axes")
            if (axesOffset < 18 || axesOffset > length) throw IllegalArgumentException("bad STAT axesOffset")
            val axesEnd = stat + axesOffset + axisCount * axisSize
            if (axesEnd > stat + length || axesEnd > bytes.size) {
                throw IllegalArgumentException("bad STAT axes bounds")
            }
            val headerRaw = bytes.copyOfRange(stat, stat + axesOffset)
            val axisRaw = bytes.copyOfRange(stat + axesOffset, axesEnd)
            val records = ArrayList<AxisValueRecord>(valueCount)
            for (i in 0 until valueCount) {
                val offPtr = stat + offsetsOffset + i * 2 // axisValueOffsets 是 uint16
                if (offPtr + 2 > bytes.size) throw IllegalArgumentException("bad STAT offsets")
                val recOff = u16(bytes, offPtr)
                val recAbs = stat + offsetsOffset + recOff
                if (recAbs + 4 > bytes.size) throw IllegalArgumentException("bad STAT record")
                val format = u16(bytes, recAbs)
                val (size, keep) = when (format) {
                    1 -> 12 to (u16(bytes, recAbs + 4) < axisCount)
                    2 -> 20 to (u16(bytes, recAbs + 4) < axisCount)
                    3 -> 16 to (u16(bytes, recAbs + 4) < axisCount)
                    4 -> {
                        val inner = u16(bytes, recAbs + 4)
                        val sz = 4 + 6 * inner
                        var allValid = true
                        for (j in 0 until inner) {
                            if (u16(bytes, recAbs + 6 + j * 6) >= axisCount) {
                                allValid = false
                                break
                            }
                        }
                        sz to allValid
                    }
                    else -> throw IllegalArgumentException("unknown STAT format $format")
                }
                if (recAbs + size > bytes.size) throw IllegalArgumentException("bad STAT record size")
                records.add(AxisValueRecord(keep, bytes.copyOfRange(recAbs, recAbs + size)))
            }
            return StatTable(
                tableOffset = tableOffset,
                length = length,
                axisCount = axisCount,
                axisSize = axisSize,
                axesOffset = axesOffset,
                minorVersion = minor,
                headerRaw = headerRaw,
                axisRaw = axisRaw,
                records = records,
            )
        }
    }

    // ───────────────────────── 重建 ─────────────────────────

    private fun rebuild(bytes: ByteArray, stat: StatTable, kept: List<AxisValueRecord>): ByteArray {
        val headerSize = stat.axesOffset // 保留原头部（含 elidedFallbackNameID 等）
        val axesSize = stat.axisCount * stat.axisSize
        val offsetsOffset = headerSize + axesSize
        val offsetsSize = kept.size * 2 // uint16
        val recordsSize = kept.sumOf { it.raw.size }
        // 记录区按 4 字节对齐（与常见工具一致，避免 OTS 边界问题）
        val recordsStart = ((offsetsOffset + offsetsSize + 3) ushr 2) shl 2
        val newStatLen = recordsStart + recordsSize
        if (newStatLen > stat.length) throw IllegalArgumentException("rebuilt STAT larger than original")

        val newStat = ByteArray(newStatLen)
        stat.headerRaw.copyInto(newStat, 0)
        writeU16(newStat, 12, kept.size)            // axisValueCount
        if (stat.minorVersion >= 1) {
            writeU32(newStat, 14, offsetsOffset.toLong()) // offsetToAxisValueOffsets
        }
        stat.axisRaw.copyInto(newStat, headerSize)
        var cursor = recordsStart
        for (i in kept.indices) {
            writeU16(newStat, offsetsOffset + i * 2, (cursor - offsetsOffset))
            kept[i].raw.copyInto(newStat, cursor)
            cursor += kept[i].raw.size
        }

        val out = bytes.copyOf()
        newStat.copyInto(out, stat.tableOffset)
        // 清空旧 STAT 表剩余区，保证整表校验和确定
        val oldEnd = stat.tableOffset + stat.length
        for (i in stat.tableOffset + newStatLen until oldEnd) out[i] = 0

        // 更新表目录：STAT 新校验和 / 新长度（offset 不变）
        val statEntry = findEntry(out, "STAT") ?: throw IllegalArgumentException("STAT entry lost")
        writeU32(out, statEntry + 8, stat.tableOffset.toLong())
        writeU32(out, statEntry + 12, newStatLen.toLong())
        writeU32(out, statEntry + 4, checksum(newStat))

        // 重算 head.checkSumAdjustment：整表校验和（head 调整字段清零）后取 0xB1B0AFBA 补数
        val headEntry = findEntry(out, "head") ?: throw IllegalArgumentException("head table missing")
        val headOffset = u32(out, headEntry + 8).toInt()
        writeU32(out, headOffset + 8, 0)
        val total = checksum(out)
        val adjustment = (MAGIC_HEAD_CHECKSUM - total) and 0xFFFFFFFFL
        writeU32(out, headOffset + 8, adjustment)
        return out
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

    private fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or
            ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or
            (b[o + 3].toLong() and 0xFF)

    private fun writeU16(b: ByteArray, o: Int, v: Int) {
        b[o] = ((v ushr 8) and 0xFF).toByte()
        b[o + 1] = (v and 0xFF).toByte()
    }

    private fun writeU32(b: ByteArray, o: Int, v: Long) {
        b[o] = ((v ushr 24) and 0xFF).toByte()
        b[o + 1] = ((v ushr 16) and 0xFF).toByte()
        b[o + 2] = ((v ushr 8) and 0xFF).toByte()
        b[o + 3] = (v and 0xFF).toByte()
    }
}
