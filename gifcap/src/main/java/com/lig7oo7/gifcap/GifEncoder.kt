package com.lig7oo7.gifcap

import java.io.ByteArrayOutputStream

/**
 * 순수 Kotlin 애니메이션 GIF 인코더 (외부 라이브러리 없음).
 * - 모든 프레임에서 색을 모아 가장 많이 쓰인 256색으로 전역 팔레트를 만든다
 *   (이모티콘처럼 단색 면이 많은 그림에 잘 맞고, 프레임 간 깜빡임이 없다)
 * - LZW 압축, 무한 반복
 */
object GifEncoder {

    /**
     * @param frames ARGB 픽셀 배열 목록 (모두 width*height 크기)
     * @param delayMs 프레임 간격(ms)
     */
    fun encode(frames: List<IntArray>, width: Int, height: Int, delayMs: Int): ByteArray {
        require(frames.isNotEmpty()) { "프레임이 없습니다" }
        val palette = buildPalette(frames)          // 최대 256색 (RGB int)
        val lookup = buildLookup(palette)           // 15비트 색 → 팔레트 번호

        val out = ByteArrayOutputStream()
        // 헤더 + 논리 화면 설명자
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        out.write(0xF7)    // 전역 팔레트 있음, 8비트 색, 256개
        out.write(0)       // 배경색 번호
        out.write(0)       // 픽셀 비율
        for (i in 0 until 256) {
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }
        // 무한 반복 (NETSCAPE2.0)
        out.write(0x21); out.write(0xFF); out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3); out.write(1); writeShort(out, 0); out.write(0)

