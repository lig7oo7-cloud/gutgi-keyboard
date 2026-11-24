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
                "상단: Gemini · 기록 · 상용구 · 상황어 · 복사/잘라내기/붙여넣기\n" +
                "  상황어: 6개 분류 단어, 누르면 입력(이어 누르면 앞 단어 교체)\n" +
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

        TextView wTitle = new TextView(this);
        wTitle.setText("\n🔤 추천 단어 (초성·앞글자로 추천)\n한 줄에 하나씩. 예) 조심히 들어가 → 'ㅈㅅ' 만 쳐도 추천");
        wTitle.setTextSize(15);
        wTitle.setTextColor(Color.BLACK);
        box.addView(wTitle);
        final EditText wordsIn = new EditText(this);
        wordsIn.setMinLines(4);
        wordsIn.setText(android.text.TextUtils.join("\n", Store.load(this, "userWords")));
        box.addView(wordsIn);
        Button saveWords = new Button(this);
        saveWords.setText("추천 단어 저장");
        saveWords.setOnClickListener(v -> {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (String line : wordsIn.getText().toString().split("\n")) if (!line.trim().isEmpty()) l.add(line.trim());
            Store.save(this, "userWords", l);
            android.widget.Toast.makeText(this, "저장했어요", android.widget.Toast.LENGTH_SHORT).show();
        });
        box.addView(saveWords);


        Button unhide = new Button(this);
        unhide.setText("🎯 상황어: 숨긴 단어 복구 + 배치 초기화 (고정은 유지)");
        unhide.setOnClickListener(v -> {
            Store.save(this, "hiddenWords", new java.util.ArrayList<>());
            for (int c = 0; c < Words.ALL.length; c++) {
                Store.save(this, "shown_" + c, new java.util.ArrayList<>());
                Store.save(this, "pool_" + c, new java.util.ArrayList<>());
            }
            android.widget.Toast.makeText(this, "복구했어요 (키보드를 다시 열면 적용)", android.widget.Toast.LENGTH_SHORT).show();
        });
        box.addView(unhide);

        EditText test = new EditText(this);
        test.setHint("③ 여기서 연습해 보세요");
        test.setMinLines(4);
        box.addView(test);

        final ScrollView sv = new ScrollView(this);
        sv.addView(box);
        setContentView(sv);
        if (getIntent().getBooleanExtra("phrases", false)) {
            sv.post(() -> { sv.smoothScrollTo(0, phTitle.getTop()); phrases.requestFocus(); });
        }
    }
}
