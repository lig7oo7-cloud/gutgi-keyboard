package com.lig7oo7.gutgi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 화면 공유 세션을 열어두고, 요청이 올 때마다 채팅 화면을 한 장 찍는다.
 * 한 번 동의하면 KEEP_MS 동안 유지 → 그동안은 동의 창 없이 바로 캡처.
 */
public class ShotService extends Service {

    public interface Callback { void onShot(File file); }

    public static volatile Callback callback;
    public static volatile boolean alive;

    public static final String ACTION_CAPTURE = "com.lig7oo7.gutgi.CAPTURE";
    public static final String ACTION_STOP = "com.lig7oo7.gutgi.SHOT_STOP";
    public static final long KEEP_MS = 10 * 60 * 1000;

    private static final String CHANNEL = "shot";

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler bg;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Image held;               // 가장 최근 화면 (bg 스레드에서만)
    private long sessionStart;
    private volatile boolean wantShot;
    private volatile long wantAfter;
    private volatile int cropBottom;  // 키보드 위쪽까지만 (0 = 전체)
    private final Runnable stopper = this::stopSelf;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_CAPTURE.equals(action)) {
            if (projection == null) { deliver(null); return START_NOT_STICKY; }
            requestShot(intent.getIntExtra("cropBottom", 0), 120);
            return START_NOT_STICKY;
        }
        // 새 동의로 시작
        startForegroundCompat();
        int code = intent == null ? 0 : intent.getIntExtra("code", 0);
        Intent data = intent == null ? null : (Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra("data", Intent.class)
                : intent.getParcelableExtra("data"));
        releaseSession();
        if (data == null) { deliver(null); stopSelf(); return START_NOT_STICKY; }
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
        } catch (Exception e) {
            projection = null;
        }
        if (projection == null) { deliver(null); stopSelf(); return START_NOT_STICKY; }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { main.post(ShotService.this::stopSelf); }
        }, main);
        openDisplay();
        alive = true;
        // 동의 창이 사라진 뒤의 화면을 찍는다
        requestShot(intent.getIntExtra("cropBottom", 0), 350);
        return START_NOT_STICKY;
    }

    private void openDisplay() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int w, h;
        if (Build.VERSION.SDK_INT >= 30) {
            w = wm.getMaximumWindowMetrics().getBounds().width();
            h = wm.getMaximumWindowMetrics().getBounds().height();
        } else {
            DisplayMetrics m = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(m);
            w = m.widthPixels; h = m.heightPixels;
        }
        thread = new HandlerThread("shot");
        thread.start();
        bg = new Handler(thread.getLooper());
        sessionStart = SystemClock.uptimeMillis();
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(r -> {
            Image img = null;
            try { img = r.acquireLatestImage(); } catch (Exception ignored) { }
            if (img == null) return;
            if (held != null) held.close();
            held = img;
            if (wantShot && SystemClock.uptimeMillis() >= wantAfter) takeShot();
        }, bg);
        display = projection.createVirtualDisplay("shot", w, h, getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, bg);
    }

    /** delayMs 뒤의 화면을 찍는다. 화면 변화가 없으면 가지고 있는 최근 화면을 쓴다 */
    private void requestShot(int crop, long delayMs) {
        main.removeCallbacks(stopper);
        cropBottom = crop;
        wantAfter = SystemClock.uptimeMillis() + delayMs;
        wantShot = true;
        bg.postDelayed(() -> { if (wantShot) takeShot(); }, delayMs + 150);
    }

    /** bg 스레드 */
    private void takeShot() {
        if (!wantShot) return;
        if (held == null) {
            // 첫 화면이 아직 없으면 조금 더 기다린다 (최대 3초)
            if (SystemClock.uptimeMillis() - wantAfter < 3000) {
                bg.postDelayed(this::takeShot, 100);
                return;
            }
            wantShot = false;
            main.post(() -> deliver(null));
            return;
        }
        wantShot = false;
        File f = save(held);
        main.post(() -> {
            deliver(f);
            main.postDelayed(stopper, KEEP_MS);   // 일정 시간 안 쓰면 세션 종료
        });
    }

    /** 채팅 부분만 잘라 작게 저장: 위쪽 상태표시줄과 키보드는 빼고, 최근 대화가 있는 아래쪽 위주 */
    private File save(Image img) {
        try {
            Image.Plane plane = img.getPlanes()[0];
            int rowPx = plane.getRowStride() / plane.getPixelStride();
            Bitmap full = Bitmap.createBitmap(rowPx, img.getHeight(), Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(plane.getBuffer());
            int w = img.getWidth(), h = img.getHeight();
            int bottom = cropBottom > 0 && cropBottom < h ? cropBottom : h;
            int top = Math.max((int) (h * 0.06f), bottom - (int) (h * 0.62f));   // 최근 대화 위주
            Bitmap crop = Bitmap.createBitmap(full, 0, top, w, bottom - top);
            full.recycle();
            float scale = Math.min(1f, 720f / crop.getWidth());
            Bitmap out = scale < 1f ? Bitmap.createScaledBitmap(crop,
                    (int) (crop.getWidth() * scale), (int) (crop.getHeight() * scale), true) : crop;
            File f = ShotProvider.shotFile(this);
            try (FileOutputStream os = new FileOutputStream(f)) {
                out.compress(Bitmap.CompressFormat.JPEG, 72, os);
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private void deliver(File f) {
        Callback cb = callback;
        callback = null;
        if (cb != null) cb.onShot(f);
    }

    private void releaseSession() {
        alive = false;
        wantShot = false;
        try { if (display != null) display.release(); } catch (Exception ignored) { }
        display = null;
        final ImageReader r = reader;
        final HandlerThread t = thread;
        reader = null;
        if (bg != null) {
            bg.post(() -> {
                try { if (held != null) held.close(); } catch (Exception ignored) { }
                held = null;
                try { if (r != null) r.close(); } catch (Exception ignored) { }
                if (t != null) t.quitSafely();
            });
        }
        try { if (projection != null) projection.stop(); } catch (Exception ignored) { }
        projection = null;
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(stopper);
        releaseSession();
        deliver(null);
        super.onDestroy();
    }

    private void startForegroundCompat() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "화면 캡처", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, ShotService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("아재개그 답장 준비됨")
                .setContentText("10분 동안 화면 공유 없이 바로 답장 (안 쓰면 자동 종료)")
                .addAction(new Notification.Action.Builder(null, "지금 끄기", stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(11, n);
    }
}
