package com.lig7oo7.gutgi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 굿기키 설정 (딩굴 옵션창 참고). 지구본 키를 길게 누르면 열림.
 * 바꾸면 바로 저장되고, 키보드를 다시 열면 적용된다.
 */
public class SettingsActivity extends Activity {

    private SharedPreferences sp;
    private float dp;
    private String page = "main";
    private ScrollView scroll;

    private static final int BG = 0xFFFAFAFC, HEAD = 0xFFF1F1F3, LINE = 0xFFE6E6EA, SUBC = 0xFF7D8892, BLUE = 0xFF1A73E8;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = Prefs.sp(this);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        show("main");
    }

    @Override
    public void onBackPressed() {
        if (!page.equals("main")) show("main");
        else super.onBackPressed();
    }

    private int px(float v) { return (int) (v * dp); }

    // ───────── 화면 틀 ─────────
    private LinearLayout box;

    private void show(String p) {
        page = p;
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        TextView head = new TextView(this);
        head.setText(titleOf(p));
        head.setTextSize(24);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextColor(0xFF111111);
        head.setBackgroundColor(HEAD);
        head.setPadding(px(22), px(28), px(22), px(18));
        if (!p.equals("main")) {
            head.setText("‹  " + titleOf(p));
            head.setOnClickListener(v -> show("main"));
        }
        outer.addView(head);

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(18), 0, px(18), px(40));
        scroll = new ScrollView(this);
        scroll.addView(box);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        switch (p) {
            case "general": general(); break;
            case "shortcuts": shortcutsPage(); break;
            case "size": sizePage(); break;
            case "toolbar": toolbarPage(); break;
            case "advanced": advanced(); break;
            default: mainPage();
        }
        setContentView(outer);
    }

    private String titleOf(String p) {
        switch (p) {
            case "general": return "환경설정";
            case "shortcuts": return "단축어";
            case "size": return "키보드 크기 및 자판";
            case "toolbar": return "툴바 설정";
            case "advanced": return "고급";
            default: return "굿기키 설정";
        }
    }

    private void line() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        box.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(0.7f))));
    }

    private TextView title(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(18);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(0xFF111111);
        return v;
    }

    private TextView sub(String t, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13.5f);
        v.setTextColor(color);
        v.setPadding(0, px(3), 0, 0);
        return v;
    }

    private void section(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(SUBC);
        v.setPadding(0, px(22), 0, px(6));
        box.addView(v);
    }

    /** 메뉴 한 줄 (누르면 하위 화면) */
    private void menu(String t, String desc, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, px(20), 0, px(20));
        row.addView(title(t));
        if (desc != null) row.addView(sub(desc, SUBC));
        row.setOnClickListener(l);
        box.addView(row);
        line();
    }

    /** 스위치 한 줄 */
    private Switch toggle(String t, String desc, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, px(16), 0, px(16));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(title(t));
        if (desc != null) texts.addView(sub(desc, SUBC));
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, on) -> sp.edit().putBoolean(key, on).apply());
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        box.addView(row);
        line();
        return sw;
    }

    /** 슬라이더 한 줄. fmt 로 값 표시 */
    private void slider(String t, String key, int def, int min, int max, java.util.function.IntFunction<String> fmt) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, px(16), 0, px(10));
        row.addView(title(t));
        final TextView val = sub(fmt.apply(sp.getInt(key, def)), BLUE);
        row.addView(val);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(sp.getInt(key, def) - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                val.setText(fmt.apply(p + min));
                sp.edit().putInt(key, p + min).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        row.addView(bar);
        box.addView(row);
        line();
    }

    /** 라디오 한 줄 (가로로 나열) */
    private void radio(String t, String key, int def, String... labels) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, px(16), 0, px(10));
        row.addView(title(t));
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setTextSize(15);
            rb.setId(5000 + i);
            g.addView(rb);
        }
        g.check(5000 + sp.getInt(key, def));
        g.setOnCheckedChangeListener((gr, id) -> sp.edit().putInt(key, id - 5000).apply());
        row.addView(g);
        box.addView(row);
        line();
    }

    private void radioBool(String t, String key, boolean def, String off, String on) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, px(16), 0, px(10));
        row.addView(title(t));
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton a = new RadioButton(this), b = new RadioButton(this);
        a.setText(off); b.setText(on);
        a.setTextSize(15); b.setTextSize(15);
        a.setId(6000); b.setId(6001);
        g.addView(a); g.addView(b);
        g.check(sp.getBoolean(key, def) ? 6001 : 6000);
        g.setOnCheckedChangeListener((gr, id) -> sp.edit().putBoolean(key, id == 6001).apply());
        row.addView(g);
        box.addView(row);
        line();
    }

    private void note(String t) {
        TextView v = sub(t, SUBC);
        v.setPadding(0, px(10), 0, px(10));
        box.addView(v);
    }

    private EditText practice() {
        EditText e = new EditText(this);
        e.setHint("여기를 눌러 키보드를 열고 바로 확인해 보세요");
        e.setMinLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(14), 0, 0);
        box.addView(e, lp);
        return e;
    }

    private Button button(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        box.addView(b);
        return b;
    }

    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_SHORT).show(); }

    // ───────── 페이지들 ─────────
    private void mainPage() {
        menu("환경설정", "진동·소리, 더블스페이스 마침표, 자동 대문자, 지구본 키, 다크 모드", v -> show("general"));
        menu("단축어", "짧게 치고 스페이스 → 긴 문구로 바뀜", v -> show("shortcuts"));
        menu("키보드 크기 및 자판", "세로·가로 높이와 폭, 가로모드 딩굴/쿼티·가운데 분리, 굿기 민감도", v -> show("size"));
        menu("툴바 설정", "툴바 아이콘 보이기/숨기기 (네이버 검색 포함)", v -> show("toolbar"));
        menu("고급", "고정 문구, 추천 단어, 환율, 학습 지우기, 상황어 복구", v -> show("advanced"));
        note("지구본(🌐) 키: 짧게 = 한/영 전환, 위로 밀기 = 숫자·기호, 길게 = 이 설정창\n바꾼 설정은 키보드를 다시 열면 적용돼요.");
        practice();
    }

    private void general() {
        radio("다크 모드", "darkMode", 2, "밝게", "어둡게", "폰 설정 따라");
        toggle("키를 누를 때 진동 발생", null, "vib", true);
        slider("키를 누를 때 진동 시간", "vibMs", 0, 0, 60, v -> v == 0 ? "시스템 기본값" : v + " ms (클수록 세게)");
        toggle("키를 굿기(슬라이드)할 때 진동 발생", "방향이 정해지는 순간 톡", "vibSlide", true);
        toggle("키를 누를 때 소리 발생", null, "sound", false);
        slider("키를 누를 때 소리 볼륨", "soundVol", 50, 0, 100, v -> v + "%");
        toggle("더블스페이스 마침표", "스페이스바를 두 번 탭하면 마침표와 공백 한 개가 삽입됩니다.", "doubleSpace", false);
        toggle("자동 대문자화 (영어)", "문장의 첫 글자를 대문자로", "autoCap", true);
        radioBool("지구본 키 짧게 누르기", "globeCycle", false, "한 ↔ 영", "한 → 영 → 숫자 순환");
        toggle("자주 쓰는 말 학습", "2번 이상 쓴 단어를 추천 (폰 안에만 저장, 비밀번호 칸 제외)", "learn", true);
        practice();
    }

    private void shortcutsPage() {
        note("단축어를 치고 스페이스를 누르면 문구로 바뀌어요. 치는 중에도 맨 앞 ⚡ 칩으로 떠요.\n예) ㅇㄷ → 지금 어디야?   ㅈㅅ → 주소: 서울시 …");
        final EditText key = new EditText(this);
        key.setHint("단축어 (예: ㅇㄷ)");
        key.setSingleLine(true);
        final EditText val = new EditText(this);
        val.setHint("바뀔 문구");
        val.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(key);
        box.addView(val);
        button("＋ 추가 (같은 단축어면 바꿈)", v -> {
            String k = key.getText().toString().trim(), t = val.getText().toString().trim();
            if (k.isEmpty() || t.isEmpty()) { toast("단축어와 문구를 둘 다 써 주세요"); return; }
            if (k.contains(" ")) { toast("단축어에는 띄어쓰기를 넣을 수 없어요"); return; }
            Map<String, String> m = Prefs.shortcuts(this);
            m.put(k, t);
            saveShortcuts(m);
            show("shortcuts");
        });
        section("등록된 단축어");
        Map<String, String> m = Prefs.shortcuts(this);
        if (m.isEmpty()) note("아직 없어요.");
        for (final Map.Entry<String, String> e : m.entrySet()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(12), 0, px(12));
            TextView k = title(e.getKey());
            k.setMinWidth(px(70));
            row.addView(k);
            TextView v = sub(e.getValue(), 0xFF333333);
            v.setTextSize(15);
            v.setPadding(px(10), 0, px(6), 0);
            row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(x -> { key.setText(e.getKey()); val.setText(e.getValue()); scroll.smoothScrollTo(0, 0); });
            TextView del = title("✕");
            del.setTextColor(SUBC);
            del.setPadding(px(14), px(4), px(6), px(4));
            del.setOnClickListener(x -> {
                Map<String, String> mm = Prefs.shortcuts(this);
                mm.remove(e.getKey());
                saveShortcuts(mm);
                show("shortcuts");
            });
            row.addView(del);
            box.addView(row);
            line();
        }
        note("줄을 누르면 위 칸으로 불러와 고칠 수 있어요.");
    }

    private void saveShortcuts(Map<String, String> m) {
        List<String> l = new ArrayList<>();
        for (Map.Entry<String, String> e : m.entrySet()) l.add(e.getKey() + "\t" + e.getValue());
        Store.save(this, "shortcuts", l);
    }

    private void sizePage() {
        section("세로모드");
        slider("키 높이", "keyHeight", 100, 70, 130, v -> v + "%");
        slider("키보드 폭", "portWidth", 100, 60, 100, v -> v == 100 ? "꽉 차게" : v + "%");
        radio("폭을 줄였을 때 위치", "portAlign", 1, "왼쪽", "가운데", "오른쪽");
        section("가로모드");
        slider("키보드 높이 (화면 높이 대비)", "landHeight", 42, 28, 60, v -> v + "%  (기본 42%)");
        slider("키보드 폭", "landWidth", 100, 50, 100, v -> v == 100 ? "꽉 차게" : v + "%");
        radio("폭을 줄였을 때 위치", "landAlign", 1, "왼쪽", "가운데", "오른쪽");
        radioBool("가로모드 한글 자판", "landQwerty", false, "딩굴", "쿼티(두벌식)");
        toggle("키보드 분리", "가운데를 비워 양손 엄지로 (사진처럼)", "landSplit", true);
        toggle("스페이스바 분리", "분리할 때 스페이스바도 양쪽에 하나씩", "landSplitSpace", true);
        section("굿기 민감도");
        slider("딩굴 세로(상하) 스와이프 민감도", "sensV", 50, 15, 100, v -> v + "  (작을수록 짧게 밀어도 인식)");
        slider("딩굴 가로(좌우) 스와이프 민감도", "sensH", 50, 15, 100, v -> v + "  (작을수록 짧게 밀어도 인식)");
        note("가로모드 설정은 폰을 눕혀서 연습칸을 누르면 바로 확인돼요.");
        practice();
    }

    private void toolbarPage() {
        toggle("툴바 사용", "끄면 위 아이콘 줄과 추천 단어 줄이 같이 숨겨져요", "toolbar", true);
        section("툴바 아이콘 표시 (켠 것끼리 같은 폭으로 나눔)");
        for (int i = 0; i < Prefs.TOOLS.length; i++) toggle(Prefs.TOOL_NAMES[i], i == 3 ? "선택한 글, 없으면 입력칸에 쓴 글 전체를 네이버로 검색" : null, "tb_" + Prefs.TOOLS[i], true);
        practice();
    }

    private void advanced() {
        section("📌 클립보드 고정 문구 (한 줄에 하나씩)");
        final EditText phrases = new EditText(this);
        phrases.setMinLines(4);
        phrases.setText(android.text.TextUtils.join("\n", Store.pins(this)));
        box.addView(phrases);
        button("고정 문구 저장", v -> { Store.save(this, "pins", lines(phrases)); toast("저장했어요"); });

        section("🔤 추천 단어 (초성·앞글자로 추천, 한 줄에 하나씩)");
        final EditText words = new EditText(this);
        words.setMinLines(3);
        words.setText(android.text.TextUtils.join("\n", Store.load(this, "userWords")));
        box.addView(words);
        button("추천 단어 저장", v -> { Store.save(this, "userWords", lines(words)); toast("저장했어요"); });

        section("💱 환율 (직접 입력, 인터넷 안 씀) — 예: 10달러 치면 원화 칩");
        final String[] cur = {"USD", "JPY", "EUR", "CNY"};
        final String[] curLbl = {"1달러 =", "100엔 =", "1유로 =", "1위안 ="};
        final EditText[] in = new EditText[4];
        for (int i = 0; i < 4; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView l = sub(curLbl[i], 0xFF333333);
            l.setMinWidth(px(80));
            row.addView(l);
            in[i] = new EditText(this);
            in[i].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            float r = sp.getFloat("rate_" + cur[i], 0f);
            in[i].setText(r > 0 ? (r == (long) r ? String.valueOf((long) r) : String.valueOf(r)) : "");
            in[i].setHint("원");
            row.addView(in[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            box.addView(row);
        }
        button("환율 저장", v -> {
            SharedPreferences.Editor e = sp.edit();
            for (int i = 0; i < 4; i++) {
                float r = 0f;
                try { r = Float.parseFloat(in[i].getText().toString().replace(",", "").trim()); } catch (Exception ignored) { }
                e.putFloat("rate_" + cur[i], r);
            }
            e.apply();
            toast("저장했어요");
        });

        section("정리");
        button("학습한 단어 모두 지우기", v -> { Store.save(this, "learned", new ArrayList<>()); toast("지웠어요"); });
        button("🎯 상황어: 숨긴 단어 복구 + 배치 초기화 (고정은 유지)", v -> {
            Store.save(this, "hiddenWords", new ArrayList<>());
            for (int c = 0; c < Words.ALL.length; c++) {
                Store.save(this, "shown_" + c, new ArrayList<>());
                Store.save(this, "pool_" + c, new ArrayList<>());
            }
            toast("복구했어요 (키보드를 다시 열면 적용)");
        });
    }

    private static List<String> lines(EditText e) {
        List<String> l = new ArrayList<>();
        for (String s : e.getText().toString().split("\n")) if (!s.trim().isEmpty()) l.add(s.trim());
        return l;
    }

    @SuppressWarnings("unused")
    private static GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }
}
