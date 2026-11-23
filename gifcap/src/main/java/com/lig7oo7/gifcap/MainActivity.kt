package com.lig7oo7.gifcap

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** 권한 안내 + 캡처 시작/종료 화면 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_CAPTURE = 100
        private const val REQ_NOTI = 101
    }

    private lateinit var overlayRow: TextView
    private lateinit var notiRow: TextView
    private lateinit var stateRow: TextView
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(36), px(20), px(24))
            setBackgroundColor(0xFFF5F7F8.toInt())
        }
        box.addView(text("이모티콘 GIF 캡처", 26f, Color.BLACK))
        box.addView(text(
            "카톡 이모티콘이 움직이는 부분만 1.5초 녹화해서 GIF로 저장해요.\n" +
                "저장 위치: 사진 › Pictures/${GifSaver.FOLDER}  (갤러리 'EmotionGif' 앨범)",
            14f, 0xFF546E7A.toInt()
        ).apply { setPadding(0, px(6), 0, px(18)) })

        overlayRow = text("", 15f, 0xFF263238.toInt())
        notiRow = text("", 15f, 0xFF263238.toInt())
        stateRow = text("", 15f, 0xFF263238.toInt())

        box.addView(overlayRow)
        box.addView(button("① 다른 앱 위에 그리기 허용", 0xFF546E7A.toInt()) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        box.addView(notiRow)
        box.addView(button("② 알림 허용 (캡처 중 표시)", 0xFF546E7A.toInt()) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTI)
        })
        box.addView(stateRow)
        box.addView(button("③ 캡처 시작 (화면 녹화 동의)", 0xFFE53935.toInt()) { startCapture() })
        box.addView(button("캡처 종료", 0xFF90A4AE.toInt()) {
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
            refresh()
        })

        box.addView(text(
            "\n사용 순서\n" +
                "1. ③을 누르고 녹화 동의 창에서 '전체 화면'을 고르세요.\n" +
                "2. 카톡으로 가서 이모티콘 창을 엽니다.\n" +
                "3. 초록 프레임을 끌어 이모티콘 위에 맞추고 [고정].\n" +
                "4. 이모티콘을 눌러 미리보기가 움직일 때 [● 1.5초 GIF].\n" +
                "5. 감정 태그를 고르면 '태그_날짜_시간.gif'로 저장.\n" +
                "   프레임 위치·크기는 기억돼서 다음에도 그대로예요.\n\n" +
                "※ 구매한 이모티콘은 카카오톡 안에서 쓰라고 허락된 것이라, 캡처본은 개인적으로만 쓰세요.",
            13f, 0xFF607D8B.toInt()
        ))

        setContentView(ScrollView(this).apply { addView(box) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        val notiOk = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        overlayRow.text = if (overlayOk) "✅ 다른 앱 위에 그리기: 허용됨" else "⬜ 다른 앱 위에 그리기: 필요"
        notiRow.text = if (notiOk) "✅ 알림: 허용됨" else "⬜ 알림: 권장"
        stateRow.text = if (CaptureService.running) "🔴 캡처 대기 중 (화면 위 버튼 사용)" else "⚪ 캡처 꺼짐"
    }

    private fun startCapture() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "먼저 ①을 허용해 주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Activity 기본 API 사용")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "화면 녹화 동의가 필요해요", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(this, CaptureService::class.java)
            .putExtra(CaptureService.EXTRA_CODE, resultCode)
            .putExtra(CaptureService.EXTRA_DATA, data)
        startForegroundService(i)
        moveTaskToBack(true)   // 바로 카톡으로 돌아갈 수 있게
    }

    private fun text(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        setPadding(0, px(4), 0, px(4))
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(px(12), px(14), px(12), px(14))
        background = GradientDrawable().apply { setColor(color); cornerRadius = 14 * dp }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, px(4), 0, px(14)) }
        setOnClickListener { onClick() }
    }
}
