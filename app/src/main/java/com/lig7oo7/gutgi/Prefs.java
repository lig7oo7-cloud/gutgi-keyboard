package com.lig7oo7.gutgi;

import android.content.Context;
import android.content.SharedPreferences;

/** 옵션창에서 바꾸는 값들 (키보드가 열릴 때마다 다시 읽음) */
public class Prefs {
    // 환경설정
    public static boolean vib = true, vibSlide = true, sound = false, doubleSpace = false, autoCap = true,
            globeCycle = false;
    public static int vibMs = 0;          // 0 = 시스템 기본
    public static int soundVol = 50;
    // 크기·자판
    public static int portWidth = 100, portAlign = 1;           // 0 왼쪽 1 가운데 2 오른쪽
    public static int landHeight = 42, landWidth = 100, landAlign = 1;   // 높이 = 화면 높이의 %
    public static boolean landQwerty = false, landSplit = true, landSplitSpace = true;
    public static int sensV = 50, sensH = 50;                    // 굿기 민감도 (작을수록 짧게 밀어도 인식)
    // 툴바
    public static boolean toolbar = true;
    public static final String[] TOOLS = {"gemini", "history", "target", "naver", "shot", "mic", "copy", "cut", "paste", "undo"};
    public static final String[] TOOL_NAMES = {"Gemini", "복사 기록·고정 문구", "상황어", "네이버 검색", "화면 캡처", "음성 입력", "복사", "잘라내기", "붙여넣기", "되돌리기"};
    public static final boolean[] tool = new boolean[TOOLS.length];

    private static String sig = "";

    public static SharedPreferences sp(Context c) { return c.getSharedPreferences("gutgi", Context.MODE_PRIVATE); }

    /** @return 키보드 모양이 바뀌어 다시 만들어야 하면 true */
    public static boolean load(Context c) {
        SharedPreferences p = sp(c);
        vib = p.getBoolean("vib", true);
        vibSlide = p.getBoolean("vibSlide", true);
        vibMs = p.getInt("vibMs", 0);
        sound = p.getBoolean("sound", false);
        soundVol = p.getInt("soundVol", 50);
        doubleSpace = p.getBoolean("doubleSpace", false);
        autoCap = p.getBoolean("autoCap", true);
        globeCycle = p.getBoolean("globeCycle", false);
        portWidth = p.getInt("portWidth", 100);
        portAlign = p.getInt("portAlign", 1);
        landHeight = p.getInt("landHeight", 42);
        landWidth = p.getInt("landWidth", 100);
        landAlign = p.getInt("landAlign", 1);
        landQwerty = p.getBoolean("landQwerty", false);
        landSplit = p.getBoolean("landSplit", true);
        landSplitSpace = p.getBoolean("landSplitSpace", true);
        sensV = p.getInt("sensV", 50);
        sensH = p.getInt("sensH", 50);
        toolbar = p.getBoolean("toolbar", true);
        StringBuilder s = new StringBuilder();
        s.append(portWidth).append(portAlign).append(landHeight).append(landWidth).append(landAlign)
                .append(landQwerty).append(landSplit).append(landSplitSpace).append(toolbar);
        for (int i = 0; i < TOOLS.length; i++) {
            tool[i] = p.getBoolean("tb_" + TOOLS[i], true);
            s.append(tool[i] ? '1' : '0');
        }
        boolean changed = !s.toString().equals(sig);
        sig = s.toString();
        return changed;
    }

    /** 단축어: "단축어\t문구" 목록 */
    public static java.util.Map<String, String> shortcuts(Context c) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String e : Store.load(c, "shortcuts")) {
            int i = e.indexOf('\t');
            if (i > 0) m.put(e.substring(0, i), e.substring(i + 1));
        }
        return m;
    }
}
