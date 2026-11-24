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
        phTitle.setText("\n📌 클립보드 고정 문구 편집 (한 줄에 하나씩)");
        phTitle.setTextSize(17);
        phTitle.setTextColor(Color.BLACK);
        box.addView(phTitle);

        final EditText phrases = new EditText(this);
        phrases.setMinLines(5);
        phrases.setText(android.text.TextUtils.join("\n", Store.pins(this)));
        box.addView(phrases);

        Button save = new Button(this);
        save.setText("고정 문구 저장");
        save.setOnClickListener(v -> {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (String line : phrases.getText().toString().split("\n")) {
                if (!line.trim().isEmpty()) l.add(line.trim());
            }
            Store.save(this, "pins", l);
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


        // ── 키보드 설정 ──
        final android.content.SharedPreferences ksp = getSharedPreferences("gutgi", MODE_PRIVATE);
        TextView kTitle = new TextView(this);
        kTitle.setText("\n⚙ 키보드 설정");
        kTitle.setTextSize(17);
        kTitle.setTextColor(Color.BLACK);
        box.addView(kTitle);

        final android.widget.RadioGroup darkGroup = new android.widget.RadioGroup(this);
        darkGroup.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        String[] dl = {"밝게", "어둡게", "폰 설정 따라"};
        for (int i = 0; i < 3; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(dl[i]);
            rb.setId(1000 + i);
            darkGroup.addView(rb);
        }
        darkGroup.check(1000 + ksp.getInt("darkMode", 2));
        box.addView(darkGroup);

        final TextView hLbl = new TextView(this);
        final android.widget.SeekBar hBar = new android.widget.SeekBar(this);
        hBar.setMax(50);                                   // 80% ~ 130%
        hBar.setProgress(ksp.getInt("keyHeight", 100) - 80);
        hLbl.setText("키 높이: " + ksp.getInt("keyHeight", 100) + "%");
        hBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar b, int p, boolean u) { hLbl.setText("키 높이: " + (p + 80) + "%"); }
            @Override public void onStartTrackingTouch(android.widget.SeekBar b) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar b) { }
        });
        box.addView(hLbl);
        box.addView(hBar);

        final android.widget.CheckBox learn = new android.widget.CheckBox(this);
        learn.setText("자주 쓰는 말 학습 (2번 이상 쓴 단어를 추천, 폰 안에만 저장)");
        learn.setChecked(ksp.getBoolean("learn", true));
        box.addView(learn);

        TextView rTitle = new TextView(this);
        rTitle.setText("환율 (직접 입력, 인터넷 안 씀) — 예: 10달러 치면 원화 칩");
        box.addView(rTitle);
        final String[] cur = {"USD", "JPY", "EUR", "CNY"};
        final String[] curLbl = {"1달러 =", "100엔 =", "1유로 =", "1위안 ="};
        final EditText[] rateIn = new EditText[4];
        for (int i = 0; i < 4; i++) {
            LinearLayout row = new LinearLayout(this);
            TextView l = new TextView(this);
            l.setText(curLbl[i]);
            l.setWidth((int) (80 * getResources().getDisplayMetrics().density));
            row.addView(l);
            rateIn[i] = new EditText(this);
            rateIn[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            float v = ksp.getFloat("rate_" + cur[i], 0f);
            rateIn[i].setText(v > 0 ? (v == (long) v ? String.valueOf((long) v) : String.valueOf(v)) : "");
            rateIn[i].setHint("원");
            row.addView(rateIn[i], new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            box.addView(row);
        }

        Button saveK = new Button(this);
        saveK.setText("키보드 설정 저장");
        saveK.setOnClickListener(v -> {
            android.content.SharedPreferences.Editor e = ksp.edit()
                    .putInt("darkMode", darkGroup.getCheckedRadioButtonId() - 1000)
                    .putInt("keyHeight", hBar.getProgress() + 80)
                    .putBoolean("learn", learn.isChecked());
            for (int i = 0; i < 4; i++) {
                float r = 0f;
                try { r = Float.parseFloat(rateIn[i].getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
                e.putFloat("rate_" + cur[i], r);
            }
            e.apply();
            android.widget.Toast.makeText(this, "저장했어요 (키보드를 다시 열면 적용)", android.widget.Toast.LENGTH_SHORT).show();
        });
        box.addView(saveK);

        Button forget = new Button(this);
        forget.setText("학습한 단어 모두 지우기");
        forget.setOnClickListener(v -> {
            Store.save(this, "learned", new java.util.ArrayList<>());
            android.widget.Toast.makeText(this, "지웠어요", android.widget.Toast.LENGTH_SHORT).show();
        });
        box.addView(forget);

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
        if (getIntent().getBooleanExtra("micperm", false)) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 9);
        }
        if (getIntent().getBooleanExtra("phrases", false)) {
            sv.post(() -> { sv.smoothScrollTo(0, phTitle.getTop()); phrases.requestFocus(); });
        }
    }
}
