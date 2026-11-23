package com.lig7oo7.gutgi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** 키를 직접 그리고, 탭/굿기(드래그) 방향을 판별하는 뷰 */
public class KeyboardView extends View {

    public interface Listener { void onOutput(String token); }

    private static final int BG = 0xFFE8EBEF;
    private static final int KEY = 0xFFFFFFFF;
    private static final int FUNC = 0xFFD3D7DC;
    private static final int PRESSED = 0xFFCBD5E1;
    private static final int SHADOW = 0xFFBFC5CC;
    private static final int TEXT = 0xFF37474F;
    private static final int SUB = 0xFF7D8892;
    private static final int ACCENT = 0xFFE0245E;
    private static final int SHIFT_ON = 0xFF9FD8CF;

    private Listener listener;
    private List<List<KeyDef>> rows;
    private final float dp;
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseArray<Ptr> ptrs = new SparseArray<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable repeater;
    private int shift;
    private boolean laidOut;
    private boolean enterSearch;
    private String backLabel = "ㄱㄴㄷ";
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private static final int ICON = 0xFF6F767C;

    private static class Ptr {
        KeyDef key;
        float x0, y0, lastX, maxD;
        String dir = "";
        boolean spaceDrag, selStarted;
        int selSteps;
    }

    public KeyboardView(Context c) {
        super(c);
        dp = getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setColor(SUB);
        setHapticFeedbackEnabled(true);
    }

    public void setListener(Listener l) { listener = l; }

    public void setRows(List<List<KeyDef>> r) {
        rows = r;
        laidOut = false;
        ptrs.clear();
        stopRepeat();
        invalidate();
    }

    public void setEnterSearch(boolean b) { enterSearch = b; invalidate(); }
    public void setBackLabel(String l) { backLabel = l; invalidate(); }

    public void setShift(int s) {
        shift = s;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int width = MeasureSpec.getSize(wSpec);
        int height = (int) (5 * 54 * dp + 10 * dp);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        laidOut = false;
    }

    private void layoutKeys() {
        if (rows == null || getWidth() == 0) return;
        float pad = 3 * dp, gap = 5 * dp;
        float rowH = (getHeight() - 2 * pad) / rows.size();
        for (int i = 0; i < rows.size(); i++) {
            List<KeyDef> row = rows.get(i);
            float sum = 0;
            for (KeyDef k : row) sum += k.w;
            float unit = (getWidth() - 2 * pad) / sum;
            float x = pad;
            for (KeyDef k : row) {
                k.rect.set(x + gap / 2, pad + i * rowH + gap / 2,
                        x + k.w * unit - gap / 2, pad + (i + 1) * rowH - gap / 2);
                x += k.w * unit;
            }
        }
        laidOut = true;
    }

    private Ptr ptrFor(KeyDef k) {
        for (int i = 0; i < ptrs.size(); i++) if (ptrs.valueAt(i).key == k) return ptrs.valueAt(i);
        return null;
    }

    private void drawCentered(Canvas c, String s, float cx, float cy, Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(BG);
        if (rows == null) return;
        if (!laidOut) layoutKeys();
        float rad = 7 * dp;
        for (List<KeyDef> row : rows) {
            for (KeyDef k : row) {
                if (k.spacer) continue;
                RectF r = k.rect;
                Ptr p = ptrFor(k);
                keyPaint.setColor(SHADOW);
                canvas.drawRoundRect(r.left, r.top + 1.5f * dp, r.right, r.bottom + 1.5f * dp, rad, rad, keyPaint);
                int col = k.func ? FUNC : KEY;
                if (k.isShift && shift > 0) col = SHIFT_ON;
                if (p != null) col = PRESSED;
                keyPaint.setColor(col);
                canvas.drawRoundRect(r, rad, rad, keyPaint);

                float cx = r.centerX(), cy = r.centerY();
                if (p != null && p.dir.length() > 0 && k.has(p.dir)) {
                    textPaint.setColor(ACCENT);
                    textPaint.setFakeBoldText(true);
                    textPaint.setTextSize(Math.min(r.height() * 0.55f, 30 * dp));
                    drawCentered(canvas, caseOf(k, k.display(p.dir)), cx, cy, textPaint);
                    textPaint.setFakeBoldText(false);
                    continue;
                }

                if (k.icon != null) { drawIcon(canvas, k, r); continue; }

                String main = k.backKey ? backLabel : k.label;
                main = caseOf(k, main);
                if (k.below != null) {
                    // 영어 키: 글자는 위쪽, 기호는 아래쪽에 작게
                    textPaint.setColor(TEXT);
                    textPaint.setTextSize(Math.min(r.height() * 0.40f, 26 * dp));
                    drawCentered(canvas, main, cx, r.top + r.height() * 0.40f, textPaint);
                    float bs = Math.min(r.height() * 0.17f, 12 * dp);
                    subPaint.setTextSize(bs);
                    drawCentered(canvas, k.below, cx, r.top + r.height() * 0.78f, subPaint);
                    continue;
                }
                textPaint.setColor(TEXT);
                float big = main.length() <= 1 ? Math.min(r.height() * 0.44f, 26 * dp)
                        : Math.min(r.height() * 0.32f, 19 * dp);
                textPaint.setTextSize(big);
                drawCentered(canvas, main, cx, cy, textPaint);

                if (!k.showSubs) continue;
                float s = Math.min(r.height() * 0.19f, 12 * dp);
                subPaint.setTextSize(s);
                if (k.has("U")) drawCentered(canvas, k.display("U"), cx, r.top + s * 0.85f, subPaint);
                if (k.has("D")) drawCentered(canvas, k.display("D"), cx, r.bottom - s * 0.85f, subPaint);
                if (k.has("L") && r.width() > r.height() * 1.2f)
                    drawCentered(canvas, k.display("L"), r.left + s * 1.1f, cy, subPaint);
                if (k.has("R") && r.width() > r.height() * 1.2f)
                    drawCentered(canvas, k.display("R"), r.right - s * 1.1f, cy, subPaint);
            }
        }
    }

