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
        List<String> l = clips(c);
        l.remove(text);
        l.add(0, text);
        while (l.size() > CLIP_MAX) l.remove(l.size() - 1);
        save(c, "clips", l);
    }

    public static List<String> phrases(Context c) {
        if (!sp(c).contains("phrases")) {
            List<String> d = new ArrayList<>();
            d.add("지금 운전 중이라 이따 연락드릴게요.");
            d.add("감사합니다!");
            d.add("네 알겠습니다.");
            save(c, "phrases", d);
        }
        return load(c, "phrases");
    }
}
