package com.lig7oo7.gutgi;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 설정 안내 + 연습 화면 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad * 2, pad, pad);
        box.setBackgroundColor(0xFFFFF8F4);

        TextView title = new TextView(this);
        title.setText("굿기 키보드");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        box.addView(title);

        TextView guide = new TextView(this);
        guide.setTextSize(15);
        guide.setTextColor(0xFF444444);
        guide.setPadding(0, pad / 2, 0, pad / 2);
        guide.setText(
                "자음: 탭 = 가운데 글자, 굿기 = 그 방향 작은 글자\n" +
                "  ㄱ ↑ㄲ ←→ㅋ   ㄴ ↑ㄸ ↓ㄷ ←→ㅌ\n" +
                "  ㅁ ↑ㅃ ↓ㅂ ←→ㅍ   ㅅ ↑ㅆ   ㅈ ↑ㅉ ←→ㅊ\n\n" +
                "모음 (점을 긋는 방향에 찍는다고 생각)\n" +
                "  ㅣ· 키: 탭 ㅣ, →ㅏ ←ㅓ ↑ㅗ ↓ㅜ  (ㅏ 다음 탭 → ㅑ)\n" +
                "  ·· 키: →ㅑ ←ㅕ ↑ㅛ ↓ㅠ\n" +
                "  ㅡ‖ 키: 탭 ㅡ, →ㅐ ←ㅔ ↑ㅙ ↓ㅞ\n" +
                "     (→ 긋고 탭 → ㅒ,  ← 긋고 탭 → ㅖ)\n" +
                "  ㅢ 키: 탭 ㅢ, →ㅘ ←ㅝ ↑ㅚ ↓ㅟ\n\n" +
                "sel(한글판): 탭 = 전체선택, 왼쪽으로 끌면 커서 앞 글자를\n" +
                "     한 칸씩 선택 (오른쪽으로 되돌리면 줄어듦)\n" +
                "상단: Gemini · 클립보드 · 상용구 · 카톡이모티콘\n" +
                "  카톡이모티콘: 단어를 누르면 입력돼서 카톡이 추천을 띄움\n" +
                "?123: 숫자·기호 (cut 잘라내기, cpy 복사, pst 붙여넣기)\n" +
                "스페이스바를 좌우로 밀면 커서 이동, ⌫ 길게 누르면 연속 삭제");
        box.addView(guide);

        Button enable = new Button(this);
        enable.setText("① 키보드 사용 설정 열기");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        box.addView(enable);

        Button pick = new Button(this);
        pick.setText("② 굿기 키보드로 바꾸기");
        pick.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        box.addView(pick);

        TextView phTitle = new TextView(this);
        phTitle.setText("\n상용구 편집 (한 줄에 하나씩)");
        phTitle.setTextSize(17);
        phTitle.setTextColor(Color.BLACK);
        box.addView(phTitle);

        final EditText phrases = new EditText(this);
        phrases.setMinLines(5);
        phrases.setText(android.text.TextUtils.join("\n", Store.phrases(this)));
        box.addView(phrases);

        Button save = new Button(this);
        save.setText("상용구 저장");
        save.setOnClickListener(v -> {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (String line : phrases.getText().toString().split("\n")) {
                if (!line.trim().isEmpty()) l.add(line.trim());
            }
            Store.save(this, "phrases", l);
            android.widget.Toast.makeText(this, "저장했어요", android.widget.Toast.LENGTH_SHORT).show();
        });
        box.addView(save);

        Button gifPerm = new Button(this);
        gifPerm.setText("카톡이모티콘 패널에서 내 GIF 보기: 사진 접근 허용");
        gifPerm.setOnClickListener(v -> {
            if (GifLibrary.permissionState(this) == 0) requestPermissions(new String[]{GifLibrary.permission()}, 7);
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        });
        box.addView(gifPerm);

        EditText test = new EditText(this);
        test.setHint("③ 여기서 연습해 보세요");
        test.setMinLines(4);
        box.addView(test);

        final ScrollView sv = new ScrollView(this);
        sv.addView(box);
        setContentView(sv);
        if (getIntent().getBooleanExtra("appsettings", false)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        }
        if (getIntent().getBooleanExtra("gifperm", false) && GifLibrary.permissionState(this) != 2) {
            requestPermissions(new String[]{GifLibrary.permission()}, 7);
        }
        if (getIntent().getBooleanExtra("phrases", false)) {
            sv.post(() -> { sv.smoothScrollTo(0, phTitle.getTop()); phrases.requestFocus(); });
        }
    }
}
