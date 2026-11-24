package com.lig7oo7.gutgi;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** 클립보드 기록과 상용구 저장 (휴대폰 안에만 저장) */
public class Store {
    private static final String PREF = "gutgi";
    public static final int CLIP_MAX = 30;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<String> load(Context c, String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    public static void save(Context c, String key, List<String> list) {
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        sp(c).edit().putString(key, a.toString()).apply();
    }

    public static List<String> clips(Context c) { return load(c, "clips"); }

    public static void addClip(Context c, String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (pins(c).contains(text)) return;   // 고정된 건 기록에 중복으로 안 쌓음
        List<String> l = clips(c);
        l.remove(text);
        l.add(0, text);
        while (l.size() > CLIP_MAX) l.remove(l.size() - 1);
        save(c, "clips", l);
    }

    /** 📌 고정 항목 (예전 상용구). 처음엔 기존 상용구를 그대로 옮겨온다 */
    public static List<String> pins(Context c) {
        SharedPreferences p = sp(c);
        if (!p.contains("pins")) {
            List<String> init = new ArrayList<>();
            if (p.contains("phrases")) init.addAll(load(c, "phrases"));
            else {
                init.add("지금 운전 중이라 이따 연락드릴게요.");
                init.add("감사합니다!");
                init.add("네 알겠습니다.");
            }
            save(c, "pins", init);
        }
        return load(c, "pins");
    }

    /** 핀 고정/해제: 고정하면 기록에서 빠지고 위로, 해제하면 기록 맨 위로 */
    public static void togglePin(Context c, String text) {
        List<String> pins = pins(c);
        List<String> clips = clips(c);
        if (pins.remove(text)) {
            clips.remove(text);
            clips.add(0, text);
            while (clips.size() > CLIP_MAX) clips.remove(clips.size() - 1);
        } else {
            pins.add(0, text);
            clips.remove(text);
        }
        save(c, "pins", pins);
        save(c, "clips", clips);
    }
}
