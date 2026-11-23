package com.lig7oo7.gutgi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 자판 배열 정의. 순서: dirs(위, 아래, 왼쪽, 오른쪽) */
public class Layouts {

    private static List<KeyDef> row(KeyDef... k) { return new ArrayList<>(Arrays.asList(k)); }

    private static KeyDef del(float w) {
        KeyDef k = new KeyDef("⌫", "@DEL").w(w).func();
        k.repeat = true;
        return k;
    }

    private static KeyDef space(float w) {
        KeyDef k = new KeyDef("", "@SPACE").w(w);
        k.space = true;
        return k;
    }

    private static KeyDef enter(float w) { return new KeyDef("↵", "@ENTER").w(w).func(); }

    public static List<List<KeyDef>> hangul() {
        List<List<KeyDef>> r = new ArrayList<>();
        r.add(row(
                new KeyDef("ㄱ", "ㄱ").dirs("ㄲ", "#", "ㅋ", "ㅋ").w(2),
                new KeyDef("ㄴ", "ㄴ").dirs("ㄸ", "ㄷ", "ㅌ", "ㅌ").w(2),
                // 겹모음 키: 탭 ㅢ, →ㅘ ←ㅝ ↑ㅚ ↓ㅟ
                new KeyDef("ㅢ", "ㅢ").dirs("ㅚ", "ㅟ", "ㅝ", "ㅘ").w(2),
                del(1)));
        r.add(row(
                new KeyDef("ㄹ", "ㄹ").dirs("^", "_", "=", "-").w(2),
                new KeyDef("ㅁ", "ㅁ").dirs("ㅃ", "ㅂ", "ㅍ", "ㅍ").w(2),
                // 점 하나 모음: 탭 ㅣ, →ㅏ ←ㅓ ↑ㅗ ↓ㅜ
                new KeyDef("ㅣ·", "ㅣ").dirs("ㅗ", "ㅜ", "ㅓ", "ㅏ").w(2),
                new KeyDef("?", "?").dirs("!", "*", "+", "").w(1)));
        r.add(row(
                new KeyDef("ㅅ", "ㅅ").dirs("ㅆ", "2", "1", "3").w(2),
                new KeyDef("ㅇ", "ㅇ").dirs("♥", "5", "4", "6").w(2),
                // 작대기 둘 모음: 탭 ㅡ, →ㅐ ←ㅔ ↑ㅙ ↓ㅞ
                new KeyDef("ㅡ‖", "ㅡ").dirs("ㅙ", "ㅞ", "ㅔ", "ㅐ").w(2),
                new KeyDef(".", ".").dirs("\"", "'", ",", "").w(1)));
        r.add(row(
                new KeyDef("ㅈ", "ㅈ").dirs("ㅉ", "~", "ㅊ", "ㅊ").w(2),
                new KeyDef("ㅎ", "ㅎ").dirs("0", "8", "7", "9").w(2),
                // 점 둘 모음: →ㅑ ←ㅕ ↑ㅛ ↓ㅠ
                new KeyDef("··", "").dirs("ㅛ", "ㅠ", "ㅕ", "ㅑ").w(2),
                new KeyDef("/", "/").dirs(":", ";", "@", "").w(1)));
        r.add(row(
                new KeyDef("?123", "@SYM").w(1).func().noSubs(),
                new KeyDef("A", "@LANG").w(0.75f).func().noSubs(),
                space(3.5f),
                new KeyDef("ㅋㅋㅋ", "ㅋㅋㅋ").dirs("ㅎㅎㅎ", "ㅠㅠ", "^^", ";;").w(0.75f).noSubs(),
                enter(1)));
        return r;
    }

    public static List<List<KeyDef>> english() {
        List<List<KeyDef>> r = new ArrayList<>();
        String top = "qwertyuiop", nums = "1234567890";
        List<KeyDef> r1 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String c = String.valueOf(top.charAt(i));
            r1.add(new KeyDef(c, c).dirs(String.valueOf(nums.charAt(i)), null, null, null).letter());
        }
        r.add(r1);
        String mid = "asdfghjkl";
        String[] midUp = {"@", "#", "/", "*", "~", "_", "+", "<", "♥"};
        String[] midDn = {null, "%", null, null, "^", "-", "=", ">", null};
        List<KeyDef> r2 = new ArrayList<>();
        r2.add(KeyDef.spacer(0.5f));
        for (int i = 0; i < 9; i++) {
            String c = String.valueOf(mid.charAt(i));
            r2.add(new KeyDef(c, c).dirs(midUp[i], midDn[i], null, null).letter());
        }
        r2.add(KeyDef.spacer(0.5f));
        r.add(r2);
        String bot = "zxcvbnm";
        String[] botUp = {"(", "[", ";", "'", "&", "!", "?"};
        String[] botDn = {")", "]", ":", "\"", "|", null, null};
        List<KeyDef> r3 = new ArrayList<>();
        KeyDef shift = new KeyDef("⇧", "@SHIFT").w(1.5f).func();
        shift.isShift = true;
        r3.add(shift);
        for (int i = 0; i < 7; i++) {
            String c = String.valueOf(bot.charAt(i));
            r3.add(new KeyDef(c, c).dirs(botUp[i], botDn[i], null, null).letter());
        }
        r3.add(del(1.5f));
        r.add(r3);
        r.add(row(
                new KeyDef("?123", "@SYM").w(1.5f).func().noSubs(),
                new KeyDef("가", "@LANG").w(1).func().noSubs(),
                space(5),
                new KeyDef(".", ".").dirs(",", "?", null, null).w(1),
                enter(1.5f)));
        return r;
    }

    public static List<List<KeyDef>> symbols() {
        List<List<KeyDef>> r = new ArrayList<>();
        String[] lines = {"1234567890", "!@#$%^&*()", "-_=+/\\:;'\""};
        for (String line : lines) {
            List<KeyDef> row = new ArrayList<>();
            for (int i = 0; i < line.length(); i++) {
                String c = String.valueOf(line.charAt(i));
                row.add(new KeyDef(c, c));
            }
            r.add(row);
        }
        List<KeyDef> r4 = new ArrayList<>();
        for (String c : new String[]{",", ".", "?", "~", "<", ">", "[", "]"}) r4.add(new KeyDef(c, c));
        r4.add(del(2));
        r.add(r4);
        r.add(row(
                new KeyDef("가/A", "@SYM").w(1.5f).func().noSubs(),
                new KeyDef("₩", "₩").w(1),
                space(5),
                new KeyDef("…", "…").w(1),
                enter(1.5f)));
        return r;
    }
}
