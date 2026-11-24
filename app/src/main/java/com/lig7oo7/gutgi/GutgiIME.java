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
        if (clipboard != null) clipboard.removePrimaryClipChangedListener(clipListener);
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        hanRows = Layouts.hangul();
        engRows = Layouts.english();
        symRows = Layouts.symbols();
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
            l.addAll(Store.phrases(this));                // 상용구도 추천
            l.addAll(java.util.Arrays.asList(Dict.WORDS));
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
        panels.setSuggestions(tok, Suggest.compute(tok, email, dict(), java.util.Arrays.asList(Dict.DOMAINS), usageMap()));
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
        ic.commitText(item.insert + (item.learnKey != null ? " " : ""), 1);
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

    /** 아재개그 요청을 Gemini로 보냈음을 기록 → 3분 안에 키보드가 다시 뜨면 '기록' 패널을 바로 연다 */
    public static void markAjaePending(android.content.Context c) {
        c.getSharedPreferences("gutgi", MODE_PRIVATE).edit()
                .putLong("ajaeAt", System.currentTimeMillis()).apply();
    }

    private boolean takeAjaePending() {
        android.content.SharedPreferences sp = getSharedPreferences("gutgi", MODE_PRIVATE);
        long at = sp.getLong("ajaeAt", 0);
        if (at == 0) return false;
        sp.edit().remove("ajaeAt").apply();
        return System.currentTimeMillis() - at < 3 * 60 * 1000;
    }

    @Override
    public void sendShotToGeminiApp() {
        AjaeBus.clear();
        Intent i = new Intent(this, ShotActivity.class);
        i.putExtra("sendOnly", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
    }

    @Override
    public void startAjae() {
        commitComposing();
        if (ShotService.alive && panels != null) {
            // 화면 공유 세션이 살아 있으면 동의 창 없이 바로 찍는다 (키보드 위쪽까지만)
            AjaeBus.capturing();
            panels.openAjae();
            int[] loc = new int[2];
            panels.root().getLocationOnScreen(loc);
            ShotService.callback = f -> new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (f == null) AjaeBus.failed("화면을 찍지 못했어요. ↻ 다시 눌러 주세요");
                else AjaeBus.start(this, f);
            });
            Intent cap = new Intent(this, ShotService.class)
                    .setAction(ShotService.ACTION_CAPTURE)
                    .putExtra("cropBottom", loc[1]);
            try {
                startService(cap);
            } catch (Exception e) {
                ShotService.callback = null;
                AjaeBus.clear();
                launchShotActivity();
            }
            return;
        }
        launchShotActivity();
    }

    private void launchShotActivity() {
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
        hc.reset();
        shift = 0;
        if (panels != null) {
            panels.reset();
            if (AjaeBus.shouldShow()) panels.openAjae();
            else if (takeAjaePending()) panels.openHistory();
        }
        lastWord = null;
        dictCache = null;   // 상용구·내 단어 변경 반영
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
                handleEnter(ic);
                return;
            case "@SPACE":
                commitComposing();
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
