package com.lig7oo7.gifcap

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 화면 캡처 동의를 받은 뒤 계속 떠 있는 서비스.
 * - 크롭 프레임 + 플로팅 버튼 오버레이
 * - [1.5초 GIF] → 오버레이 숨김 → 지정 영역만 12fps로 18프레임 캡처 → GIF 인코딩 → 태그 선택 → 저장
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_STOP = "com.lig7oo7.gifcap.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "gifcap"
        private const val NOTI_ID = 7

        const val FPS = 12
        const val DURATION_MS = 1500
        const val MAX_SIDE = 360          // GIF 최대 가로/세로(px) — 용량과 속도 균형

        @Volatile var running = false
    }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private lateinit var bgThread: HandlerThread
    private lateinit var bg: Handler

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var screenW = 0
    private var screenH = 0

    private var frameView: CropFrameView? = null
    private lateinit var frameParams: WindowManager.LayoutParams
    private var bar: LinearLayout? = null
    private lateinit var barParams: WindowManager.LayoutParams
    private var popup: View? = null
    private var lockBtn: TextView? = null
    private var shotBtn: TextView? = null

    // 캡처 상태 (bg 스레드에서만 접근)
    @Volatile private var capturing = false
    private var held: Image? = null
    private var lastFrame: IntArray? = null
    private var lastConvert = 0L
    private val frames = ArrayList<IntArray>()
    private val crop = Rect()
    private var outW = 0
    private var outH = 0

    private val dp get() = resources.displayMetrics.density

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        bgThread = HandlerThread("gifcap").apply { start() }
        bg = Handler(bgThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 안드로이드 14+: 화면 캡처 전에 반드시 mediaProjection 유형으로 포그라운드 시작
        startForegroundCompat()

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        releaseProjection()

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = try { mpm.getMediaProjection(code, data) } catch (e: Exception) { null }
        if (mp == null) {
            toast("화면 캡처를 시작할 수 없어요")
            stopSelf()
            return START_NOT_STICKY
        }
        projection = mp
        // 안드로이드 14+: createVirtualDisplay 전에 콜백 등록 필수
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { stopSelf() } }
        }, main)

        setupDisplay(mp)
        if (frameView == null) showOverlays()
        running = true
        toast("영역을 이모티콘에 맞추고 [고정] → [1.5초 GIF]")
        return START_NOT_STICKY
    }

    // ───────── 포그라운드 알림 ─────────
    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "GIF 캡처", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle("이모티콘 GIF 캡처 대기 중")
            .setContentText("화면 위 버튼으로 1.5초 GIF 저장")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "종료", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTI_ID, n)
        }
    }

    // ───────── 가상 디스플레이 ─────────
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    private fun setupDisplay(mp: MediaProjection) {
        val (w, h) = screenSize()
        screenW = w; screenH = h
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        reader = r
        r.setOnImageAvailableListener({ onImage(it) }, bg)
        display = mp.createVirtualDisplay(
            "gifcap", w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, bg
        )
    }

    /** bg 스레드: 새 화면이 올 때마다 호출 */
    private fun onImage(r: ImageReader) {
        val img = try { r.acquireLatestImage() } catch (e: Exception) { null } ?: return
        if (!capturing) {
            // 대기 중엔 가장 최근 화면 1장만 들고 있다 (정지 화면 대비)
            held?.close()
            held = img
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastConvert >= 1000L / (FPS * 2)) {
            lastConvert = now
            lastFrame = try { convert(img) } catch (e: Exception) { lastFrame }
        }
        img.close()
    }

    /** 전체 화면 이미지 → 크롭 → 축소 → ARGB 배열 */
    private fun convert(img: Image): IntArray {
        val plane = img.planes[0]
        val rowPx = plane.rowStride / plane.pixelStride
        val full = Bitmap.createBitmap(rowPx, img.height, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(plane.buffer)
        val c = Rect(crop)
        c.intersect(0, 0, img.width, img.height)
        val part = Bitmap.createBitmap(full, c.left, c.top, c.width(), c.height())
        val scaled = if (part.width != outW || part.height != outH)
            Bitmap.createScaledBitmap(part, outW, outH, true) else part
        val px = IntArray(outW * outH)
        scaled.getPixels(px, 0, outW, 0, 0, outW, outH)
        if (scaled !== part) scaled.recycle()
        part.recycle()
        full.recycle()
        return px
    }

    // ───────── 오버레이 ─────────
    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlays() {
        val (w, h) = screenSize()
        val r = Prefs.loadRect(this, w, h)
        frameParams = WindowManager.LayoutParams(
            r.width(), r.height(), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = r.left; y = r.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val fv = CropFrameView(this, wm, frameParams) { saveRect() }
        frameView = fv
        wm.addView(fv, frameParams)

        // 플로팅 버튼 묶음
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(6), px(6), px(6), px(6))
            background = GradientDrawable().apply {
                setColor(0xE6263238.toInt()); cornerRadius = 24 * dp
            }
        }
        val grip = btn("⠿", 0x00000000)
        val shot = btn("● 1.5초 GIF", 0xFFE53935.toInt())
        val lock = btn("고정", 0xFF00897B.toInt())
        val close = btn("✕", 0x00000000)
        b.addView(grip); b.addView(shot); b.addView(lock); b.addView(close)
        shotBtn = shot; lockBtn = lock
        bar = b

        val (bx, by) = Prefs.loadBar(this, px(16), (h * 0.75f).toInt())
        barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = bx; y = by }
        wm.addView(b, barParams)

        // 손잡이로 버튼 묶음 이동
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
        grip.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = barParams.x; sy = barParams.y }
                MotionEvent.ACTION_MOVE -> {
                    barParams.x = sx + (e.rawX - dx).toInt()
                    barParams.y = sy + (e.rawY - dy).toInt()
                    wm.updateViewLayout(b, barParams)
                }
                MotionEvent.ACTION_UP -> Prefs.saveBar(this, barParams.x, barParams.y)
            }
            true
        }
        shot.setOnClickListener { startCapture() }
        lock.setOnClickListener { setAdjusting(!fv.adjusting) }
        close.setOnClickListener { stopSelf() }
        setAdjusting(true)
    }

    private fun btn(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(px(12), px(8), px(12), px(8))
        background = GradientDrawable().apply { setColor(color); cornerRadius = 18 * dp }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(px(3), 0, px(3), 0) }
    }

    private fun px(v: Int) = (v * dp).toInt()

    /** 조정 모드: 프레임을 끌 수 있음 / 고정 모드: 터치가 카톡으로 통과 */
    private fun setAdjusting(on: Boolean) {
        val fv = frameView ?: return
        fv.adjusting = on
        frameParams.flags = if (on)
            frameParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else
            frameParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(fv, frameParams)
        lockBtn?.text = if (on) "고정" else "영역 조정"
        if (!on) saveRect()
    }

    /** 프레임의 실제 화면 좌표를 저장 */
    private fun saveRect(): Rect {
        val fv = frameView ?: return Rect()
        val loc = IntArray(2)
        fv.getLocationOnScreen(loc)
        val r = Rect(loc[0], loc[1], loc[0] + fv.width, loc[1] + fv.height)
        Prefs.saveRect(this, Rect(frameParams.x, frameParams.y,
            frameParams.x + frameParams.width, frameParams.y + frameParams.height))
        return r
    }

    private fun setOverlaysVisible(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.INVISIBLE
        frameView?.visibility = vis
        bar?.visibility = vis
    }

    // ───────── 캡처 ─────────
    private fun startCapture() {
        if (capturing || popup != null || reader == null) return
        val r = saveRect()
        r.intersect(0, 0, screenW, screenH)
        if (r.width() < 8 || r.height() < 8) { toast("영역이 화면 밖에 있어요"); return }
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(r.width(), r.height()))
        val ow = maxOf(1, (r.width() * scale).toInt())
        val oh = maxOf(1, (r.height() * scale).toInt())

        // 프레임·버튼이 녹화에 찍히지 않도록 숨긴 뒤 화면이 다시 그려질 시간을 준다
        setOverlaysVisible(false)
        main.postDelayed({
            bg.post {
                crop.set(r); outW = ow; outH = oh
                frames.clear()
                lastFrame = held?.let { h -> try { convert(h) } catch (e: Exception) { null } }
                held?.close(); held = null
                lastConvert = 0L
                capturing = true
                val total = DURATION_MS * FPS / 1000
                val interval = 1000L / FPS
                val start = SystemClock.uptimeMillis()
                val tick = object : Runnable {
                    override fun run() {
                        lastFrame?.let { frames.add(it) }
                        if (frames.size < total && SystemClock.uptimeMillis() - start < DURATION_MS + 1500) {
                            bg.postAtTime(this, start + interval * (frames.size + 1))
                        } else {
                            capturing = false
                            val got = ArrayList(frames)
                            main.post { finishCapture(got, ow, oh) }
                        }
                    }
                }
                bg.postDelayed(tick, interval)
            }
        }, 250)
    }

    private fun finishCapture(got: List<IntArray>, w: Int, h: Int) {
        setOverlaysVisible(true)
        if (got.isEmpty()) {
            toast("화면을 가져오지 못했어요. 다시 시도해 주세요")
            return
        }
        toast("GIF 만드는 중…")
        bg.post {
            val bytes = try { GifEncoder.encode(got, w, h, 1000 / FPS) } catch (e: Exception) { null }
            main.post {
                if (bytes == null) toast("GIF 만들기 실패")
                else showTagPicker(bytes, Bitmap.createBitmap(got[got.size / 2], w, h, Bitmap.Config.ARGB_8888))
            }
        }
    }

    // ───────── 감정 태그 팝업 ─────────
    private fun showTagPicker(bytes: ByteArray, preview: Bitmap) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(18), px(16), px(18), px(14))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 20 * dp }
            elevation = 12 * dp
        }
        card.addView(TextView(this).apply {
            text = "감정 태그를 고르면 저장돼요"
            textSize = 16f
            setTextColor(0xFF263238.toInt())
        })
        card.addView(ImageView(this).apply {
            setImageBitmap(preview)
            adjustViewBounds = true
            maxHeight = px(140)
            setPadding(0, px(10), 0, px(10))
        })
        card.addView(TextView(this).apply {
            text = "${bytes.size / 1024}KB"
            textSize = 12f
            setTextColor(0xFF78909C.toInt())
        })
        val grid = GridLayout(this).apply { columnCount = 4; setPadding(0, px(8), 0, px(4)) }
        for (tag in GifSaver.TAGS) {
            grid.addView(TextView(this).apply {
                text = tag
                textSize = 14f
                setTextColor(0xFF263238.toInt())
                gravity = Gravity.CENTER
                setPadding(px(10), px(10), px(10), px(10))
                background = GradientDrawable().apply { setColor(0xFFE0F2F1.toInt()); cornerRadius = 12 * dp }
                layoutParams = GridLayout.LayoutParams().apply { setMargins(px(4), px(4), px(4), px(4)) }
                setOnClickListener { saveGif(tag, bytes) }
            })
        }
        card.addView(grid)
        card.addView(TextView(this).apply {
            text = "저장 안 함"
            textSize = 14f
            setTextColor(0xFF90A4AE.toInt())
            setPadding(px(12), px(10), px(12), px(4))
            setOnClickListener { closePopup() }
        })

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER; dimAmount = 0.4f }
        popup = card
        wm.addView(card, p)
    }

    private fun saveGif(tag: String, bytes: ByteArray) {
        val name = GifSaver.fileName(tag)
        closePopup()
        bg.post {
            val uri = GifSaver.save(this, name, bytes)
            main.post {
                toast(if (uri != null) "저장: Pictures/${GifSaver.FOLDER}/$name" else "저장 실패")
            }
        }
    }

    private fun closePopup() {
        popup?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        popup = null
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ───────── 정리 ─────────
    private fun releaseProjection() {
        capturing = false
        display?.release(); display = null
        val r = reader
        reader = null
        r?.setOnImageAvailableListener(null, null)
        bg.post {
            try { held?.close() } catch (_: Exception) { }
            held = null
            try { r?.close() } catch (_: Exception) { }
        }
        projection?.stop(); projection = null
    }

    override fun onDestroy() {
        running = false
        closePopup()
        frameView?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        bar?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        frameView = null; bar = null
        releaseProjection()
        bgThread.quitSafely()
        super.onDestroy()
    }
}
