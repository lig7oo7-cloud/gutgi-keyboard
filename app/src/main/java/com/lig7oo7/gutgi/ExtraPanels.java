package com.lig7oo7.gutgi;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 키보드 상단 툴바(제미나이·클립보드 기록·상용구·상황어·복사/잘라내기/붙여넣기)와 각 패널 */
public class ExtraPanels {

    public interface Host {
        void insertText(String text);
        void contextAction(int id);
        CharSequence selectedText();
        void replaceWord(String word);
        void openApp(String extra);
    }

    private static final int BG = 0xFFE8EBEF;
    private static final int CHIP = 0xFFFFFFFF;
    private static final int TEXT = 0xFF37474F;
    private static final int SUB = 0xFF7D8892;
    private static final int ACTIVE = 0xFFB2DFDB;

    private final Context ctx;
    private final Host host;
    private final float dp;
    private final View keyboard;
    private final FrameLayout content;
    private final LinearLayout root;
    private final TextView[] tabs = new TextView[4];
    private int open = -1;   // 열린 패널 (-1 = 키보드)

    public ExtraPanels(Context ctx, Host host, View keyboard) {
        this.ctx = ctx;
        this.host = host;
        this.keyboard = keyboard;
        this.dp = ctx.getResources().getDisplayMetrics().density;

        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(px(4), px(4), px(4), px(2));
        String[] names = {"✦ Gemini", "📋 기록", "📝 상용구", "🎯 상황어"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = chip(names[i], 12.5f);
            t.setPadding(px(3), 0, px(3), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(36), 1f);
            lp.setMargins(px(2), 0, px(2), 0);
            bar.addView(t, lp);
            t.setOnClickListener(v -> onTab(idx));
            tabs[i] = t;
        }
        // 복사 · 잘라내기 · 붙여넣기 (아이콘만)
        int[] kinds = {IconView.COPY, IconView.CUT, IconView.PASTE};
        int[] ids = {android.R.id.copy, android.R.id.cut, android.R.id.paste};
        for (int i = 0; i < 3; i++) {
            final int id = ids[i];
            IconView iv = new IconView(ctx, kinds[i]);
            iv.setBackground(round(0xFFD3D7DC));
            iv.setOnClickListener(v -> host.contextAction(id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(38), px(36));
            lp.setMargins(px(2), 0, px(2), 0);
            bar.addView(iv, lp);
        }
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(ctx);
        content.addView(keyboard);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public View root() { return root; }

    /** 입력창이 바뀌면 키보드로 돌아오기 */
    public void reset() { showKeyboard(); }

    private int px(float v) { return (int) (v * dp); }

    private TextView chip(String text, float sp) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(TEXT);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(px(6), 0, px(6), 0);
        t.setBackground(round(CHIP));
        return t;
    }

    private GradientDrawable round(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(8));
        return g;
    }

    private void onTab(int idx) {
        if (idx == 0) { openGemini(); return; }
        if (open == idx) { showKeyboard(); return; }
        View panel;
        if (idx == 1) panel = clipboardPanel();
        else if (idx == 2) panel = phrasePanel();
        else panel = emotionPanel();
        showPanel(idx, panel);
    }

    private void showKeyboard() {
        open = -1;
        content.removeAllViews();
        content.addView(keyboard);
        for (TextView t : tabs) t.setBackground(round(CHIP));
    }

    private void showPanel(int idx, View panel) {
        open = idx;
        int h = keyboard.getHeight() > 0 ? keyboard.getHeight() : px(280);
        content.removeAllViews();
        content.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        for (int i = 0; i < tabs.length; i++) tabs[i].setBackground(round(i == idx ? ACTIVE : CHIP));
    }

    private void refresh() {
        if (open == 1) showPanel(1, clipboardPanel());
        else if (open == 2) showPanel(2, phrasePanel());
    }