    private void drawIcon(Canvas c, KeyDef k, RectF r) {
        float cx = r.centerX(), cy = r.centerY();
        float u = Math.min(r.height(), r.width()) * 0.16f;   // 아이콘 기준 크기
        iconPaint.setStyle(Paint.Style.FILL);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        switch (k.icon) {
            case "DEL": {
                float w = u * 2.6f, h = u * 1.7f;
                path.reset();
                path.moveTo(cx - w / 2, cy);
                path.lineTo(cx - w / 2 + h * 0.55f, cy - h / 2);
                path.lineTo(cx + w / 2, cy - h / 2);
                path.lineTo(cx + w / 2, cy + h / 2);
                path.lineTo(cx - w / 2 + h * 0.55f, cy + h / 2);
                path.close();
                iconPaint.setColor(ICON);
                c.drawPath(path, iconPaint);
                iconPaint.setColor(0xFFFFFFFF);
                iconPaint.setStyle(Paint.Style.STROKE);
                iconPaint.setStrokeWidth(u * 0.28f);
                float xc = cx + w * 0.12f, d = h * 0.22f;
                c.drawLine(xc - d, cy - d, xc + d, cy + d, iconPaint);
                c.drawLine(xc - d, cy + d, xc + d, cy - d, iconPaint);
                break;
            }
            case "GLOBE": {
                float rad = u * 1.35f;
                iconPaint.setColor(0xFF8A9299);
                iconPaint.setStyle(Paint.Style.STROKE);
                iconPaint.setStrokeWidth(u * 0.22f);
                c.drawCircle(cx, cy, rad, iconPaint);
                c.drawOval(cx - rad * 0.45f, cy - rad, cx + rad * 0.45f, cy + rad, iconPaint);
                c.drawLine(cx, cy - rad, cx, cy + rad, iconPaint);
                c.drawLine(cx - rad, cy, cx + rad, cy, iconPaint);
                float yo = rad * 0.5f, xo = (float) Math.sqrt(rad * rad - yo * yo);
                c.drawLine(cx - xo, cy - yo, cx + xo, cy - yo, iconPaint);
                c.drawLine(cx - xo, cy + yo, cx + xo, cy + yo, iconPaint);
                break;
            }
            case "ENTER": {
                iconPaint.setColor(0xFFFFFFFF);
                iconPaint.setStyle(Paint.Style.STROKE);
                iconPaint.setStrokeWidth(u * 0.32f);
                if (enterSearch) {
                    float rad = u * 0.95f;
                    float ox = cx - u * 0.35f, oy = cy - u * 0.35f;
                    c.drawCircle(ox, oy, rad, iconPaint);
                    float o = rad * 0.72f;
                    c.drawLine(ox + o, oy + o, ox + o + u * 1.1f, oy + o + u * 1.1f, iconPaint);
                } else {
                    float x1 = cx + u * 1.3f, x0 = cx - u * 1.3f, yb = cy + u * 0.4f;
                    path.reset();
                    path.moveTo(x1, cy - u * 1.0f);
                    path.lineTo(x1, yb);
                    path.lineTo(x0, yb);
                    c.drawPath(path, iconPaint);
                    c.drawLine(x0, yb, x0 + u * 0.7f, yb - u * 0.7f, iconPaint);
                    c.drawLine(x0, yb, x0 + u * 0.7f, yb + u * 0.7f, iconPaint);
                }
                break;
            }
            case "SHIFT": {
                iconPaint.setColor(shift > 0 ? 0xFF00897B : ICON);
                float w = u * 1.4f, top = cy - u * 1.3f, mid = cy, bot = cy + u * 1.0f;
                path.reset();
                path.moveTo(cx, top);
                path.lineTo(cx + w, mid);
                path.lineTo(cx + w * 0.45f, mid);
                path.lineTo(cx + w * 0.45f, bot);
                path.lineTo(cx - w * 0.45f, bot);
                path.lineTo(cx - w * 0.45f, mid);
                path.lineTo(cx - w, mid);
                path.close();
                c.drawPath(path, iconPaint);
                if (shift == 2) c.drawRect(cx - w * 0.6f, bot + u * 0.35f, cx + w * 0.6f, bot + u * 0.65f, iconPaint);
                break;
            }
        }
    }

