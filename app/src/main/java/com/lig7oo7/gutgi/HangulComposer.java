package com.lig7oo7.gutgi;

import java.util.ArrayDeque;

/** 두벌식 한글 조합기 (초성·중성·종성, 겹모음·겹받침, 받침 넘김 처리) */
public class HangulComposer {
    static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    static final String JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    static final String[] JONG_PAIRS = {
            "ㄱㅅㄳ", "ㄴㅈㄵ", "ㄴㅎㄶ", "ㄹㄱㄺ", "ㄹㅁㄻ", "ㄹㅂㄼ",
            "ㄹㅅㄽ", "ㄹㅌㄾ", "ㄹㅍㄿ", "ㄹㅎㅀ", "ㅂㅅㅄ"};
    static final String[] JUNG_PAIRS = {
            "ㅗㅏㅘ", "ㅗㅐㅙ", "ㅗㅣㅚ", "ㅜㅓㅝ", "ㅜㅔㅞ", "ㅜㅣㅟ", "ㅡㅣㅢ",
            "ㅏㅣㅐ", "ㅓㅣㅔ", "ㅑㅣㅒ", "ㅕㅣㅖ", "ㅘㅣㅙ", "ㅝㅣㅞ"};

    private char cho, jung, jong;
    private final ArrayDeque<char[]> hist = new ArrayDeque<>();
    private final StringBuilder out = new StringBuilder();

    public static boolean isCho(char c) { return c != 0 && CHO.indexOf(c) >= 0; }
    public static boolean isVowel(char c) { return c != 0 && JUNG.indexOf(c) >= 0; }

    private static char pair(String[] table, char a, char b) {
        for (String s : table) if (s.charAt(0) == a && s.charAt(1) == b) return s.charAt(2);
        return 0;
    }

    private void push() { hist.push(new char[]{cho, jung, jong}); }

    private void commitCurrent() {
        out.append(current());
        cho = jung = jong = 0;
        hist.clear();
    }

    public void input(char c) {
        if (isVowel(c)) inputVowel(c);
        else if (isCho(c)) inputConsonant(c);
    }

    public void inputConsonant(char c) {
        if (jung == 0) {
            if (cho != 0) commitCurrent();
            push(); cho = c; return;
        }
        if (jong == 0) {
            if (cho != 0 && JONG.indexOf(c) > 0) { push(); jong = c; return; }
            commitCurrent(); push(); cho = c; return;
        }
        char comb = pair(JONG_PAIRS, jong, c);
        if (comb != 0) { push(); jong = comb; return; }
        commitCurrent(); push(); cho = c;
    }

    public void inputVowel(char v) {
        if (jong != 0) {
            char remain = 0, moved = jong;
            for (String s : JONG_PAIRS) {
                if (s.charAt(2) == jong) { remain = s.charAt(0); moved = s.charAt(1); break; }
            }
            jong = remain;
            commitCurrent();
            push(); cho = moved;
            push(); jung = v;
            return;
        }
        if (jung != 0) {
            char comb = pair(JUNG_PAIRS, jung, v);
            if (comb != 0) { push(); jung = comb; return; }
            commitCurrent(); push(); jung = v; return;
        }
        push(); jung = v;
    }

    public boolean backspace() {
        if (!isComposing()) return false;
        if (!hist.isEmpty()) {
            char[] p = hist.pop();
            cho = p[0]; jung = p[1]; jong = p[2];
        } else {
            cho = jung = jong = 0;
        }
        return true;
    }

    public String current() {
        if (cho != 0 && jung != 0) {
            int ci = CHO.indexOf(cho), ji = JUNG.indexOf(jung);
            int ti = jong == 0 ? 0 : JONG.indexOf(jong);
            return String.valueOf((char) (0xAC00 + (ci * 21 + ji) * 28 + ti));
        }
        StringBuilder s = new StringBuilder();
        if (cho != 0) s.append(cho);
        if (jung != 0) s.append(jung);
        if (jong != 0) s.append(jong);
        return s.toString();
    }

    public boolean isComposing() { return cho != 0 || jung != 0 || jong != 0; }

    /** 확정된(더 이상 바뀌지 않는) 글자를 꺼낸다 */
    public String takeCommit() {
        String s = out.toString();
        out.setLength(0);
        return s;
    }

    /** 조합 중인 글자까지 모두 확정 */
    public String finish() {
        commitCurrent();
        return takeCommit();
    }

    public void reset() {
        cho = jung = jong = 0;
        hist.clear();
        out.setLength(0);
    }
}
