package com.lig7oo7.gutgi;

import android.content.Context;
import android.content.res.Configuration;

/** 밝은/어두운 테마 색과 키 높이. 앱 설정에서 바꾸면 키보드가 다시 그려진다 */
public class Theme {
    public static boolean dark;
    public static float keyScale = 1f;

    public static int BG, KEY, FUNC, PRESSED, SHADOW, TEXT, SUB, ACCENT, SHIFT_ON, ICON,
            CHIP, ACTIVE, PINNED, PINNED_CLIP, PANEL_FUNC, ICON_LINE;

    static { apply(false); }

    /** @return 바뀌었으면 true */
    public static boolean load(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("gutgi", Context.MODE_PRIVATE);
        int mode = sp.getInt("darkMode", 2);   // 0 밝게, 1 어둡게, 2 시스템 따라
        boolean d = mode == 1 || (mode == 2 && (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        float s = sp.getInt("keyHeight", 100) / 100f;
        boolean changed = d != dark || s != keyScale;
        keyScale = s;
        apply(d);
        return changed;
    }

    public static void apply(boolean d) {
        dark = d;
        if (!d) {
            BG = 0xFFE8EBEF; KEY = 0xFFFFFFFF; FUNC = 0xFFD3D7DC; PRESSED = 0xFFCBD5E1; SHADOW = 0xFFBFC5CC;
            TEXT = 0xFF37474F; SUB = 0xFF7D8892; ACCENT = 0xFFE0245E; SHIFT_ON = 0xFF9FD8CF; ICON = 0xFF6F767C;
            CHIP = 0xFFFFFFFF; ACTIVE = 0xFFB2DFDB; PINNED = 0xFFE0F2F1; PINNED_CLIP = 0xFFFFF8E1;
            PANEL_FUNC = 0xFFD3D7DC; ICON_LINE = 0xFF455A64;
        } else {
            BG = 0xFF1B1E21; KEY = 0xFF3A3F45; FUNC = 0xFF2A2E33; PRESSED = 0xFF56606B; SHADOW = 0xFF101214;
            TEXT = 0xFFE8EAED; SUB = 0xFF9AA0A6; ACCENT = 0xFFFF6B9A; SHIFT_ON = 0xFF00796B; ICON = 0xFFC3C9CF;
            CHIP = 0xFF3A3F45; ACTIVE = 0xFF00695C; PINNED = 0xFF27453F; PINNED_CLIP = 0xFF4A4428;
            PANEL_FUNC = 0xFF2A2E33; ICON_LINE = 0xFFC3C9CF;
        }
    }
}
