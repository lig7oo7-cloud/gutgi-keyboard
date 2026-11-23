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

/** 키보드 상단 툴바(제미나이·클립보드·상용구·이모티콘)와 각 패널 */
public class ExtraPanels {

    public interface Host {
        void insertText(String text);
        void contextAction(int id);
        CharSequence selectedText();
        String[] acceptedTypes();
        boolean acceptsGif();
        void sendGif(GifLibrary.Item item);
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
        String[] names = {"✦ Gemini", "📋 클립보드", "📝 상용구", "😊 카톡이모티콘"};
        float[] weights = {1f, 1f, 1f, 1.25f};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = chip(names[i], 13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(36), weights[i]);
            lp.setMargins(px(3), 0, px(3), 0);
            bar.addView(t, lp);
            t.setOnClickListener(v -> onTab(idx));
            tabs[i] = t;
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

    // ───────── 내 GIF (캡처 앱이 저장한 것) ─────────
    private void addMyGifs(LinearLayout col) {
        // 진단: 지금 입력창이 GIF 직접 넣기를 받는지
        String[] types = host.acceptedTypes();
        String diag = host.acceptsGif()
                ? "✅ 이 입력창은 GIF 직접 넣기 가능"
                : "↪ 이 입력창은 직접 넣기 불가 → 누르면 공유창으로 보냄";
        TextView d = note(diag + (types.length > 0 ? "\n   받는 형식: " + TextUtils.join(", ", types) : "\n   받는 형식: 없음"));
        d.setTextColor(host.acceptsGif() ? 0xFF00897B : 0xFFE65100);
        col.addView(d);

        int perm = GifLibrary.permissionState(ctx);
        if (perm == 0) {
            col.addView(note("⚠ 사진 접근 권한이 없어서 내 GIF를 못 읽어요."));
            col.addView(buttonRow(new String[]{"사진 접근 허용하러 가기"}, new View.OnClickListener[]{
                    v -> host.openApp("gifperm")}));
            return;
        }
        final List<GifLibrary.Item> all = GifLibrary.load(ctx);
        TextView info = note("권한: " + (perm == 2 ? "전체 허용" : "일부 사진만 ⚠") +
                " · 폰에서 보이는 GIF " + GifLibrary.lastGifTotal + "개 · EmotionGif " + all.size() + "개" +
                (GifLibrary.lastError != null ? "\n오류: " + GifLibrary.lastError : ""));
        col.addView(info);
        if (perm == 1) {
            col.addView(buttonRow(new String[]{"'모두 허용'으로 바꾸기 (설정 열기)"}, new View.OnClickListener[]{
                    v -> host.openApp("appsettings")}));
        }
        col.addView(buttonRow(new String[]{"↻ 새로고침"}, new View.OnClickListener[]{
                v -> showPanel(3, emotionPanel())}));
        if (all.isEmpty()) {
            col.addView(note("EmotionGif 폴더의 GIF를 못 찾았어요."));
            return;
        }
        // 태그 필터
        java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
        for (GifLibrary.Item it : all) tags.add(it.tag);
        LinearLayout tagRow = new LinearLayout(ctx);
        HorizontalScrollView ths = new HorizontalScrollView(ctx);
        ths.setHorizontalScrollBarEnabled(false);
        ths.addView(tagRow);
        java.util.List<String> chips = new java.util.ArrayList<>();
        chips.add("전체");
        chips.addAll(tags);
        for (String t : chips) {
            boolean on = (gifFilter == null && t.equals("전체")) || t.equals(gifFilter);
            TextView c = chip(t, 13);
            c.setBackground(round(on ? ACTIVE : CHIP));
            c.setOnClickListener(v -> {
                gifFilter = t.equals("전체") ? null : t;
                showPanel(3, emotionPanel());
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(30));
            lp.setMargins(px(3), px(2), px(3), px(2));
            tagRow.addView(c, lp);
        }
        col.addView(ths);

        // 미리보기 줄
        LinearLayout strip = new LinearLayout(ctx);
        HorizontalScrollView shs = new HorizontalScrollView(ctx);
        shs.addView(strip);
        col.addView(shs);
        final int size = px(76);
        for (final GifLibrary.Item it : all) {
            if (gifFilter != null && !gifFilter.equals(it.tag)) continue;
            final android.widget.ImageView iv = new android.widget.ImageView(ctx);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(round(CHIP));
            iv.setPadding(px(2), px(2), px(2), px(2));
            iv.setOnClickListener(v -> host.sendGif(it));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(px(3), px(3), px(3), px(3));
            strip.addView(iv, lp);
            new Thread(() -> {
                final android.graphics.Bitmap b = GifLibrary.thumb(ctx, it.uri, size);
                if (b != null) iv.post(() -> iv.setImageBitmap(b));
            }).start();
        }
    }

    // ───────── 카톡 이모티콘 유도 ─────────
    private String gifFilter = null;   // null = 전체

    private View emotionPanel() {
        LinearLayout col = column();
        addMyGifs(col);
        col.addView(note("단어를 누르면 입력돼요 → 카톡이 그 단어에 맞는 이모티콘을 추천해요. 위 줄은 바로 넣는 이모지."));

        LinearLayout suggest = new LinearLayout(ctx);
        suggest.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.addView(suggest);
        hs.setVisibility(View.GONE);

        int perRow = 4;
        LinearLayout row = null;
        for (int i = 0; i < Emotions.LIST.length; i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row);
            }
            final String[] e = Emotions.LIST[i];
            TextView w = chip(e[0], 15);
            w.setTypeface(Typeface.DEFAULT_BOLD);
            w.setOnClickListener(v -> {
                host.insertText(e[0]);
                suggest.removeAllViews();
                for (int k = 1; k < e.length; k++) {
                    final String s = e[k];
                    TextView c = chip(s, 15);
                    c.setBackground(round(0xFFFCE4EC));
                    c.setOnClickListener(x -> host.insertText(s));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(36));
                    lp.setMargins(px(3), px(3), px(3), px(3));
                    suggest.addView(c, lp);
                }
                hs.setVisibility(View.VISIBLE);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(38), 1f);
            lp.setMargins(px(3), px(3), px(3), px(3));
            row.addView(w, lp);
        }

        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View sc = scroll(col);
        wrap.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return wrap;
    }
}
