package com.lig7oo7.gutgi;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
  * Gemini API로 채팅 캡처를 보내 평범한 답장 후보를 '스트리밍'으로 받는다 (첫 답부터 바로 표시).
 * 버튼을 눌렀을 때 캡처 이미지 1장만 보낸다. 키보드 입력 내용은 보내지 않는다.
 */
public class AjaeApi {

    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

    public static final String DEFAULT_STYLE =
            "짧고 담백하게. 친한 사이엔 반말, 상대가 존댓말이면 존댓말.";

    public interface OnReply { void reply(String type, String text); }

    public static class Result {
        public int count;
        public String error;
        public boolean keyProblem;
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("gutgi", Context.MODE_PRIVATE);
    }

    public static String key(Context c) { return prefs(c).getString("geminiKey", "").trim(); }
    public static String model(Context c) {
        String m = prefs(c).getString("geminiModel", "").trim();
        return m.isEmpty() ? DEFAULT_MODEL : m;
    }
    public static String style(Context c) { return prefs(c).getString("style", DEFAULT_STYLE); }

    static String prompt(Context c) {
        return "캡처된 카카오톡 대화에서 상대가 마지막으로 보낸 메시지에 보낼 자연스러운 답장을 만들어라.\n" +
                "대화 맥락과 두 사람의 관계(말투, 존댓말 여부)를 보고 내가 실제로 보낼 법한 평범한 문자로.\n" +
                "농담·과장·이모지·해시태그는 쓰지 마.\n\n" +
                "[내 말투]\n" + style(c) + "\n\n" +
                "[만들 것] 서로 다른 답장 4개\n" +
                "- 짧게: 한두 마디 대답\n" +
                "- 공감: 상대 말에 맞장구\n" +
                "- 질문: 대화를 이어가는 물음\n" +
                "- 정중: 조금 더 예의 있게\n" +
                "[규칙] 한 줄 40자 이내, 따옴표·번호·설명 금지. 화면 아래 키보드는 무시.\n" +
                "출력은 한 줄에 하나씩 '종류|답장' 형식만. 예) 짧게|응 알겠어";
    }

    /** 블로킹 호출. 답장이 한 줄 완성될 때마다 onReply 호출 (작업 스레드) */
    public static Result stream(Context c, File image, OnReply onReply) {
        Result r = new Result();
        String key = key(c);
        if (key.isEmpty()) {
            r.error = "API 키가 없어요. 굿기 키보드 앱에 키를 넣고 [AI 설정 저장]을 눌러 주세요";
            r.keyProblem = true;
            return r;
        }
        String b64;
        try {
            b64 = Base64.encodeToString(readAll(new FileInputStream(image)), Base64.NO_WRAP);
        } catch (Exception e) {
            r.error = "캡처 파일을 읽지 못했어요";
            return r;
        }
        // 생각 단계를 최소로 해서 빠르게. 모델이 이 설정을 모르면 빼고 다시 요청
        int code = request(c, key, b64, true, onReply, r);
        if (code == 400 && r.count == 0) {
            r.error = null;
            r.keyProblem = false;
            request(c, key, b64, false, onReply, r);
        }
        return r;
    }

    private static int request(Context c, String key, String b64, boolean fastThinking, OnReply onReply, Result r) {
        HttpURLConnection con = null;
        try {
            JSONObject body = new JSONObject();
            JSONArray parts = new JSONArray()
                    .put(new JSONObject().put("text", prompt(c)))
                    .put(new JSONObject().put("inline_data",
                            new JSONObject().put("mime_type", "image/jpeg").put("data", b64)));
            body.put("contents", new JSONArray().put(new JSONObject().put("parts", parts)));
            JSONObject gen = new JSONObject().put("temperature", 0.7).put("maxOutputTokens", 400);
            if (fastThinking) gen.put("thinkingConfig", new JSONObject().put("thinkingLevel", "minimal"));
            body.put("generationConfig", gen);
            JSONArray safety = new JSONArray();
            for (String cat : new String[]{"HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_HARASSMENT"}) {
                safety.put(new JSONObject().put("category", cat).put("threshold", "BLOCK_ONLY_HIGH"));
            }
            body.put("safetySettings", safety);

            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                    + model(c) + ":streamGenerateContent?alt=sse");
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(30000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setRequestProperty("x-goog-api-key", key);
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }
            int code = con.getResponseCode();
            if (code >= 400) {
                if (code == 429) r.error = "사용량 한도 초과 (429). AI Studio에서 결제를 켜면 계속 쓸 수 있어요";
                else if (code == 400 || code == 401 || code == 403) r.error = "API 키 또는 모델 설정을 확인하세요 (" + code + ")";
                else r.error = "AI 서버 오류 (" + code + ")";
                r.keyProblem = code == 400 || code == 401 || code == 403 || code == 429;
                return code;
            }
            StringBuilder pending = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String json = line.substring(5).trim();
                    if (json.isEmpty()) continue;
                    JSONArray cands = new JSONObject(json).optJSONArray("candidates");
                    if (cands == null || cands.length() == 0) continue;
                    JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                    JSONArray ps = content == null ? null : content.optJSONArray("parts");
                    if (ps == null) continue;
                    for (int i = 0; i < ps.length(); i++) {
                        JSONObject p = ps.getJSONObject(i);
                        if (p.optBoolean("thought")) continue;
                        pending.append(p.optString("text"));
                    }
                    // 완성된 줄마다 바로 내보내기
                    int nl;
                    while ((nl = pending.indexOf("\n")) >= 0) {
                        emit(pending.substring(0, nl), onReply, r);
                        pending.delete(0, nl + 1);
                    }
                }
            }
            emit(pending.toString(), onReply, r);
            if (r.count == 0) r.error = "답을 받지 못했어요 (내용이 걸러졌을 수 있어요). ↻ 다시 눌러 보세요";
            return code;
        } catch (java.net.UnknownHostException | java.net.SocketTimeoutException e) {
            r.error = "인터넷 연결을 확인하세요";
            return -1;
        } catch (Exception e) {
            if (r.count == 0) r.error = "오류: " + e.getClass().getSimpleName();
            return -1;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static void emit(String raw, OnReply onReply, Result r) {
        String s = raw.trim();
        if (s.isEmpty() || s.startsWith("```")) return;
        String type = "";
        int bar = s.indexOf('|');
        if (bar > 0 && bar < 8) {
            type = s.substring(0, bar).trim();
            s = s.substring(bar + 1).trim();
        }
        s = s.replaceAll("^(?:[-•*]|\\d+[.)])\\s*", "").replaceAll("^[\"'“”]+|[\"'“”]+$", "").trim();
        if (s.isEmpty()) return;
        r.count++;
        onReply.reply(type, s);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }
}
