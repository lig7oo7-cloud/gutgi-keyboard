package com.lig7oo7.gutgi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 추천 후보 계산 (안드로이드에 의존하지 않는 순수 로직 → 단독 테스트 가능)
 * 우선순위: 이메일 도메인 → 초성/접두사 사전 매칭
 */
public class Suggest {

    public static class Item {
        public final String label;     // 칩에 보이는 글자
        public final String insert;    // 실제로 넣을 글자
        public final int deleteBefore; // 넣기 전에 지울 글자 수 (입력 중이던 조각)
        public final String learnKey;  // 사용 횟수 기록용 (null 이면 기록 안 함)
        public final boolean space;    // 넣은 뒤 띄어쓰기
        Item(String label, String insert, int deleteBefore, String learnKey) {
            this(label, insert, deleteBefore, learnKey, learnKey != null);
        }
        Item(String label, String insert, int deleteBefore, String learnKey, boolean space) {
            this.label = label; this.insert = insert; this.deleteBefore = deleteBefore;
            this.learnKey = learnKey; this.space = space;
        }
        @Override public String toString() { return label + "(-" + deleteBefore + "+" + insert + ")"; }
    }

    static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    static final int MAX = 8;

    static boolean isSyllable(char c) { return c >= 0xAC00 && c <= 0xD7A3; }
    static boolean isCho(char c) { return CHO.indexOf(c) >= 0; }
    static char choOf(char c) { return CHO.charAt((c - 0xAC00) / 588); }
    static int jungOf(char c) { return ((c - 0xAC00) % 588) / 28; }
    static int jongOf(char c) { return (c - 0xAC00) % 28; }

    private static double rateOf(String tok, Map<String, Double> rates) {
        String l = Calc.unitLabel(tok);
        String k = l.contains("달러") ? "USD" : l.contains("엔") ? "JPY" : l.contains("유로") ? "EUR" : "CNY";
        Double r = rates.get(k);
        return r == null ? 0 : r;
    }

    /** 커서 앞에서 공백·줄바꿈 이후의 조각 */
    public static String token(CharSequence before) {
        if (before == null) return "";
        String s = before.toString();
        int i = s.length();
        while (i > 0 && !Character.isWhitespace(s.charAt(i - 1))) i--;
        return s.substring(i);
    }

    /**
     * 입력 조각 한 글자 t 가 단어 글자 w 와 맞는지.
     * last=true(마지막 글자)이면 아직 조합 중일 수 있으니 '가' 가 '각'·'간' 에도 맞게 본다.
     */
    static boolean charMatch(char t, char w, boolean last) {
        if (t == w) return true;
        if (isCho(t) && isSyllable(w)) return choOf(w) == t;
        if (last && isSyllable(t) && isSyllable(w)) {
            return choOf(t) == choOf(w) && jungOf(t) == jungOf(w) && jongOf(t) == 0;
        }
        return false;
    }

    /** 단어(공백 제외)가 조각으로 시작하는지 */
    static boolean prefixMatch(String tok, String word) {
        String w = word.replace(" ", "");
        if (w.length() <= tok.length() && !hasChoOnly(tok)) return false;   // 다 친 단어는 추천할 필요 없음
        if (w.length() < tok.length()) return false;
        for (int i = 0; i < tok.length(); i++) {
            if (!charMatch(tok.charAt(i), w.charAt(i), i == tok.length() - 1)) return false;
        }
        return true;
    }

    static boolean hasChoOnly(String tok) {
        for (int i = 0; i < tok.length(); i++) if (!isCho(tok.charAt(i))) return false;
        return true;
    }

    static boolean hasHangul(String tok) {
        for (int i = 0; i < tok.length(); i++) {
            char c = tok.charAt(i);
            if (isCho(c) || isSyllable(c)) return true;
        }
        return false;
    }

    /**
     * @param tok          커서 앞 조각
     * @param emailField   이메일 입력칸이면 '@' 없이도 도메인 추천
     * @param words        사전 (자주 쓰는 말 + 상용구 + 내 단어)
     * @param domains      이메일 도메인 (예: gmail.com)
     * @param usage        고른 횟수 (많이 고른 것 먼저)
     */
    public static List<Item> compute(String tok, boolean emailField, List<String> words,
                                     List<String> domains, Map<String, Integer> usage) {
        return compute(tok, emailField, words, domains, usage, null);
    }

