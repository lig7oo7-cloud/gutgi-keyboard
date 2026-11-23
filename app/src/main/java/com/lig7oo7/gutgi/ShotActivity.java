package com.lig7oo7.gutgi;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;

/**
 * 투명 화면: 화면 공유 동의 → 채팅 화면 캡처 → 캡처본과 아재개그 요청을 Gemini 앱으로 넘긴다.
 * (Gemini 구독 앱은 결과를 돌려주는 통로가 없어서, 답은 Gemini에서 복사해 오면 키보드 '기록'에 뜬다)
 */
public class ShotActivity extends Activity {

    private static final int REQ = 1;

    public static final String PROMPT =
            "이 카카오톡 대화 화면에서 상대방이 마지막으로 보낸 메시지에 답장해 줘. " +
            "아재개그(말장난·언어유희) 스타일로 서로 다른 답장 3개를 만들어 줘. " +
            "각 답장은 한 줄, 30자 이내로, 설명이나 번호 없이 답장 문장만 줄바꿈으로 구분해 줘. " +
            "화면 아래쪽 키보드 부분은 무시해.";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getIntent().getBooleanExtra("sendOnly", false)) {
            // 무료 사용량이 끝났을 때: 이미 찍어둔 화면을 Gemini 앱으로
            sendToGemini();
            finish();
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ);
    }

    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ || resultCode != RESULT_OK || data == null) {
            finish();
            return;
        }
        ShotService.callback = file -> runOnUiThread(() -> {
            ShotService.callback = null;
            if (file == null) {
                Toast.makeText(this, "화면을 캡처하지 못했어요. 다시 눌러 주세요", Toast.LENGTH_SHORT).show();
            } else if (!AjaeApi.key(this).isEmpty()) {
                AjaeBus.start(this, file);   // 키보드 안에서 바로 답장 받기
            } else {
                sendToGemini();               // 키가 없으면 Gemini 앱 방식
            }
            finish();
        });
        // 안전장치: 결과가 안 오면 투명 화면이 남지 않게 닫는다
        h.postDelayed(() -> { if (!isFinishing()) { ShotService.callback = null; finish(); } }, 9000);
        Intent svc = new Intent(this, ShotService.class)
                .putExtra("code", resultCode)
                .putExtra("data", data);
        startForegroundService(svc);
    }

    void sendToGemini() {
        // Gemini가 같이 보낸 글을 무시하는 경우를 대비해 요청문도 복사해 둔다 (기록에는 안 남김)
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("아재개그 요청", PROMPT));
        } catch (Exception ignored) { }

        Uri uri = ShotProvider.uri(System.currentTimeMillis());
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/jpeg");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TEXT, PROMPT);
        send.setClipData(ClipData.newRawUri("shot", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        String[] pkgs = {"com.google.android.apps.bard", "com.google.android.googlequicksearchbox"};
        for (String pkg : pkgs) {
            Intent t = new Intent(send).setPackage(pkg);
            if (t.resolveActivity(getPackageManager()) != null) {
                startActivity(t);
                Toast.makeText(this, "Gemini 답을 길게 눌러 복사 → 카톡으로 돌아오면 기록에 떠요", Toast.LENGTH_LONG).show();
                GutgiIME.markAjaePending(this);
                return;
            }
        }
        Intent chooser = Intent.createChooser(send, "Gemini로 보내기");
        chooser.setClipData(ClipData.newRawUri("shot", uri));
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(chooser);
        GutgiIME.markAjaePending(this);
    }
}