        val delayCs = (delayMs + 5) / 10
        val indices = ByteArray(width * height)
        for (f in frames) {
            // 그래픽 제어 확장 (프레임 지연)
            out.write(0x21); out.write(0xF9); out.write(4)
            out.write(0x04)              // 이전 프레임 그대로 두고 덮어쓰기
            writeShort(out, delayCs)
            out.write(0); out.write(0)
            // 이미지 설명자
            out.write(0x2C)
            writeShort(out, 0); writeShort(out, 0)
            writeShort(out, width); writeShort(out, height)
            out.write(0)                 // 지역 팔레트 없음, 비월
            for (i in indices.indices) indices[i] = lookup[key15(f[i])]
            lzw(out, indices, 8)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun key15(argb: Int): Int =
        ((argb shr 9) and 0x7C00) or ((argb shr 6) and 0x03E0) or ((argb shr 3) and 0x001F)

    /** 메디안컷: 색 공간을 가장 넓은 축으로 반복해서 나눠 256개 대표색을 만든다 */
    private fun buildPalette(frames: List<IntArray>): IntArray {
        val count = IntArray(32768)
        for (f in frames) for (p in f) count[key15(p)]++
        val used = (0 until 32768).filter { count[it] > 0 }.toIntArray()
        if (used.size <= 256) return IntArray(used.size) { expand(used[it]) }

        fun ch(k: Int, c: Int) = when (c) { 0 -> (k shr 10) and 31; 1 -> (k shr 5) and 31; else -> k and 31 }
        class Box(val keys: IntArray) {
            var axis = 0; var range = 0; var weight = 0L
            init {
                for (c in 0..2) {
                    var lo = 31; var hi = 0
                    for (k in keys) { val v = ch(k, c); if (v < lo) lo = v; if (v > hi) hi = v }
                    if (hi - lo > range) { range = hi - lo; axis = c }
                }
                for (k in keys) weight += count[k]
            }
        }
        val boxes = ArrayList<Box>()
        boxes.add(Box(used))
        while (boxes.size < 256) {
            // 넓고 픽셀이 많은 상자부터 나눈다
            var bi = -1; var bestScore = 0.0
            for (i in boxes.indices) {
                val b = boxes[i]
                if (b.keys.size < 2 || b.range == 0) continue
                val score = b.range.toDouble() * Math.sqrt(b.weight.toDouble())
                if (score > bestScore) { bestScore = score; bi = i }
            }
            if (bi < 0) break
            val b = boxes.removeAt(bi)
            val sorted = b.keys.sortedBy { ch(it, b.axis) }
            var acc = 0L; var cut = 1
            for (i in sorted.indices) {
                acc += count[sorted[i]]
                if (acc * 2 >= b.weight) { cut = (i + 1).coerceIn(1, sorted.size - 1); break }
            }
            boxes.add(Box(sorted.subList(0, cut).toIntArray()))
            boxes.add(Box(sorted.subList(cut, sorted.size).toIntArray()))
        }
        return IntArray(boxes.size) { i ->
            var r = 0L; var g = 0L; var bl = 0L; var w = 0L
            for (k in boxes[i].keys) {
                val n = count[k].toLong()
                r += n * ch(k, 0); g += n * ch(k, 1); bl += n * ch(k, 2); w += n
            }
            expand(((r / w).toInt() shl 10) or ((g / w).toInt() shl 5) or (bl / w).toInt())
        }
    }

    private fun expand(k: Int): Int {
        val r = (k shr 10) and 31; val g = (k shr 5) and 31; val b = k and 31
        return (((r shl 3) or (r shr 2)) shl 16) or (((g shl 3) or (g shr 2)) shl 8) or ((b shl 3) or (b shr 2))
    }

    private fun buildLookup(palette: IntArray): ByteArray {
        val pr = IntArray(palette.size) { (palette[it] shr 16) and 0xFF }
        val pg = IntArray(palette.size) { (palette[it] shr 8) and 0xFF }
        val pb = IntArray(palette.size) { palette[it] and 0xFF }
        val table = ByteArray(32768)
        for (k in 0 until 32768) {
            val r = ((k shr 10) and 31) shl 3
            val g = ((k shr 5) and 31) shl 3
            val b = (k and 31) shl 3
            var best = 0
            var bestD = Int.MAX_VALUE
            for (i in palette.indices) {
                val dr = r - pr[i]; val dg = g - pg[i]; val db = b - pb[i]
                val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
                if (d < bestD) { bestD = d; best = i; if (d == 0) break }
            }
            table[k] = best.toByte()
        }
        return table
    }

    private fun writeShort(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    /** GIF LZW 압축 후 255바이트 블록으로 기록 */
    private fun lzw(out: ByteArrayOutputStream, data: ByteArray, minCodeSize: Int) {
        out.write(minCodeSize)
        val clear = 1 shl minCodeSize
        val eoi = clear + 1

        val block = ByteArray(255)
        var blockLen = 0
        var bitBuf = 0
        var bitCount = 0

        fun flushBlock() {
            if (blockLen > 0) {
                out.write(blockLen)
                out.write(block, 0, blockLen)
                blockLen = 0
            }
        }
        fun emit(code: Int, size: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                block[blockLen++] = (bitBuf and 0xFF).toByte()
                if (blockLen == 255) flushBlock()
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
        }

        // 사전: (prefix 코드 << 8 | 다음 바이트) → 코드
        val dict = HashMap<Int, Int>(8192)
        var codeSize = minCodeSize + 1
        var next = eoi + 1

        emit(clear, codeSize)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val c = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or c
            val found = dict[key]
            if (found != null) {
                prefix = found
                continue
            }
            emit(prefix, codeSize)
            if (next < 4096) {
                dict[key] = next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clear, codeSize)
                dict.clear()
                codeSize = minCodeSize + 1
                next = eoi + 1
            }
            prefix = c
        }
        emit(prefix, codeSize)
        emit(eoi, codeSize)
        if (bitCount > 0) {
            block[blockLen++] = (bitBuf and 0xFF).toByte()
            if (blockLen == 255) flushBlock()
        }
        flushBlock()
        out.write(0)   // 블록 끝
    }
}
