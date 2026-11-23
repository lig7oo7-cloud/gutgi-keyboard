package com.lig7oo7.gutgi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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

/** 화면 공유 동의를 받은 뒤 채팅 화면을 딱 한 장 찍고 바로 종료하는 서비스 */
public class ShotService extends Service {

    public interface Callback { void onShot(File file); }

    /** ShotActivity 가 결과를 기다리는 통로 (같은 앱 안) */
    public static volatile Callback callback;

    private static final String CHANNEL = "shot";
    private static final long SETTLE_MS = 700;   // 동의 창이 사라질 때까지 기다림

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private long startAt;
    private boolean done;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat();
        int code = intent == null ? 0 : intent.getIntExtra("code", 0);
        Intent data = intent == null ? null : (Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra("data", Intent.class)
                : intent.getParcelableExtra("data"));
        if (data == null) { finish(null); return START_NOT_STICKY; }
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
        } catch (Exception e) {
            projection = null;
        }
        if (projection == null) { finish(null); return START_NOT_STICKY; }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { main.post(() -> finish(null)); }
        }, main);

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
        Handler bg = new Handler(thread.getLooper());
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        startAt = SystemClock.uptimeMillis();
        reader.setOnImageAvailableListener(r -> {
            Image img = null;
            try { img = r.acquireLatestImage(); } catch (Exception ignored) { }
            if (img == null) return;
            if (done || SystemClock.uptimeMillis() - startAt < SETTLE_MS) { img.close(); return; }
            File f = save(img);
            img.close();
            main.post(() -> finish(f));
        }, bg);
        display = projection.createVirtualDisplay("shot", w, h, getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, bg);
        // 화면이 가만히 있으면 새 프레임이 안 올 수 있으니 시간 제한
        main.postDelayed(() -> { if (!done) finish(null); }, 6000);
        return START_NOT_STICKY;
    }

    private File save(Image img) {
        try {
            Image.Plane plane = img.getPlanes()[0];
            int rowPx = plane.getRowStride() / plane.getPixelStride();
            Bitmap full = Bitmap.createBitmap(rowPx, img.getHeight(), Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(plane.getBuffer());
            Bitmap crop = Bitmap.createBitmap(full, 0, 0, img.getWidth(), img.getHeight());
            // 글자는 읽을 수 있게, 용량은 작게
            float scale = Math.min(1f, 1600f / crop.getHeight());
            Bitmap out = scale < 1f ? Bitmap.createScaledBitmap(crop,
                    (int) (crop.getWidth() * scale), (int) (crop.getHeight() * scale), true) : crop;
            File f = ShotProvider.shotFile(this);
            try (FileOutputStream os = new FileOutputStream(f)) {
                out.compress(Bitmap.CompressFormat.JPEG, 85, os);
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private void finish(File f) {
        if (done) return;
        done = true;
        try { if (display != null) display.release(); } catch (Exception ignored) { }
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        try { if (projection != null) projection.stop(); } catch (Exception ignored) { }
        if (thread != null) thread.quitSafely();
        Callback cb = callback;
        if (cb != null) cb.onShot(f);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void startForegroundCompat() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "화면 캡처", NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("채팅 화면 캡처 중")
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(11, n);
    }
}