    private String caseOf(KeyDef k, String s) {
        if (k.letter && shift > 0 && s.length() == 1 && Character.isLetter(s.charAt(0)))
            return s.toUpperCase();
        return s;
    }

    private KeyDef findKey(float x, float y) {
        if (rows == null) return null;
        if (!laidOut) layoutKeys();
        KeyDef best = null;
        float bestD = Float.MAX_VALUE;
        for (List<KeyDef> row : rows) {
            for (KeyDef k : row) {
                if (k.spacer) continue;
                if (k.rect.contains(x, y)) return k;
                float dx = Math.max(Math.max(k.rect.left - x, 0), x - k.rect.right);
                float dy = Math.max(Math.max(k.rect.top - y, 0), y - k.rect.bottom);
                float d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; best = k; }
            }
        }
        return best;
    }

    private static String dirOf(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? "R" : "L";
        return dy > 0 ? "D" : "U";
    }

    private float threshold(KeyDef k) {
        return Math.max(14 * dp, k.rect.height() * 0.28f);
    }

    private void emit(String t) {
        if (listener != null && t != null && !t.isEmpty()) listener.onOutput(t);
    }

    private void release(Ptr p) {
        if (p.key.repeat) { stopRepeat(); return; }
        if ((p.key.space || p.key.selDrag) && p.spaceDrag) return;
        emit(p.key.outFor(p.dir));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (rows == null) return true;
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // 빠르게 칠 때: 이전 손가락 입력을 먼저 확정해서 순서 보장
                List<Ptr> old = new ArrayList<>();
                for (int i = 0; i < ptrs.size(); i++) old.add(ptrs.valueAt(i));
                ptrs.clear();
                for (Ptr o : old) release(o);

                float x = e.getX(idx), y = e.getY(idx);
                KeyDef k = findKey(x, y);
                if (k == null) break;
                Ptr p = new Ptr();
                p.key = k; p.x0 = x; p.y0 = y; p.lastX = x;
                ptrs.put(e.getPointerId(idx), p);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (k.repeat) { emit(k.tap); startRepeat(k.tap); }
                invalidate();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    Ptr p = ptrs.get(e.getPointerId(i));
                    if (p == null) continue;
                    float x = e.getX(i), y = e.getY(i);
                    if (p.key.space) {
                        // 스페이스바 좌우로 밀면 커서 이동
                        float step = 12 * dp;
                        if (!p.spaceDrag && Math.abs(x - p.x0) < 18 * dp) continue;
                        while (x - p.lastX > step) { emit("@RIGHT"); p.lastX += step; p.spaceDrag = true; }
                        while (p.lastX - x > step) { emit("@LEFT"); p.lastX -= step; p.spaceDrag = true; }
                        continue;
                    }
                    if (p.key.selDrag) {
                        // sel 키: 왼쪽으로 끌수록 커서 앞 글자를 더 많이 선택
                        float step = 14 * dp;
                        if (!p.selStarted && Math.abs(x - p.x0) < 16 * dp) continue;
                        if (!p.selStarted) { p.selStarted = true; p.spaceDrag = true; emit("@SELBEGIN"); }
                        int want = (int) Math.max(0, (p.x0 - x) / step);
                        while (p.selSteps < want) { emit("@SELMORE"); p.selSteps++; }
                        while (p.selSteps > want) { emit("@SELLESS"); p.selSteps--; }
                        invalidate();
                        continue;
                    }
                    if (p.key.repeat) continue;
                    float dx = x - p.x0, dy = y - p.y0;
                    float d = (float) Math.hypot(dx, dy);
                    if (d > p.maxD) {
                        p.maxD = d;
                        String nd = d > threshold(p.key) ? dirOf(dx, dy) : "";
                        if (!nd.equals(p.dir)) {
                            p.dir = nd;
                            if (!nd.isEmpty() && p.key.has(nd))
                                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            invalidate();
                        }
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int id = e.getPointerId(idx);
                Ptr p = ptrs.get(id);
                if (p != null) {
                    ptrs.remove(id);
                    release(p);
                }
                invalidate();
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                ptrs.clear();
                stopRepeat();
                invalidate();
                break;
        }
        return true;
    }

    private void startRepeat(final String t) {
        stopRepeat();
        repeater = new Runnable() {
            @Override public void run() {
                emit(t);
                handler.postDelayed(this, 50);
            }
        };
        handler.postDelayed(repeater, 400);
    }

    private void stopRepeat() {
        if (repeater != null) handler.removeCallbacks(repeater);
        repeater = null;
    }
}
