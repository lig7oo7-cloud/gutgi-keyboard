package com.lig7oo7.gutgi;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * 키보드 캡처 버튼용 접근성 서비스.
 * 화면 내용은 읽지 않고(canRetrieveWindowContent=false), 시스템 스크린샷 명령만 보낸다.
 */
public class ShotAccess extends AccessibilityService {
    private static volatile ShotAccess instance;

    public static boolean isOn() { return instance != null; }

    public static boolean shoot() {
        ShotAccess s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }

    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
