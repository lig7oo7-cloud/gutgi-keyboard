package com.lig7oo7.gutgi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 자판 배열 정의. 순서: dirs(위, 아래, 왼쪽, 오른쪽) */
public class Layouts {

    private static List<KeyDef> row(KeyDef... k) { return new ArrayList<>(Arrays.asList(k)); }

    private static KeyDef del(float w) {
        KeyDef k = new KeyDef("", "@DEL").w(w).func().icon("DEL");
        k.repeat = true;
        return k;
    }

    private static KeyDef space(float w) {
        KeyDef k = new KeyDef("", "@SPACE").w(w);
        k.space = true;
        return k;
    }

    private static KeyDef enter(float w) { return new KeyDef("", "@ENTER").w(w).func().icon("ENTER"); }

    private static KeyDef globe(float w) { return new KeyDef("", "@LANG").w(w).noSubs().icon("GLOBE"); }

    /** 오른쪽 기호 열 (한글·기호 자판 공통) */
    private static KeyDef question() { return new KeyDef("?", "?").dirs("!", "*", "+", "").w(1); }
    private static KeyDef period() { return new KeyDef(".", ".").dirs("\"", "'", ",", "").w(1); }
    private static KeyDef slash() { return new KeyDef("/", "/").dirs(":", ";", "@", "").w(1); }

    public static List<List<KeyDef>> hangul() {
        List<List<KeyDef>> r = new ArrayList<>();
        r.add(row(
                new KeyDef("ㄱ", "ㄱ").dirs("ㄲ", "#", "ㅋ", "ㅋ").w(2),
                new KeyDef("ㄴ", "ㄴ").dirs("ㄸ", "ㄷ", "ㅌ", "ㅌ").w(2),
                // 겹모음: 탭 ㅢ, →ㅘ ←ㅝ ↑ㅚ ↓ㅟ
                new KeyDef("ㅢ", "ㅢ").dirs("ㅚ", "ㅟ", "ㅝ", "ㅘ").w(2).noSubs(),
                del(1)));
        r.add(row(
                new KeyDef("ㄹ", "ㄹ").dirs("^", "_", "=", "-").w(2),
                new KeyDef("ㅁ", "ㅁ").dirs("ㅃ", "ㅂ", "ㅍ", "ㅍ").w(2),
                // 점 하나: 탭 ㅣ, →ㅏ ←ㅓ ↑ㅗ ↓ㅜ  (ㅏ 다음 탭 → ㅑ)
                new KeyDef("ㅣ·", "ㅣ").dirs("ㅗ", "ㅜ", "ㅓ", "ㅏ").w(2).noSubs(),
                question()));
        r.add(row(
                new KeyDef("ㅅ", "ㅅ").dirs("ㅆ", "2", "1", "3").w(2),
                new KeyDef("ㅇ", "ㅇ").dirs("❤️", "5", "4", "6").w(2),
                // 작대기 둘: 탭 ㅡ, →ㅐ ←ㅔ ↑ㅙ ↓ㅞ  (ㅐ 다음 탭 → ㅒ, ㅔ 다음 탭 → ㅖ)
                new KeyDef("ㅡ‖", "ㅡ").dirs("ㅙ", "ㅞ", "ㅔ", "ㅐ").w(2).noSubs(),
                period()));
        r.add(row(
                new KeyDef("ㅈ", "ㅈ").dirs("ㅉ", "~", "ㅊ", "ㅊ").w(2),
                new KeyDef("ㅎ", "ㅎ").dirs("0", "8", "7", "9").w(2),
                // 점 둘: →ㅑ ←ㅕ ↑ㅛ ↓ㅠ
                new KeyDef("··", "@STROKE").dirs("ㅛ", "ㅠ", "ㅕ", "ㅑ").w(2).noSubs(),
                slash()));
        r.add(row(
                new KeyDef("?123", "@SYM").w(1).func().noSubs(),
                globe(0.62f),
                space(3.6f),
                // sel: 탭 = 전체선택, ← = 커서부터 처음까지, → = 커서부터 끝까지
                new KeyDef("sel", "@SELALL").dirs(null, null, "@SELSTART", "@SELEND").w(0.62f).noSubs(),
                enter(1)));
        return r;
    }

