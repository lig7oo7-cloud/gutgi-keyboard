package com.lig7oo7.gutgi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 복사·잘라내기·붙여넣기 아이콘을 직접 그린다 (폰 글꼴에 상관없이 똑같이 보이게) */
public class IconView extends View {
    public static final int COPY = 0, CUT = 1, PASTE = 2, GEMINI = 3, HISTORY = 4, PHRASE = 5, SHOT = 6, MIC = 7, UNDO = 8;

    private final int kind;
    private boolean active;   // 마이크 듣는 중 (빨간색)

    public void setActive(boolean a) { active = a; invalidate(); }
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public IconView(Context c, int kind) {
        super(c);
        this.kind = kind;
        p.setColor(Theme.ICON_LINE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        String[] desc = {"복사", "잘라내기", "붙여넣기", "Gemini", "복사 기록", "상용구", "화면 캡처", "음성 입력", "되돌리기"};
        setContentDescription(desc[kind]);
    }

    @Override
    protected void onDraw(Canvas c) {
        p.setColor(Theme.ICON_LINE);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float u = Math.min(getWidth(), getHeight()) * 0.11f;
        p.setStrokeWidth(u * 0.55f);
        p.setStyle(Paint.Style.STROKE);
        switch (kind) {
            case COPY: {
                // 겹친 두 장
                r.set(cx - u * 2.2f, cy - u * 1.2f, cx + u * 0.8f, cy + u * 2.6f);
                c.drawRoundRect(r, u * 0.5f, u * 0.5f, p);
                c.drawLine(cx - u * 1.2f, cy - u * 2.2f, cx + u * 1.6f, cy - u * 2.2f, p);
                c.drawLine(cx + u * 1.8f, cy - u * 2.0f, cx + u * 1.8f, cy + u * 1.6f, p);
                break;
            }
            case CUT: {
                // 가위
                float ry = cy + u * 1.6f, rr = u * 0.9f;
                c.drawCircle(cx - u * 1.3f, ry, rr, p);
                c.drawCircle(cx + u * 1.3f, ry, rr, p);
                c.drawLine(cx - u * 0.7f, ry - rr * 0.8f, cx + u * 1.2f, cy - u * 2.6f, p);
                c.drawLine(cx + u * 0.7f, ry - rr * 0.8f, cx - u * 1.2f, cy - u * 2.6f, p);
                break;
            }
            case PASTE: {
                // 클립보드 + 줄
                r.set(cx - u * 2.0f, cy - u * 2.0f, cx + u * 2.0f, cy + u * 2.6f);
                c.drawRoundRect(r, u * 0.5f, u * 0.5f, p);
                p.setStyle(Paint.Style.FILL);
                r.set(cx - u * 1.0f, cy - u * 2.7f, cx + u * 1.0f, cy - u * 1.6f);
                c.drawRoundRect(r, u * 0.3f, u * 0.3f, p);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - u * 1.1f, cy - u * 0.4f, cx + u * 1.1f, cy - u * 0.4f, p);
                c.drawLine(cx - u * 1.1f, cy + u * 0.7f, cx + u * 1.1f, cy + u * 0.7f, p);
                c.drawLine(cx - u * 1.1f, cy + u * 1.8f, cx + u * 0.3f, cy + u * 1.8f, p);
                break;
            }
            case GEMINI: {
                // 네 갈래 반짝이 별 (파랑)
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF4285F4);
                android.graphics.Path s = new android.graphics.Path();
                float R = u * 3.0f, k = u * 0.45f;
                s.moveTo(cx, cy - R);
                s.quadTo(cx + k, cy - k, cx + R, cy);
                s.quadTo(cx + k, cy + k, cx, cy + R);
                s.quadTo(cx - k, cy + k, cx - R, cy);
                s.quadTo(cx - k, cy - k, cx, cy - R);
                c.drawPath(s, p);
                p.setColor(Theme.ICON_LINE);
                break;
            }
            case UNDO: {
                // 왼쪽으로 휘어 돌아가는 화살표
                r.set(cx - u * 2.0f, cy - u * 1.6f, cx + u * 2.4f, cy + u * 2.4f);
                c.drawArc(r, 200, 250, false, p);
                float ax = cx - u * 1.85f, ay = cy - u * 0.55f;
                c.drawLine(ax, ay, ax - u * 0.2f, ay - u * 1.5f, p);
                c.drawLine(ax, ay, ax + u * 1.4f, ay - u * 0.5f, p);
                break;
            }
            case MIC: {
                // 마이크
                int col = active ? 0xFFE53935 : Theme.ICON_LINE;
                p.setColor(col);
                p.setStyle(Paint.Style.FILL);
                r.set(cx - u * 1.0f, cy - u * 2.7f, cx + u * 1.0f, cy + u * 0.6f);
                c.drawRoundRect(r, u, u, p);
                p.setStyle(Paint.Style.STROKE);
                r.set(cx - u * 1.9f, cy - u * 1.5f, cx + u * 1.9f, cy + u * 1.5f);
                c.drawArc(r, 0, 180, false, p);
                c.drawLine(cx, cy + u * 1.5f, cx, cy + u * 2.6f, p);
                p.setColor(Theme.ICON_LINE);
                break;
            }
            case SHOT: {
                // 캡처: 네 귀퉁이 꺾쇠 + 가운데 점
                float h = u * 2.6f, k = u * 1.1f;
                c.drawLine(cx - h, cy - h, cx - h + k, cy - h, p); c.drawLine(cx - h, cy - h, cx - h, cy - h + k, p);
                c.drawLine(cx + h, cy - h, cx + h - k, cy - h, p); c.drawLine(cx + h, cy - h, cx + h, cy - h + k, p);
                c.drawLine(cx - h, cy + h, cx - h + k, cy + h, p); c.drawLine(cx - h, cy + h, cx - h, cy + h - k, p);
                c.drawLine(cx + h, cy + h, cx + h - k, cy + h, p); c.drawLine(cx + h, cy + h, cx + h, cy + h - k, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, u * 0.8f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            }
            case HISTORY: {
                // 시계 (복사 기록)
                c.drawCircle(cx, cy, u * 2.6f, p);
                c.drawLine(cx, cy, cx, cy - u * 1.6f, p);
                c.drawLine(cx, cy, cx + u * 1.2f, cy + u * 0.7f, p);
                break;
            }
            case PHRASE: {
                // 말풍선 + 줄 (상용구)
                android.graphics.Path b = new android.graphics.Path();
                r.set(cx - u * 2.8f, cy - u * 2.2f, cx + u * 2.8f, cy + u * 1.4f);
                b.addRoundRect(r, u * 0.8f, u * 0.8f, android.graphics.Path.Direction.CW);
                c.drawPath(b, p);
                c.drawLine(cx - u * 1.2f, cy + u * 1.4f, cx - u * 1.8f, cy + u * 2.8f, p);
                c.drawLine(cx - u * 1.8f, cy + u * 2.8f, cx - u * 0.2f, cy + u * 1.4f, p);
                c.drawLine(cx - u * 1.6f, cy - u * 0.9f, cx + u * 1.6f, cy - u * 0.9f, p);
                c.drawLine(cx - u * 1.6f, cy + u * 0.2f, cx + u * 0.6f, cy + u * 0.2f, p);
                break;
            }
        }
    }
}
