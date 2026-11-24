package com.lig7oo7.gutgi;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** 투명 화면: 화면 녹화 동의를 받아 캡처 서비스를 시작하고 바로 닫힌다 */
public class ShotActivity extends Activity {
    private static final int REQ = 1;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ || resultCode != RESULT_OK || data == null) { finish(); return; }
        final android.content.Context app = getApplicationContext();
        ShotService.callback = ok -> h.post(() ->
                Toast.makeText(app, ok ? "캡처 저장됨 (갤러리 › Screenshots)" : "캡처하지 못했어요", Toast.LENGTH_SHORT).show());
        startForegroundService(new Intent(this, ShotService.class)
                .putExtra("code", resultCode).putExtra("data", data));
        finish();   // 투명 화면이 찍히지 않도록 바로 닫기
    }
}
