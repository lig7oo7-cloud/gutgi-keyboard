package com.lig7oo7.gutgi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 캡처 → API 결과를 키보드 패널에 전달하는 통로. 답이 한 줄씩 도착할 때마다 패널을 다시 그린다 */
public class AjaeBus {
    public static final int IDLE = 0, CAPTURING = 1, LOADING = 2, DONE = 3;

    public static volatile int state = IDLE;
    public static final List<String[]> replies = new ArrayList<>();   // 메인 스레드에서만
    public static volatile String error;
    public static volatile boolean keyProblem;
    public static volatile long startedAt;
    public static volatile Runnable listener;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static int generation;

    public static boolean shouldShow() {
        return state != IDLE && System.currentTimeMillis() - startedAt < 2 * 60 * 1000;
    }

    public static void clear() {
        generation++;
        state = IDLE;
        replies.clear();
        error = null;
        keyProblem = false;
    }

    private static void changed() {
        Runnable l = listener;
        if (l != null) l.run();
    }

    /** 캡처 시작 표시 (키보드가 떠 있는 상태에서 바로 '찍는 중' 보여주기) */
    public static void capturing() {
        clear();
        state = CAPTURING;
        startedAt = System.currentTimeMillis();
        changed();
    }

    public static void failed(String msg) {
        state = DONE;
        error = msg;
        changed();
    }

    public static void start(Context c, File shot) {
        final Context app = c.getApplicationContext();
        final int gen = ++generation;
        state = LOADING;
        replies.clear();
        error = null;
        keyProblem = false;
        startedAt = System.currentTimeMillis();
        changed();
        new Thread(() -> {
            AjaeApi.Result r = AjaeApi.stream(app, shot, (type, text) -> main.post(() -> {
                if (gen != generation) return;
                replies.add(new String[]{type, text});
                changed();
            }));
            main.post(() -> {
                if (gen != generation) return;
                state = DONE;
                if (replies.isEmpty()) { error = r.error; keyProblem = r.keyProblem; }
                changed();
            });
        }).start();
    }
}
