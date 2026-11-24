package com.lig7oo7.gutgi;

import android.content.ClipData;
import android.content.Intent;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.List;

public class GutgiIME extends InputMethodService implements KeyboardView.Listener, ExtraPanels.Host {

    private static final int HAN = 0, ENG = 1, SYM = 2;

    private KeyboardView view;
    private final HangulComposer hc = new HangulComposer();
    private List<List<KeyDef>> hanRows, engRows, symRows;
    private int mode = HAN, langMode = HAN;
    private int shift; // 0 끔, 1 한 번, 2 고정
    private long lastShiftTap;
    private ExtraPanels panels;
    private ClipboardManager clipboard;
    private int selAnchor = -1, selCount;
    private boolean selFallback;

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = () -> {
        try {
            ClipData cd = clipboard.getPrimaryClip();
            if (cd != null && cd.getItemCount() > 0) {
                CharSequence t = cd.getItemAt(0).coerceToText(this);
                if (t != null) Store.addClip(this, t.toString());
            }
        } catch (Exception ignored) { }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.addPrimaryClipChangedListener(clipListener);
    }

    @Override
    public void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (clipboard != null) clipboard.removePrimaryClipChangedListener(clipListener);
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        hanRows = Layouts.hangul();
        engRows = Layouts.english();
        symRows = Layouts.symbols();
        Theme.load(this);
        view = new KeyboardView(this);
        view.setListener(this);
        panels = new ExtraPanels(this, this, view);
        applyLayout();
        return panels.root();
    }

    // ExtraPanels.Host
    @Override
    public void insertText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        commitComposing();
        ic.commitText(text, 1);
    }

    @Override
    public void contextAction(int id) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        commitComposing();
        ic.performContextMenuAction(id);
    }

    private String lastWord = null;

    // ───────── 추천 칩 (초성·앞글자·이메일) ─────────
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable suggestTask = this::refreshSuggestions;
    private java.util.List<String> dictCache;
    private java.util.Map<String, Integer> suggestUsage;

    private void scheduleSuggest() {
        ui.removeCallbacks(suggestTask);
        ui.postDelayed(suggestTask, 40);
    }

    private java.util.List<String> dict() {
        if (dictCache == null) {
            java.util.List<String> l = new java.util.ArrayList<>();
            l.addAll(Store.load(this, "userWords"));      // 내 단어가 먼저
            l.addAll(Store.pins(this));                   // 📌 고정 항목도 추천
            l.addAll(learnedWords());                     // 자주 쓴 말 (2번 이상)
            l.addAll(java.util.Arrays.asList(Dict.WORDS));
            l.addAll(java.util.Arrays.asList(Dict.ENGLISH));
            dictCache = l;
        }
        return dictCache;
    }

    private java.util.Map<String, Integer> usageMap() {
        if (suggestUsage == null) {
            suggestUsage = new java.util.HashMap<>();
            for (String e : Store.load(this, "suggestUsage")) {
                int i = e.lastIndexOf('\t');
                if (i > 0) try { suggestUsage.put(e.substring(0, i), Integer.parseInt(e.substring(i + 1))); } catch (Exception ignored) { }
            }
        }
        return suggestUsage;
    }

    private boolean isPrivateField() {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei == null) return true;
        int var = ei.inputType & InputType.TYPE_MASK_VARIATION;
        int cls = ei.inputType & InputType.TYPE_MASK_CLASS;
        return cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || cls == InputType.TYPE_CLASS_NUMBER && (ei.inputType & InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0;
    }

    private void refreshSuggestions() {
        if (panels == null) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || isPrivateField()) { panels.setSuggestions("", null); return; }
        CharSequence before = ic.getTextBeforeCursor(64, 0);
        String tok = Suggest.token(before);
        EditorInfo ei = getCurrentInputEditorInfo();
        int var = ei == null ? 0 : ei.inputType & InputType.TYPE_MASK_VARIATION;
        boolean email = var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
        panels.setSuggestions(tok, Suggest.compute(tok, email, dict(), java.util.Arrays.asList(Dict.DOMAINS), usageMap(), rates()));
    }

    /** 추천 칩 선택: 입력 중이던 조각(조합 중인 글자 포함)을 지우고 완성 단어로 바꾼다 */
    @Override
    public void applySuggestion(Suggest.Item item) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String comp = hc.isComposing() ? hc.current() : "";
        hc.reset();
        ic.beginBatchEdit();
        int del = item.deleteBefore;
        if (!comp.isEmpty()) {
            ic.setComposingText("", 1);
            ic.finishComposingText();
            del = Math.max(0, del - comp.length());
        }
        if (del > 0) ic.deleteSurroundingText(del, 0);
        ic.commitText(item.insert + (item.space ? " " : ""), 1);
        ic.endBatchEdit();
        lastWord = null;
        if (item.learnKey != null) {
            java.util.Map<String, Integer> u = usageMap();
            u.put(item.learnKey, (u.containsKey(item.learnKey) ? u.get(item.learnKey) : 0) + 1);
            java.util.List<String> out = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, Integer> e : u.entrySet()) out.add(e.getKey() + "\t" + e.getValue());
            Store.save(this, "suggestUsage", out);
        }
        scheduleSuggest();
    }

    // ───────── 음성 입력 (폰의 음성 인식 서비스 사용, 키보드 자체는 인터넷 없음) ─────────
    private android.speech.SpeechRecognizer recognizer;
    private boolean listening;

    @Override
    public void toggleVoice() {
        if (listening) { stopVoice(); return; }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "처음 한 번: 마이크 권한을 허용해 주세요", Toast.LENGTH_SHORT).show();
            openApp("micperm");
            return;
        }
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "음성 인식 서비스가 없어요 (Google 앱 필요)", Toast.LENGTH_SHORT).show();
            return;
        }
        commitComposing();
        if (recognizer == null) {
            recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle b) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float v) { }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onEvent(int t, android.os.Bundle b) { }
                @Override public void onPartialResults(android.os.Bundle b) {
                    String t = first(b);
                    InputConnection ic = getCurrentInputConnection();
                    if (t != null && ic != null) ic.setComposingText(t, 1);   // 말하는 중 미리 보기
                }
                @Override public void onResults(android.os.Bundle b) {
                    String t = first(b);
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        if (t != null && !t.isEmpty()) ic.commitText(t + " ", 1);
                        else ic.finishComposingText();
                    }
                    setListening(false);
                }
                @Override public void onError(int error) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.finishComposingText();
                    if (error == android.speech.SpeechRecognizer.ERROR_NO_MATCH
                            || error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Toast.makeText(GutgiIME.this, "말소리를 못 들었어요", Toast.LENGTH_SHORT).show();
                    } else if (error == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        openApp("micperm");
                    } else {
                        Toast.makeText(GutgiIME.this, "음성 인식 오류 (" + error + ")", Toast.LENGTH_SHORT).show();
                    }
                    setListening(false);
                }
            });
        }
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, mode == ENG ? "en-US" : "ko-KR");
        i.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        try {
            recognizer.startListening(i);
            setListening(true);
            Toast.makeText(this, "🎤 말씀하세요", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "음성 인식을 시작하지 못했어요", Toast.LENGTH_SHORT).show();
        }
    }

    private static String first(android.os.Bundle b) {
        java.util.ArrayList<String> r = b == null ? null
                : b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
        return r == null || r.isEmpty() ? null : r.get(0);
    }

    private void stopVoice() {
        if (recognizer != null) recognizer.stopListening();   // 지금까지 말한 것으로 마무리
        setListening(false);
    }

    private void setListening(boolean on) {
        listening = on;
        if (panels != null) panels.setVoiceActive(on);
    }

    // ───────── 되돌리기 (입력칸의 실행 취소 기능: Ctrl+Z) ─────────
    @Override
    public void undo(boolean redo) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        commitComposing();
        int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON | (redo ? KeyEvent.META_SHIFT_ON : 0);
        long now = android.os.SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, meta));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, meta));
    }

    // ───────── ⌫ 끌어서 단어 단위 삭제 ─────────
    private int dwAnchor = -1, dwSteps;
    private final java.util.ArrayList<Integer> dwOffsets = new java.util.ArrayList<>();

    private void wordDelBegin(InputConnection ic) {
        commitComposing();
        dwSteps = 0;
        dwOffsets.clear();
        CharSequence before = ic.getTextBeforeCursor(400, 0);
        String b = before == null ? "" : before.toString();
        int i = b.length();
        while (i > 0 && dwOffsets.size() < 60) {
            while (i > 0 && Character.isWhitespace(b.charAt(i - 1))) i--;
            while (i > 0 && !Character.isWhitespace(b.charAt(i - 1))) i--;
            dwOffsets.add(b.length() - i);
        }
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        dwAnchor = et == null ? -1 : et.startOffset + Math.max(et.selectionStart, et.selectionEnd);
    }

    private void wordDelStep(InputConnection ic, int d) {
        dwSteps = Math.max(0, Math.min(dwOffsets.size(), dwSteps + d));
        if (dwAnchor < 0) return;   // 미리보기 불가한 입력칸: 손 뗄 때 지움
        int len = dwSteps == 0 ? 0 : dwOffsets.get(dwSteps - 1);
        ic.setSelection(dwAnchor - len, dwAnchor);
    }

    private void wordDelEnd(InputConnection ic) {
        int len = dwSteps == 0 ? 0 : dwOffsets.get(dwSteps - 1);
        if (len > 0) {
            if (dwAnchor >= 0) ic.commitText("", 1);          // 선택된 단어들 삭제
            else ic.deleteSurroundingText(len, 0);
        } else if (dwAnchor >= 0) {
            ic.setSelection(dwAnchor, dwAnchor);
        }
        dwSteps = 0;
        dwAnchor = -1;
    }

    // ───────── 자주 쓰는 말 학습 (폰 안에만 저장, 비밀번호 칸 제외) ─────────
    private java.util.Map<String, Integer> learned;

    private java.util.Map<String, Integer> learnedMap() {
        if (learned == null) {
            learned = new java.util.HashMap<>();
            for (String e : Store.load(this, "learned")) {
                int i = e.lastIndexOf('\t');
                if (i > 0) try { learned.put(e.substring(0, i), Integer.parseInt(e.substring(i + 1))); } catch (Exception ignored) { }
            }
        }
        return learned;
    }

    private java.util.List<String> learnedWords() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : learnedMap().entrySet()) if (e.getValue() >= 2) out.add(e.getKey());
        return out;
    }

    private void learnBeforeCursor(InputConnection ic) {
        if (isPrivateField() || !getSharedPreferences("gutgi", MODE_PRIVATE).getBoolean("learn", true)) return;
        String w = Suggest.token(ic.getTextBeforeCursor(40, 0)).replaceAll("[.,?!~^]+$", "");
        boolean ko = w.matches("[가-힣]{2,12}");
        boolean en = w.matches("[A-Za-z]{3,20}");
        if (!ko && !en) return;                       // 숫자(전화·인증번호)는 배우지 않음
        java.util.Map<String, Integer> m = learnedMap();
        int n = (m.containsKey(w) ? m.get(w) : 0) + 1;
        m.put(w, n);
        if (m.size() > 500) {                         // 오래 안 쓴(적게 쓴) 것부터 정리
            String min = null;
            for (java.util.Map.Entry<String, Integer> e : m.entrySet())
                if (min == null || e.getValue() < m.get(min)) min = e.getKey();
            m.remove(min);
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : m.entrySet()) out.add(e.getKey() + "\t" + e.getValue());
        Store.save(this, "learned", out);
        if (n == 2 && dictCache != null) dictCache.add(w);
    }

    // ───────── 환율 (설정에 직접 적어둔 값) ─────────
    private java.util.Map<String, Double> rates() {
        android.content.SharedPreferences sp = getSharedPreferences("gutgi", MODE_PRIVATE);
        java.util.Map<String, Double> r = new java.util.HashMap<>();
        for (String c : new String[]{"USD", "JPY", "EUR", "CNY"}) {
            float v = sp.getFloat("rate_" + c, 0f);
            if (v > 0) r.put(c, (double) v);
        }
        return r;
    }

    /** 키보드를 내리고 화면 캡처 (화면 공유 세션이 살아 있으면 동의 없이 바로) */
    @Override
    public void takeScreenshot() {
        commitComposing();
        requestHideSelf(0);                                   // 키보드 내리기
        if (ShotService.alive) {
            ui.postDelayed(() -> {                            // 키보드가 완전히 내려간 뒤
                ShotService.callback = ok -> ui.post(() -> Toast.makeText(this,
                        ok ? "캡처 저장됨 (갤러리 › Screenshots)" : "캡처하지 못했어요", Toast.LENGTH_SHORT).show());
                try {
                    startService(new Intent(this, ShotService.class).setAction(ShotService.ACTION_CAPTURE));
                } catch (Exception e) {
                    ShotService.callback = null;
                    launchShot();
                }
            }, 450);
        } else {
            launchShot();
        }
    }

    private void launchShot() {
        Intent i = new Intent(this, ShotActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
    }

    /** 감정 단어 버튼: 방금 넣은 단어가 커서 바로 앞에 그대로 있으면 지우고 새 단어로 교체 */
    @Override
    public void replaceWord(String word) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        commitComposing();
        ic.beginBatchEdit();
        if (lastWord != null) {
            CharSequence before = ic.getTextBeforeCursor(lastWord.length(), 0);
            CharSequence sel = ic.getSelectedText(0);
            if (before != null && before.toString().equals(lastWord) && (sel == null || sel.length() == 0)) {
                ic.deleteSurroundingText(lastWord.length(), 0);
            }
        }
        ic.commitText(word, 1);
        ic.endBatchEdit();
        lastWord = word;
    }

    @Override
    public void openApp(String extra) {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(extra, true);
        startActivity(i);
    }

    @Override
    public CharSequence selectedText() {
        InputConnection ic = getCurrentInputConnection();
        return ic == null ? null : ic.getSelectedText(0);
    }

    @Override
    public boolean onEvaluateFullscreenMode() { return false; }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (Theme.load(this) && view != null) setInputView(onCreateInputView());   // 다크 모드·키 높이 변경 반영
        hc.reset();
        shift = 0;
        if (panels != null) {
            panels.reset();
        }
        lastWord = null;
        dictCache = null;   // 상용구·내 단어 변경 반영
        learned = null;
        scheduleSuggest();
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int var = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME) {
            mode = SYM;
        } else if (cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_URI)) {
            mode = ENG;
        } else {
            mode = langMode;
        }
        applyLayout();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (listening && recognizer != null) recognizer.cancel();
        setListening(false);
        commitComposing();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        scheduleSuggest();
        // 사용자가 커서를 다른 곳에 찍으면 조합을 끊는다
        if (hc.isComposing() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            hc.reset();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
        }
    }

    private void applyLayout() {
        if (view == null) return;
        view.setRows(mode == HAN ? hanRows : mode == ENG ? engRows : symRows);
        view.setShift(mode == ENG ? shift : 0);
        view.setBackLabel(langMode == HAN ? "ㄱㄴㄷ" : "abc");
        EditorInfo ei = getCurrentInputEditorInfo();
        view.setEnterSearch(ei != null
                && (ei.imeOptions & EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_SEARCH);
    }

    private void commitComposing() {
        InputConnection ic = getCurrentInputConnection();
        if (!hc.isComposing()) return;
        String s = hc.finish();
        if (ic != null) ic.commitText(s, 1);
    }

    @Override
    public void onOutput(String t) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        switch (t) {
            case "@DEL":
                if (hc.backspace()) {
                    ic.setComposingText(hc.current(), 1);
                    if (!hc.isComposing()) ic.finishComposingText();
                } else {
                    CharSequence sel = ic.getSelectedText(0);
                    if (sel != null && sel.length() > 0) ic.commitText("", 1);
                    else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
                return;
            case "@STROKE":
                if (mode == HAN && hc.addStroke()) ic.setComposingText(hc.current(), 1);
                return;
            case "@SELBEGIN": {
                // sel 키를 왼쪽으로 끌면 커서 앞 글자를 한 칸씩 선택
                commitComposing();
                selCount = 0;
                selFallback = false;
                ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
                if (et != null && et.text != null) {
                    selAnchor = et.startOffset + Math.max(et.selectionStart, et.selectionEnd);
                } else {
                    selAnchor = -1;
                    selFallback = true;
                }
                return;
            }
            case "@SELMORE":
            case "@SELLESS": {
                boolean more = t.equals("@SELMORE");
                if (selFallback) {
                    if (more) { shiftArrow(ic, KeyEvent.KEYCODE_DPAD_LEFT); selCount++; }
                    else if (selCount > 0) { shiftArrow(ic, KeyEvent.KEYCODE_DPAD_RIGHT); selCount--; }
                    return;
                }
                if (selAnchor < 0) return;
                selCount = more ? Math.min(selCount + 1, selAnchor) : Math.max(0, selCount - 1);
                ic.setSelection(selAnchor - selCount, selAnchor);
                return;
            }
            case "@DWBEGIN": wordDelBegin(ic); return;
            case "@DWMORE": wordDelStep(ic, 1); return;
            case "@DWLESS": wordDelStep(ic, -1); return;
            case "@DWEND": wordDelEnd(ic); return;
            case "@SELALL":
                commitComposing();
                ic.performContextMenuAction(android.R.id.selectAll);
                return;
            case "@SELSTART":
            case "@SELEND": {
                commitComposing();
                ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
                if (et == null || et.text == null) {
                    ic.performContextMenuAction(android.R.id.selectAll);
                    return;
                }
                if (t.equals("@SELSTART")) ic.setSelection(et.startOffset, et.startOffset + Math.min(et.selectionStart, et.selectionEnd));
                else ic.setSelection(et.startOffset + Math.min(et.selectionStart, et.selectionEnd), et.startOffset + et.text.length());
                return;
            }
            case "@CUT":
                commitComposing();
                ic.performContextMenuAction(android.R.id.cut);
                return;
            case "@COPY":
                commitComposing();
                ic.performContextMenuAction(android.R.id.copy);
                return;
            case "@PASTE":
                commitComposing();
                ic.performContextMenuAction(android.R.id.paste);
                return;
            case "@ENTER":
                commitComposing();
                learnBeforeCursor(ic);
                handleEnter(ic);
                return;
            case "@SPACE":
                commitComposing();
                learnBeforeCursor(ic);
                ic.commitText(" ", 1);
                return;
            case "@LEFT":
                commitComposing();
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                return;
            case "@RIGHT":
                commitComposing();
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                return;
            case "@SYM":
                commitComposing();
                mode = (mode == SYM) ? langMode : SYM;
                applyLayout();
                return;
            case "@LANG":
                commitComposing();
                langMode = (mode == HAN) ? ENG : HAN;
                mode = langMode;
                shift = 0;
                applyLayout();
                return;
            case "@SHIFT": {
                long now = System.currentTimeMillis();
                if (shift == 0) shift = (now - lastShiftTap < 350) ? 2 : 1;
                else shift = 0;
                lastShiftTap = now;
                view.setShift(shift);
                return;
            }
        }

        if (mode == HAN && t.length() == 1
                && (HangulComposer.isCho(t.charAt(0)) || HangulComposer.isVowel(t.charAt(0)))) {
            if (!hc.retap(t.charAt(0))) hc.input(t.charAt(0));
            String done = hc.takeCommit();
            ic.beginBatchEdit();
            if (!done.isEmpty()) ic.commitText(done, 1);
            ic.setComposingText(hc.current(), 1);
            ic.endBatchEdit();
            return;
        }

        commitComposing();
        if (t.length() == 1 && ".,?!~".indexOf(t.charAt(0)) >= 0) learnBeforeCursor(ic);
        String text = t;
        if (mode == ENG && shift > 0 && t.length() == 1 && Character.isLetter(t.charAt(0))) {
            text = t.toUpperCase();
            if (shift == 1) { shift = 0; view.setShift(0); }
        }
        ic.commitText(text, 1);
    }

    private void shiftArrow(InputConnection ic, int code) {
        long now = android.os.SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.META_SHIFT_ON));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, KeyEvent.META_SHIFT_ON));
    }

    private void handleEnter(InputConnection ic) {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noAction = (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            if (!noAction && action != EditorInfo.IME_ACTION_NONE
                    && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action);
                return;
            }
        }
        sendKeyChar('\n');
    }
}
