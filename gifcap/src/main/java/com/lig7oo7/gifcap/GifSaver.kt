package com.lig7oo7.gifcap

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 공용 사진 폴더 Pictures/EmotionGif 에 저장 → 갤러리·파일앱·키보드 앱에서 바로 보임 */
object GifSaver {
    const val FOLDER = "EmotionGif"

    val TAGS = listOf("웃참", "비웃음", "박장대소", "어이없음", "당황", "감사", "기타")

    fun fileName(tag: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
        val safe = tag.replace(Regex("[\\\\/:*?\"<>|\\s]"), "")
        return "${safe}_$stamp.gif"
    }

    fun save(ctx: Context, name: String, bytes: ByteArray): Uri? {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IllegalStateException("열 수 없음")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }
}
