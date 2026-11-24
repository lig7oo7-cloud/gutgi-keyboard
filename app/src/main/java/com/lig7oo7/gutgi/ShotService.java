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


/**
 * 화면 공유 세션을 열어두고, 캡처 버튼을 누를 때마다 전체 화면을 갤러리에 저장한다.
 * 한 번 동의하면 KEEP_MS 동안 유지 → 그동안은 동의 창 없이 바로 캡처.
 */
public class ShotService extends Service {

    public interface Callback { void onShot(boolean ok); }

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
    private final Runnable stopper = this::stopSelf;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_CAPTURE.equals(action)) {
            if (projection == null) { deliver(false); return START_NOT_STICKY; }
            requestShot(120);
            return START_NOT_STICKY;
        }
        // 새 동의로 시작
        startForegroundCompat();
        int code = intent == null ? 0 : intent.getIntExtra("code", 0);
        Intent data = intent == null ? null : (Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra("data", Intent.class)
                : intent.getParcelableExtra("data"));
        releaseSession();
        if (data == null) { deliver(false); stopSelf(); return START_NOT_STICKY; }
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
        } catch (Exception e) {
            projection = null;
        }
        if (projection == null) { deliver(false); stopSelf(); return START_NOT_STICKY; }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { main.post(ShotService.this::stopSelf); }
        }, main);
        openDisplay();
        alive = true;
        // 동의 창이 사라진 뒤의 화면을 찍는다
        requestShot(350);
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
    private void requestShot(long delayMs) {
        main.removeCallbacks(stopper);
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
            main.post(() -> deliver(false));
            return;
        }
        wantShot = false;
        final boolean ok = save(held);
        main.post(() -> {
            deliver(ok);
            main.postDelayed(stopper, KEEP_MS);   // 일정 시간 안 쓰면 세션 종료
        });
    }

    /** 채팅 부분만 잘라 작게 저장: 위쪽 상태표시줄과 키보드는 빼고, 최근 대화가 있는 아래쪽 위주 */
    /** 전체 화면을 갤러리 Pictures/Screenshots 에 저장 */
    private boolean save(Image img) {
        try {
            Image.Plane plane = img.getPlanes()[0];
            int rowPx = plane.getRowStride() / plane.getPixelStride();
            Bitmap full = Bitmap.createBitmap(rowPx, img.getHeight(), Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(plane.getBuffer());
            Bitmap out = Bitmap.createBitmap(full, 0, 0, img.getWidth(), img.getHeight());
            String name = "Screenshot_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.KOREA)
                    .format(new java.util.Date()) + ".png";
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots");
                v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
            }
            android.content.ContentResolver cr = getContentResolver();
            android.net.Uri uri = cr.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return false;
            try (java.io.OutputStream os = cr.openOutputStream(uri)) {
                out.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                v.clear();
                v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                cr.update(uri, v, null, null);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void deliver(boolean ok) {
        Callback cb = callback;
        callback = null;
        if (cb != null) cb.onShot(ok);
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
        deliver(false);
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
                .setContentTitle("캡처 버튼 준비됨")
                .setContentText("10분 동안 동의 없이 바로 캡처 (안 쓰면 자동 종료)")
                .addAction(new Notification.Action.Builder(null, "지금 끄기", stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(11, n);
    }
}
