package com.lig7oo7.gutgi;

import android.graphics.RectF;

/** 키 하나: 탭 출력 + 위/아래/왼쪽/오른쪽 굿기 출력 */
public class KeyDef {
    public String label, tap, up, down, left, right;
    public float w = 1f;
    public boolean func, repeat, space, spacer, letter, isShift, backKey, selDrag, showSubs = true;
    public String icon;   // DEL, ENTER, GLOBE, SHIFT
    public String below;  // 영어 키: 글자 아래 작은 기호
    public String longTap;  // 길게 누르면 (예: 지구본 → 옵션창)
    public String shifted;  // 쿼티 한글: 시프트 때 보일 글자 (ㅂ→ㅃ)
    public final RectF rect = new RectF();

    public KeyDef(String label, String tap) {
        this.label = label;
        this.tap = tap;
    }

    public KeyDef dirs(String up, String down, String left, String right) {
        this.up = up; this.down = down; this.left = left; this.right = right;
        return this;
    }

    public KeyDef w(float w) { this.w = w; return this; }
    public KeyDef func() { this.func = true; return this; }
    public KeyDef letter() { this.letter = true; return this; }
    public KeyDef noSubs() { this.showSubs = false; return this; }
    public KeyDef icon(String i) { this.icon = i; return this; }

    public static KeyDef spacer(float w) {
        KeyDef k = new KeyDef("", "");
        k.spacer = true;
        k.w = w;
        return k;
    }

    public String raw(String dir) {
        switch (dir) {
            case "U": return up;
            case "D": return down;
            case "L": return left;
            case "R": return right;
            default: return tap;
        }
    }

    public boolean has(String dir) {
        String s = raw(dir);
        return s != null && !s.isEmpty();
    }

    public String outFor(String dir) {
        return has(dir) ? raw(dir) : tap;
    }

    /** 화면에 보여줄 글자 (특수 토큰은 키 라벨로) */
    public String display(String dir) {
        String s = outFor(dir);
        if (s == null || s.isEmpty()) return label;
        if (s.length() > 1 && s.charAt(0) == '@') return label;
        return s;
    }
}
