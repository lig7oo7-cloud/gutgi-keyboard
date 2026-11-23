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

        TextView aiTitle = new TextView(this);
        aiTitle.setText("\n😄 아재개그 답장 (Gemini API)");
        aiTitle.setTextSize(17);
        aiTitle.setTextColor(Color.BLACK);
        box.addView(aiTitle);
        TextView aiNote = new TextView(this);
        aiNote.setTextSize(13);
        aiNote.setTextColor(0xFF607D8B);
        aiNote.setText("Google AI Studio(aistudio.google.com)에서 발급한 API 키를 붙여넣으세요.\n" +
                "무료 한도를 넘기지 않으려면 AI Studio에서 결제(유료 등급)를 켜 두세요. 몇 번 쓰는 정도면 요금은 아주 적어요.\n" +
                "버튼을 눌렀을 때 캡처한 채팅 화면만 전송되고, 키보드 입력 내용은 보내지 않아요.");
        box.addView(aiNote);
        final android.content.SharedPreferences sp = AjaeApi.prefs(this);
        final EditText keyIn = new EditText(this);
        keyIn.setHint("API 키");
        keyIn.setSingleLine(true);
        keyIn.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyIn.setText(sp.getString("geminiKey", ""));
        box.addView(keyIn);
        final EditText modelIn = new EditText(this);
        modelIn.setHint("모델 (비우면 " + AjaeApi.DEFAULT_MODEL + " / 더 웃기게: gemini-3.8-flash)");
        modelIn.setSingleLine(true);
        modelIn.setText(sp.getString("geminiModel", ""));
        box.addView(modelIn);
        final android.widget.CheckBox spicy = new android.widget.CheckBox(this);
        spicy.setText("야한 농담(19금 말장난) 포함");
        spicy.setChecked(sp.getBoolean("spicy", true));
        box.addView(spicy);
        TextView roomLbl = new TextView(this);
        roomLbl.setText("우리 단톡방 설명 (분위기·은어)");
        box.addView(roomLbl);
        final EditText roomIn = new EditText(this);
        roomIn.setMinLines(3);
        roomIn.setText(AjaeApi.room(this));
        box.addView(roomIn);
        TextView exLbl = new TextView(this);
        exLbl.setText("웃겼던 예시 (한 줄에 '상대 말 → 내 답')  ← 많이 적을수록 내 스타일로 나와요");
        box.addView(exLbl);
        final EditText exIn = new EditText(this);
        exIn.setMinLines(4);
        exIn.setText(AjaeApi.examples(this));
        box.addView(exIn);

        Button saveAi = new Button(this);
        saveAi.setText("AI 설정 저장");
        saveAi.setOnClickListener(v -> {
            sp.edit().putString("geminiKey", keyIn.getText().toString().trim())
                    .putString("geminiModel", modelIn.getText().toString().trim())
                    .putBoolean("spicy", spicy.isChecked())
                    .putString("room", roomIn.getText().toString().trim())
                    .putString("examples", exIn.getText().toString().trim()).apply();
            android.widget.Toast.makeText(this, "저장했어요", android.widget.Toast.LENGTH_SHORT).show();
        });
        box.addView(saveAi);

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
        if (getIntent().getBooleanExtra("aisettings", false)) {
            sv.post(() -> { sv.smoothScrollTo(0, aiTitle.getTop()); keyIn.requestFocus(); });
        }
        if (getIntent().getBooleanExtra("phrases", false)) {
            sv.post(() -> { sv.smoothScrollTo(0, phTitle.getTop()); phrases.requestFocus(); });
        }
    }
}
