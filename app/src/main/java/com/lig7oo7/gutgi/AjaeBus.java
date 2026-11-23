package com.lig7oo7.gutgi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/** 캡처 → API 결과를 키보드 패널에 전달하는 통로 (같은 앱 안) */
public class AjaeBus {
    public static final int IDLE = 0, LOADING = 1, DONE = 2;

    public static volatile int state = IDLE;
    public static volatile AjaeApi.Result result;
    public static volatile long startedAt;
    public static volatile Runnable listener;   // 패널이 열려 있으면 다시 그리기

    private static final Handler main = new Handler(Looper.getMainLooper());

    /** 최근 2분 안의 요청이면 키보드가 다시 뜰 때 답장 패널을 연다 */
    public static boolean shouldShow() {
        return state != IDLE && System.currentTimeMillis() - startedAt < 2 * 60 * 1000;
    }

    public static void clear() {
        state = IDLE;
        result = null;
    }

    public static void start(Context c, File shot) {
        final Context app = c.getApplicationContext();
        state = LOADING;
        result = null;
        startedAt = System.currentTimeMillis();
        new Thread(() -> {
            AjaeApi.Result r = AjaeApi.call(app, shot);
            main.post(() -> {
                result = r;
                state = DONE;
                Runnable l = listener;
                if (l != null) l.run();
            });
        }).start();
    }
}