    public static List<List<KeyDef>> symbols() {
        List<List<KeyDef>> r = new ArrayList<>();
        r.add(row(
                new KeyDef("1", "1").dirs("$", "₩", "€", "¥").w(2),
                new KeyDef("2", "2").dirs("%", "&", "◀", "▶").w(2),
                new KeyDef("3", "3").dirs("∞", "∴", "¡", "¿").w(2),
                del(1)));
        r.add(row(
                new KeyDef("4", "4").dirs("^", "_", "=", "-").w(2),
                new KeyDef("5", "5").dirs("×", "÷", "←", "→").w(2),
                new KeyDef("6", "6").dirs("♀", "♂", "<", ">").w(2),
                question()));
        r.add(row(
                new KeyDef("7", "7").dirs("!?", "?!", "(", ")").w(2),
                new KeyDef("8", "8").dirs("\\", "|", "{", "}").w(2),
                new KeyDef("9", "9").dirs("❤️", "♡", "[", "]").w(2),
                period()));
        r.add(row(
                new KeyDef("*", "*").dirs("★", "☆", "☜", "☞").w(2),
                new KeyDef("0", "0").dirs("😃", "😭", "😑", "😱").w(2),
                new KeyDef("#", "#").dirs("😘", "💔", "👍", "👎").w(2),
                slash()));
        KeyDef back = new KeyDef("ㄱㄴㄷ", "@SYM").w(1).func().noSubs();
        back.backKey = true;
        r.add(row(
                back,
                new KeyDef("cut", "@CUT").w(0.7f).func().noSubs(),
                new KeyDef("cpy", "@COPY").w(0.7f).func().noSubs(),
                space(2.5f),
                new KeyDef("pst", "@PASTE").w(0.7f).func().noSubs(),
                new KeyDef("sel", "@SELALL").dirs(null, null, "@SELSTART", "@SELEND").w(0.7f).func().noSubs(),
                enter(1)));
        return r;
    }

    /** 영어 쿼티: 글자 아래 작은 기호. 굿기 ↑↓← = 왼쪽 기호, → = 오른쪽 기호 */
    private static KeyDef eng(char c, String s1, String s2) {
        String l = String.valueOf(c);
        KeyDef k = new KeyDef(l, l).letter().noSubs();
        String right = s2 != null ? s2 : s1;
        k.dirs(s1, s1, s1, right);
        k.below = s2 != null ? s1 + " " + s2 : s1;
        return k;
    }

    public static List<List<KeyDef>> english() {
        List<List<KeyDef>> r = new ArrayList<>();
        String top = "qwertyuiop", nums = "1234567890";
        List<KeyDef> r1 = new ArrayList<>();
        for (int i = 0; i < 10; i++) r1.add(eng(top.charAt(i), String.valueOf(nums.charAt(i)), null));
        r.add(r1);

        String mid = "asdfghjkl";
        String[] m1 = {"@", "#", "/", "*", "~", "_", "+", "<", "❤️"};
        String[] m2 = {null, "%", null, null, "^", "-", "=", ">", null};
        List<KeyDef> r2 = new ArrayList<>();
        r2.add(KeyDef.spacer(0.5f));
        for (int i = 0; i < 9; i++) r2.add(eng(mid.charAt(i), m1[i], m2[i]));
        r2.add(KeyDef.spacer(0.5f));
        r.add(r2);

        String bot = "zxcvbnm";
        String[] b1 = {"(", "[", ";", "'", "&", "!", "?"};
        String[] b2 = {")", "]", ":", "\"", "|", null, null};
        List<KeyDef> r3 = new ArrayList<>();
        KeyDef shift = new KeyDef("", "@SHIFT").w(1.5f).func().icon("SHIFT");
        shift.isShift = true;
        r3.add(shift);
        for (int i = 0; i < 7; i++) r3.add(eng(bot.charAt(i), b1[i], b2[i]));
        r3.add(del(1.5f));
        r.add(r3);

        r.add(row(
                new KeyDef("?123", "@SYM").w(1.5f).func().noSubs(),
                globe(1),
                space(5),
                new KeyDef(".", ".").dirs(",", null, null, null).w(1),
                enter(1.5f)));
        return r;
    }
}
