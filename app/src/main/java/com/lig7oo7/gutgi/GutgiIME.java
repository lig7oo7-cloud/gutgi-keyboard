package com.lig7oo7.gutgi;

import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.List;

public class GutgiIME extends InputMethodService implements KeyboardView.Listener {

    private static final int HAN = 0, ENG = 1, SYM = 2;

    private KeyboardView view;
    private final HangulComposer hc = new HangulComposer();
    private List<List<KeyDef>> hanRows, engRows, symRows;
    private int mode = HAN, langMode = HAN;
    private int shift; // 0 끔, 1 한 번, 2 고정
    private long lastShiftTap;

    @Override
    public View onCreateInputView() {
        hanRows = Layouts.hangul();
        engRows = Layouts.english();
        symRows = Layouts.symbols();
        view = new KeyboardView(this);
        view.setListener(this);
        applyLayout();
        return view;
    }

    @Override
    public boolean onEvaluateFullscreenMode() { return false; }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        hc.reset();
        shift = 0;
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
