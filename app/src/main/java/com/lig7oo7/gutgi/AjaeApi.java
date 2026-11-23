package com.lig7oo7.gutgi;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemini API로 채팅 캡처를 보내 답장 후보를 받는다.
 * 버튼을 눌렀을 때 캡처 이미지 1장만 보낸다. 키보드 입력 내용은 보내지 않는다.
 */
public class AjaeApi {

    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

    public static class Result {
        public final List<String[]> replies = new ArrayList<>();   // {종류, 문장}
        public String error;
        public boolean quotaOrKey;   // 사용량 초과·키 문제 → 앱 방식으로 넘어가기
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("gutgi", Context.MODE_PRIVATE);
    }

    public static String key(Context c) { return prefs(c).getString("geminiKey", "").trim(); }
    public static String model(Context c) {
        String m = prefs(c).getString("geminiModel", "").trim();
        return m.isEmpty() ? DEFAULT_MODEL : m;
    }
    public static boolean spicy(Context c) { return prefs(c).getBoolean("spicy", true); }

    static String prompt(boolean spicy) {
        return "이 카카오톡 대화 화면을 보고, 상대방이 마지막으로 보낸 메시지에 대한 답장을 만들어 줘.\n" +
                "- 아재개그 2개: 썰렁한 말장난, 동음이의어·발음 비슷한 단어 활용\n" +
                "- 언어유희 2개: 단어를 비틀거나 끝말을 이어 재치 있게\n" +
                (spicy ? "- 야한 농담 2개: 성인끼리 주고받는 은근한 19금 말장난. 노골적인 표현 없이 웃기게\n" : "") +
                "규칙: 대화 맥락에 맞게, 각 답장은 한 줄 35자 이내, 이모지·해시태그·설명 금지, 화면 아래 키보드 부분은 무시.\n" +
                "JSON으로만 답해: {\"replies\":[{\"type\":\"아재개그\",\"text\":\"...\"}]}";
    }

    public static Result call(Context c, File image) {
        Result r = new Result();
        String key = key(c);
        if (key.isEmpty()) { r.error = "API 키가 없어요. 굿기 키보드 앱에 키를 넣고 [AI 설정 저장]을 눌러 주세요"; r.quotaOrKey = true; return r; }
        HttpURLConnection con = null;
        try {
            String b64 = Base64.encodeToString(readAll(new FileInputStream(image)), Base64.NO_WRAP);
            JSONObject body = new JSONObject();
            JSONArray parts = new JSONArray()
                    .put(new JSONObject().put("text", prompt(spicy(c))))
                    .put(new JSONObject().put("inline_data",
                            new JSONObject().put("mime_type", "image/jpeg").put("data", b64)));
            body.put("contents", new JSONArray().put(new JSONObject().put("parts", parts)));
            body.put("generationConfig", new JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 1.0));
            JSONArray safety = new JSONArray();
            for (String cat : new String[]{"HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_HARASSMENT"}) {
                safety.put(new JSONObject().put("category", cat).put("threshold", "BLOCK_ONLY_HIGH"));
            }
            body.put("safetySettings", safety);

            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                    + model(c) + ":generateContent");
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(40000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setRequestProperty("x-goog-api-key", key);
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }
            int code = con.getResponseCode();
            InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
            String text = in == null ? "" : new String(readAll(in), "UTF-8");
            if (code == 429) { r.error = "사용량 한도 초과 (429). AI Studio에서 결제를 켜면 계속 쓸 수 있어요"; r.quotaOrKey = true; return r; }
            if (code == 400 || code == 401 || code == 403) {
                r.error = "API 키 또는 모델 설정을 확인하세요 (" + code + ")";
                r.quotaOrKey = true;
                return r;
            }
            if (code >= 400) { r.error = "AI 서버 오류 (" + code + ")"; r.quotaOrKey = true; return r; }

            JSONObject res = new JSONObject(text);
            JSONArray cands = res.optJSONArray("candidates");
            if (cands == null || cands.length() == 0) { r.error = "답을 받지 못했어요 (내용이 걸러졌을 수 있어요)"; return r; }
            JSONArray outParts = cands.getJSONObject(0).optJSONObject("content") == null ? null
                    : cands.getJSONObject(0).getJSONObject("content").optJSONArray("parts");
            StringBuilder sb = new StringBuilder();
            if (outParts != null) for (int i = 0; i < outParts.length(); i++) sb.append(outParts.getJSONObject(i).optString("text"));
            String json = sb.toString().trim();
            int s = json.indexOf('{'), e = json.lastIndexOf('}');
            if (s >= 0 && e > s) json = json.substring(s, e + 1);
            JSONArray reps = new JSONObject(json).optJSONArray("replies");
            if (reps != null) {
                for (int i = 0; i < reps.length(); i++) {
                    JSONObject o = reps.getJSONObject(i);
                    String t = o.optString("text").trim();
                    if (!t.isEmpty()) r.replies.add(new String[]{o.optString("type", ""), t});
                }
            }
            if (r.replies.isEmpty()) r.error = "답을 읽지 못했어요. 다시 시도해 주세요";
        } catch (java.net.UnknownHostException | java.net.SocketTimeoutException e) {
            r.error = "인터넷 연결을 확인하세요";
            r.quotaOrKey = true;
        } catch (Exception e) {
            r.error = "오류: " + e.getClass().getSimpleName();
        } finally {
            if (con != null) con.disconnect();
        }
        return r;
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