    public static List<Item> compute(String tok, boolean emailField, List<String> words,
                                     List<String> domains, Map<String, Integer> usage,
                                     Map<String, Double> rates) {
        List<Item> out = new ArrayList<>();
        if (tok == null || tok.isEmpty()) return out;

        // 계산기: 12500+3000 → "=15,500" (수식 뒤에 붙이기) / "15,500" (결과로 바꾸기)
        Double v = Calc.eval(tok);
        if (v != null) {
            boolean endsEq = tok.endsWith("=");
            out.add(new Item("= " + Calc.fmt(v), (endsEq ? "" : "=") + Calc.plain(v), 0, null, false));
            out.add(new Item("→ " + Calc.fmt(v), Calc.fmt(v), tok.length(), null, false));
            return out;
        }
        // 환율 (설정에 적어둔 값): 10달러 → 약 14,000원
        double[] won = Calc.toWon(tok, rates);
        if (won != null) {
            String w = Calc.fmt(Math.round(won[0])) + "원";
            out.add(new Item(tok + " ≈ " + w, "(약 " + w + ")", 0, null, false));
            out.add(new Item("→ " + w, w, tok.length(), null, false));
            out.add(new Item("기준 " + Calc.unitLabel(tok) + "=" + Calc.fmt(rateOf(tok, rates)) + "원", "", 0, null, false));
            return out;
        }

        // 0) 저장해 둔 내 이메일 주소: 앞 2글자부터 주소 전체 추천
        if (tok.length() >= 2 && tok.matches("[A-Za-z0-9._%+@-]+")) {
            String low = tok.toLowerCase();
            for (String w : words) {
                if (w == null || w.indexOf('@') <= 0 || w.contains(" ")) continue;
                if (w.toLowerCase().startsWith(low) && !w.equalsIgnoreCase(tok)) {
                    boolean dup = false;
                    for (Item it : out) if (it.label.equals(w)) dup = true;
                    if (!dup) out.add(new Item(w, w, tok.length(), w, false));
                }
            }
        }

        // 1) 이메일 도메인
        int at = tok.indexOf('@');
        if (at > 0 && tok.substring(0, at).matches("[A-Za-z0-9._%+-]+")) {
            String typed = tok.substring(at + 1).toLowerCase();
            for (String d : domains) {
                if (d.startsWith(typed) && !d.equals(typed)) {
                    out.add(new Item("@" + d, d.substring(typed.length()), 0, null));
                }
            }
            return out;
        }
        if (at < 0 && emailField && tok.matches("[A-Za-z0-9._%+-]{2,}")) {
            for (String d : domains) out.add(new Item("@" + d, "@" + d, 0, null));
            return out;
        }
        if (!out.isEmpty() && !tok.matches("[A-Za-z]+")) return out;   // 숫자·기호가 섞이면 이메일만

        // 2) 영어 앞글자 (2글자부터)
        if (tok.matches("[A-Za-z]{2,}")) {
            String low = tok.toLowerCase();
            boolean cap = Character.isUpperCase(tok.charAt(0));
            boolean allCap = tok.length() > 1 && tok.equals(tok.toUpperCase());
            List<String> eh = new ArrayList<>();
            for (String w : words) {
                if (w == null || w.length() <= tok.length() || eh.contains(w) || w.indexOf('@') >= 0) continue;
                if (w.toLowerCase().startsWith(low) && w.matches("[A-Za-z][A-Za-z '\\-]*")) eh.add(w);
            }
            final Map<String, Integer> u0 = usage;
            Collections.sort(eh, (a, b) -> {
                int ua = u0.containsKey(a) ? u0.get(a) : 0, ub = u0.containsKey(b) ? u0.get(b) : 0;
                if (ua != ub) return ub - ua;
                return a.length() - b.length();
            });
            for (String w : eh) {
                if (out.size() >= MAX + 2) break;
                String shown = allCap ? w.toUpperCase() : cap ? Character.toUpperCase(w.charAt(0)) + w.substring(1) : w;
                out.add(new Item(shown, shown, tok.length(), w));
            }
            return out;
        }

        // 3) 초성·접두사 (한글이 들어 있을 때만, 한 글자 초성은 너무 많아서 2글자부터)
        if (!hasHangul(tok)) return out;
        if (hasChoOnly(tok) && tok.length() < 2) return out;
        List<String> hits = new ArrayList<>();
        for (String w : words) {
            if (w == null || w.isEmpty() || hits.contains(w)) continue;
            if (prefixMatch(tok, w)) hits.add(w);
        }
        final Map<String, Integer> u = usage;
        Collections.sort(hits, (a, b) -> {
            int ua = u.containsKey(a) ? u.get(a) : 0, ub = u.containsKey(b) ? u.get(b) : 0;
            if (ua != ub) return ub - ua;
            return a.length() - b.length();
        });
        for (String w : hits) {
            if (out.size() >= MAX) break;
            out.add(new Item(w, w, tok.length(), w));
        }
        return out;
    }
}