    // ───────── 제미나이 ─────────
    private void openGemini() {
        PackageManager pm = ctx.getPackageManager();
        CharSequence sel = host.selectedText();
        String[] pkgs = {"com.google.android.apps.bard", "com.google.android.googlequicksearchbox"};
        try {
            if (sel != null && sel.length() > 0) {
                // 선택한 글이 있으면 제미나이로 보내기
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, sel.toString());
                send.setPackage(pkgs[0]);
                send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (send.resolveActivity(pm) != null) { ctx.startActivity(send); return; }
            }
            Intent launch = pm.getLaunchIntentForPackage(pkgs[0]);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(launch);
                return;
            }
            Intent assist = new Intent(Intent.ACTION_ASSIST);
            assist.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (assist.resolveActivity(pm) != null) { ctx.startActivity(assist); return; }
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com"));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(web);
        } catch (Exception e) {
            Toast.makeText(ctx, "제미나이를 열 수 없어요", Toast.LENGTH_SHORT).show();
        }
    }

    // ───────── 공통: 목록 패널 ─────────
    private LinearLayout column() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(6), px(4), px(6), px(6));
        return col;
    }

    private View scroll(View inner) {
        ScrollView sv = new ScrollView(ctx);
        sv.addView(inner);
        return sv;
    }

    private LinearLayout buttonRow(String[] labels, View.OnClickListener[] actions) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            TextView b = chip(labels[i], 13);
            b.setBackground(round(0xFFD3D7DC));
            b.setOnClickListener(actions[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(34), 1f);
            lp.setMargins(px(3), px(2), px(3), px(4));
            row.addView(b, lp);
        }
        return row;
    }

    /** 한 줄: 글 (탭하면 입력) + ✕ */
    private View item(String text, View.OnClickListener onTap, View.OnClickListener onDelete) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(CHIP));
        TextView t = new TextView(ctx);
        t.setText(text.replace('\n', ' '));
        t.setTextSize(15);
        t.setTextColor(TEXT);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(px(12), px(10), px(6), px(10));
        t.setOnClickListener(onTap);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (onDelete != null) {
            TextView x = new TextView(ctx);
            x.setText("✕");
            x.setTextSize(15);
            x.setTextColor(SUB);
            x.setGravity(Gravity.CENTER);
            x.setOnClickListener(onDelete);
            row.addView(x, new LinearLayout.LayoutParams(px(44), px(44)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(3), 0, px(3));
        row.setLayoutParams(lp);
        return row;
    }

    private TextView note(String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(SUB);
        t.setPadding(px(8), px(10), px(8), px(10));
        return t;
    }

    // ───────── 클립보드 ─────────
    private View clipboardPanel() {
        // 지금 클립보드에 있는 것도 기록에 반영
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = cm.getPrimaryClip();
            if (cd != null && cd.getItemCount() > 0) {
                CharSequence s = cd.getItemAt(0).coerceToText(ctx);
                if (s != null) Store.addClip(ctx, s.toString());
            }
        } catch (Exception ignored) { }

        LinearLayout col = column();
        col.addView(buttonRow(new String[]{"복사", "잘라내기", "붙여넣기", "기록 지우기"}, new View.OnClickListener[]{
                v -> { host.contextAction(android.R.id.copy); v.postDelayed(this::refresh, 300); },
                v -> { host.contextAction(android.R.id.cut); v.postDelayed(this::refresh, 300); },
                v -> host.contextAction(android.R.id.paste),
                v -> { Store.save(ctx, "clips", new java.util.ArrayList<>()); refresh(); }
        }));
        List<String> clips = Store.clips(ctx);
        if (clips.isEmpty()) col.addView(note("복사한 글이 여기에 쌓여요. 탭하면 입력돼요."));
        for (String c : clips) {
            col.addView(item(c, v -> host.insertText(c), v -> {
                List<String> l = Store.clips(ctx);
                l.remove(c);
                Store.save(ctx, "clips", l);
                refresh();
            }));
        }
        return scroll(col);
    }

    // ───────── 상용구 ─────────
    private View phrasePanel() {
        LinearLayout col = column();
        col.addView(buttonRow(new String[]{"＋ 선택한 글 추가", "＋ 최근 복사 추가", "편집"}, new View.OnClickListener[]{
                v -> {
                    CharSequence s = host.selectedText();
                    addPhrase(s == null ? null : s.toString(), "먼저 글을 선택하세요 (sel 키)");
                },
                v -> {
                    List<String> c = Store.clips(ctx);
                    addPhrase(c.isEmpty() ? null : c.get(0), "복사한 글이 없어요");
                },
                v -> host.openApp("phrases")
        }));
        List<String> ph = Store.phrases(ctx);
        if (ph.isEmpty()) col.addView(note("자주 쓰는 문구를 추가해 보세요."));
        for (String p : ph) {
            col.addView(item(p, v -> host.insertText(p), v -> {
                List<String> l = Store.phrases(ctx);
                l.remove(p);
                Store.save(ctx, "phrases", l);
                refresh();
            }));
        }
        return scroll(col);
    }

    private void addPhrase(String s, String emptyMsg) {
        if (s == null || s.trim().isEmpty()) {
            Toast.makeText(ctx, emptyMsg, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> l = Store.phrases(ctx);
        l.remove(s);
        l.add(0, s);
        Store.save(ctx, "phrases", l);
        refresh();
        Toast.makeText(ctx, "상용구에 추가했어요", Toast.LENGTH_SHORT).show();
    }

    // ───────── 🎯 상황어 (카톡 이모티콘 추천 유도) ─────────
    private int wordTab = 0;
    private final java.util.Map<Integer, java.util.ArrayList<String>> shown = new java.util.HashMap<>();
    private final java.util.Map<Integer, java.util.ArrayDeque<String>> pools = new java.util.HashMap<>();

    private java.util.ArrayList<String> shownFor(int cat) {
        java.util.ArrayList<String> l = shown.get(cat);
        if (l == null) {
            l = new java.util.ArrayList<>(java.util.Arrays.asList(Words.ALL[cat].base));
            shown.put(cat, l);
            pools.put(cat, new java.util.ArrayDeque<>(java.util.Arrays.asList(Words.ALL[cat].pool)));
        }
        return l;
    }

    /** 누른 칸을 예비 풀의 새 단어로 교체 (화면에 이미 있는 단어는 건너뜀), 누른 단어는 풀 맨 뒤로 */
    private String rotate(int cat, int index) {
        java.util.ArrayList<String> l = shownFor(cat);
        java.util.ArrayDeque<String> pool = pools.get(cat);
        String used = l.get(index);
        String next = null;
        for (int tries = pool.size(); tries > 0; tries--) {
            String c = pool.poll();
            if (!l.contains(c)) { next = c; break; }
            pool.add(c);
        }
        if (next == null) return used;
        pool.add(used);
        l.set(index, next);
        return next;
    }

    private View emotionPanel() {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(4), px(2), px(4), px(4));

        TextView hint = new TextView(ctx);
        hint.setText("누르면 입력 → 카톡이 어울리는 이모티콘 추천 · 이어 누르면 앞 단어 교체");
        hint.setTextSize(11);
        hint.setTextColor(SUB);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        hint.setPadding(px(6), px(1), px(6), px(2));
        col.addView(hint);

        // 서브 카테고리 칩 바 (가로 스크롤, 컴팩트)
        LinearLayout chipRow = new LinearLayout(ctx);
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chipRow);
        for (int c = 0; c < Words.ALL.length; c++) {
            final int cat = c;
            TextView t = chip(Words.ALL[c].tab, 12.5f);
            t.setPadding(px(10), 0, px(10), 0);
            t.setBackground(round(c == wordTab ? ACTIVE : CHIP));
            t.setOnClickListener(v -> {
                wordTab = cat;
                showPanel(3, emotionPanel());
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28));
            lp.setMargins(px(2), px(1), px(2), px(3));
            chipRow.addView(t, lp);
        }
        col.addView(hs);

        // 4열 × 5행 단어 그리드 (남은 높이를 균등 분할)
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        final int cat = wordTab;
        java.util.ArrayList<String> words = shownFor(cat);
        for (int r = 0; r < 5; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 4; c++) {
                final int index = r * 4 + c;
                final TextView w = chip(words.get(index), 15);
                w.setTypeface(Typeface.DEFAULT_BOLD);
                w.setOnClickListener(v -> {
                    host.replaceWord(shownFor(cat).get(index));
                    w.setText(rotate(cat, index));
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                lp.setMargins(px(2), px(2), px(2), px(2));
                row.addView(w, lp);
            }
            grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        col.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return col;
    }

}
