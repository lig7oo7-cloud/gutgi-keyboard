package com.lig7oo7.gutgi;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 키보드 속 계산기 + 직접 설정한 환율로 원화 환산 (인터넷 사용 안 함) */
public class Calc {

    /** 사칙연산 수식이면 결과, 아니면 null. 연산자가 하나 이상 있어야 수식으로 본다 */
    public static Double eval(String expr) {
        if (expr == null) return null;
        String s = expr.replace(",", "").replace("×", "*").replace("÷", "/").replace(" ", "");
        boolean eq = s.endsWith("=");
        if (eq) s = s.substring(0, s.length() - 1);
        // 날짜·전화번호(2026-11-24, 010-1234-5678)는 계산하지 않음: 빼기만 있으면 끝에 = 를 쳐야 계산
        if (!eq && !s.matches(".*[+*/].*")) return null;
        if (!s.matches("[0-9.+\\-*/()]+")) return null;
        if (!s.matches(".*[0-9.)][+\\-*/][(0-9.].*")) return null;   // 숫자 사이 연산자 필수
        try {
            Parser p = new Parser(s);
            double v = p.expr();
            if (p.pos != s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private static class Parser {
        final String s; int pos;
        Parser(String s) { this.s = s; }
        double expr() {
            double v = term();
            while (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                char op = s.charAt(pos++);
                double r = term();
                v = op == '+' ? v + r : v - r;
            }
            return v;
        }
        double term() {
            double v = factor();
            while (pos < s.length() && (s.charAt(pos) == '*' || s.charAt(pos) == '/')) {
                char op = s.charAt(pos++);
                double r = factor();
                v = op == '*' ? v * r : v / r;
            }
            return v;
        }
        double factor() {
            if (s.charAt(pos) == '-') { pos++; return -factor(); }
            if (s.charAt(pos) == '(') {
                pos++;
                double v = expr();
                if (s.charAt(pos++) != ')') throw new IllegalStateException();
                return v;
            }
            int st = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (st == pos) throw new IllegalStateException();
            return Double.parseDouble(s.substring(st, pos));
        }
    }

    public static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return new DecimalFormat("#,##0").format(v);
        return new DecimalFormat("#,##0.##").format(v);
    }

    /** 숫자만 (콤마 없이) — 수식 뒤에 붙일 때 */
    public static String plain(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return new DecimalFormat("0.##").format(v);
    }

    // ───────── 환율 ─────────
    /** 통화 이름 → 설정 키 */
    static String currency(String unit) {
        switch (unit.toLowerCase()) {
            case "달러": case "불": case "$": case "usd": return "USD";
            case "엔": case "jpy": case "¥": return "JPY";
            case "유로": case "eur": case "€": return "EUR";
            case "위안": case "cny": return "CNY";
            default: return null;
        }
    }

    private static final Pattern AMOUNT_UNIT =
            Pattern.compile("^([0-9][0-9,]*(?:\\.[0-9]+)?)(달러|불|usd|USD|엔|JPY|jpy|유로|EUR|eur|위안|CNY|cny)$");
    private static final Pattern UNIT_AMOUNT =
            Pattern.compile("^([$¥€])([0-9][0-9,]*(?:\\.[0-9]+)?)$");

    /**
     * "10달러", "$10", "3000엔" 등을 원화로. rates: USD/EUR/CNY 는 1단위당 원, JPY 는 100엔당 원.
     * @return {원화 금액, 사용한 통화, 금액} 또는 null
     */
    public static double[] toWon(String tok, Map<String, Double> rates) {
        if (tok == null || rates == null) return null;
        String amount, unit;
        Matcher m = AMOUNT_UNIT.matcher(tok);
        if (m.matches()) { amount = m.group(1); unit = m.group(2); }
        else {
            m = UNIT_AMOUNT.matcher(tok);
            if (!m.matches()) return null;
            unit = m.group(1); amount = m.group(2);
        }
        String cur = currency(unit);
        Double rate = cur == null ? null : rates.get(cur);
        if (rate == null || rate <= 0) return null;
        double a = Double.parseDouble(amount.replace(",", ""));
        double won = cur.equals("JPY") ? a * rate / 100.0 : a * rate;
        return new double[]{won, a};
    }

    public static String unitLabel(String tok) {
        Matcher m = AMOUNT_UNIT.matcher(tok);
        String unit = m.matches() ? m.group(2) : tok.substring(0, 1);
        String cur = currency(unit);
        if ("USD".equals(cur)) return "1달러";
        if ("JPY".equals(cur)) return "100엔";
        if ("EUR".equals(cur)) return "1유로";
        return "1위안";
    }
}
