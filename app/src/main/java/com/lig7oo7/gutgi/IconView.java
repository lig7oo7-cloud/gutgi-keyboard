package com.lig7oo7.gutgi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 복사·잘라내기·붙여넣기 아이콘을 직접 그린다 (폰 글꼴에 상관없이 똑같이 보이게) */
public class IconView extends View {
    public static final int COPY = 0, CUT = 1, PASTE = 2;

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public IconView(Context c, int kind) {
        super(c);
        this.kind = kind;
        p.setColor(0xFF455A64);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        String[] desc = {"복사", "잘라내기", "붙여넣기"};
        setContentDescription(desc[kind]);
    }

    @Override
    protected void onDraw(Canvas c) {
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
        }
    }
}
