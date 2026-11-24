package com.lig7oo7.gutgi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 자판 배열 정의. 순서: dirs(위, 아래, 왼쪽, 오른쪽)
 * split = 가운데를 비워 양손 엄지로 치게 나눔 (가로모드), splitSpace = 스페이스바도 둘로
 */
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

    /** 지구본: 탭 = 한/영(설정에 따라 한→영→숫), 위로 밀기 = 숫자·기호, 길게 = 옵션창 */
    private static KeyDef globe(float w) {
        KeyDef k = new KeyDef("", "@LANG").dirs("@SYM", null, null, null).w(w).noSubs().icon("GLOBE");
        k.longTap = "@OPTIONS";
        return k;
    }

    /** 오른쪽 기호 열 (한글·기호 자판 공통) */
    private static KeyDef question() { return new KeyDef("?", "?").dirs("!", "*", "+", "").w(1); }
    private static KeyDef period() { return new KeyDef(".", ".").dirs("\"", "'", ",", "").w(1); }
    private static KeyDef slash() { return new KeyDef("/", "/").dirs(":", ";", "@", "").w(1); }

    private static KeyDef selKey(float w) {
        KeyDef k = new KeyDef("sel", "@SELALL").w(w).noSubs();
        k.selDrag = true;
        return k;
    }

    /** 줄 가운데(at 번째 앞)에 빈 칸 넣기 */
    private static List<KeyDef> gap(List<KeyDef> r, int at, float w) {
        r.add(at, KeyDef.spacer(w));
        return r;
    }

    /** 맨 아래 줄: 스페이스바를 가운데 빈칸 양쪽으로 나누거나, 빈칸만큼 넓힌다 */
    private static List<KeyDef> bottom(KeyDef[] left, float spaceW, KeyDef[] right, boolean split, boolean splitSpace, float gapW) {
        List<KeyDef> r = new ArrayList<>(Arrays.asList(left));
        if (split && splitSpace) {
            r.add(space(spaceW / 2));
            r.add(KeyDef.spacer(gapW));
            r.add(space(spaceW / 2));
        } else {
            r.add(space(split ? spaceW + gapW : spaceW));
        }
        r.addAll(Arrays.asList(right));
        return r;
    }

    // ───────── 딩굴식 한글 ─────────
    public static List<List<KeyDef>> hangul(boolean split, boolean splitSpace) {
        float g = 1.8f;
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
        if (split) for (int i = 0; i < 4; i++) gap(r.get(i), 2, g);   // 자음 | 모음
        r.add(bottom(
                new KeyDef[]{new KeyDef("?123", "@SYM").w(1).func().noSubs(), globe(0.62f)},
                3.6f,
                // sel: 탭 = 전체선택, 왼쪽으로 끌면 커서 앞 글자를 한 칸씩 선택 (오른쪽으로 되돌리면 줄어듦)
                new KeyDef[]{selKey(0.62f), enter(1)},
                split, splitSpace, g));
        return r;
    }

    public static List<List<KeyDef>> symbols(boolean split, boolean splitSpace) {
        float g = 1.8f;
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
        if (split) for (int i = 0; i < 4; i++) gap(r.get(i), 2, g);
        KeyDef back = new KeyDef("ㄱㄴㄷ", "@SYM").w(1).func().noSubs();
        back.backKey = true;
        r.add(bottom(
                new KeyDef[]{back,
                        new KeyDef("cut", "@CUT").w(0.7f).func().noSubs(),
                        new KeyDef("cpy", "@COPY").w(0.7f).func().noSubs()},
                2.5f,
                new KeyDef[]{new KeyDef("pst", "@PASTE").w(0.7f).func().noSubs(),
                        new KeyDef("sel", "@SELALL").w(0.7f).func().noSubs(),
                        enter(1)},
                split, splitSpace, g));
        return r;
    }

    /** 쿼티 키: 글자 아래 작은 기호. 굿기 ↑↓← = 왼쪽 기호, → = 오른쪽 기호 (한글은 ↑ = 쌍자음) */
    private static KeyDef qk(String l, String s1, String s2, String shifted, boolean latin) {
        KeyDef k = new KeyDef(l, l).noSubs();
        if (latin) k.letter();
        String right = s2 != null ? s2 : s1;
        k.dirs(shifted != null ? shifted : s1, s1, s1, right);
        k.below = s2 != null ? s1 + " " + s2 : s1;
        k.shifted = shifted;
        return k;
    }

    private static final String NUMS = "1234567890";
    private static final String[] M1 = {"@", "#", "/", "*", "~", "_", "+", "<", "❤️"};
    private static final String[] M2 = {null, "%", null, null, "^", "-", "=", ">", null};
    private static final String[] B1 = {"(", "[", ";", "'", "&", "!", "?"};
    private static final String[] B2 = {")", "]", ":", "\"", "|", null, null};

    /** 영어 쿼티와 두벌식 한글 쿼티가 같은 틀을 씀 */
    private static List<List<KeyDef>> qwerty(String[] top, String[] mid, String[] bot, String[] topSh, String[] midSh,
                                              boolean latin, boolean split, boolean splitSpace) {
        float g = 2.2f;
        List<List<KeyDef>> r = new ArrayList<>();
        List<KeyDef> r1 = new ArrayList<>();
        for (int i = 0; i < 10; i++) r1.add(qk(top[i], String.valueOf(NUMS.charAt(i)), null, topSh == null ? null : topSh[i], latin));
        if (split) gap(r1, 5, g);                 // q w e r t | y u i o p
        r.add(r1);

        List<KeyDef> r2 = new ArrayList<>();
        r2.add(KeyDef.spacer(0.5f));
        for (int i = 0; i < 9; i++) r2.add(qk(mid[i], M1[i], M2[i], midSh == null ? null : midSh[i], latin));
        r2.add(KeyDef.spacer(0.5f));
        if (split) gap(r2, 6, g);                 // a s d f g | h j k l
        r.add(r2);

        List<KeyDef> r3 = new ArrayList<>();
        KeyDef shift = new KeyDef("", "@SHIFT").w(1.5f).func().icon("SHIFT");
        shift.isShift = true;
        r3.add(shift);
        for (int i = 0; i < 7; i++) r3.add(qk(bot[i], B1[i], B2[i], null, latin));
        r3.add(del(1.5f));
        if (split) gap(r3, 5, g);                 // ⇧ z x c v | b n m ⌫
        r.add(r3);

        r.add(bottom(
                new KeyDef[]{new KeyDef("?123", "@SYM").w(1.5f).func().noSubs(), globe(1)},
                5f,
                new KeyDef[]{new KeyDef(".", ".").dirs(",", null, null, null).w(1), enter(1.5f)},
                split, splitSpace, g));
        return r;
    }

    private static String[] chars(String s) {
        String[] a = new String[s.length()];
        for (int i = 0; i < a.length; i++) a[i] = String.valueOf(s.charAt(i));
        return a;
    }

    public static List<List<KeyDef>> english(boolean split, boolean splitSpace) {
        return qwerty(chars("qwertyuiop"), chars("asdfghjkl"), chars("zxcvbnm"), null, null, true, split, splitSpace);
    }

    /** 두벌식 한글 쿼티 (가로모드용). 시프트·위로 밀기 = 쌍자음·ㅒㅖ */
    public static List<List<KeyDef>> hangulQwerty(boolean split, boolean splitSpace) {
        String[] topSh = {"ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", null, null, null, "ㅒ", "ㅖ"};
        return qwerty(chars("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ"), chars("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ"), chars("ㅋㅌㅊㅍㅠㅜㅡ"),
                topSh, null, false, split, splitSpace);
    }

    /** 시프트 눌린 상태의 두벌식 글자 */
    public static String dubeolShift(String t) {
        String m = "ㅂㅃㅈㅉㄷㄸㄱㄲㅅㅆㅐㅒㅔㅖ";
        int i = m.indexOf(t);
        return t.length() == 1 && i >= 0 && i % 2 == 0 ? String.valueOf(m.charAt(i + 1)) : t;
    }
}
