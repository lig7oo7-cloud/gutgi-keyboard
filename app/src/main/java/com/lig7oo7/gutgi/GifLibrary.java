package com.lig7oo7.gutgi;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** 캡처 앱이 저장한 Pictures/EmotionGif 의 GIF 목록 */
public class GifLibrary {
    public static final String FOLDER = "EmotionGif";

    public static class Item {
        public final Uri uri;
        public final String name;
        public final String tag;
        Item(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
            int i = name.indexOf('_');
            this.tag = i > 0 ? name.substring(0, i) : "기타";
        }
    }

    public static String permission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED
                || (Build.VERSION.SDK_INT >= 34 && c.checkSelfPermission(
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED);
    }

    public static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        if (!hasPermission(c)) return out;
        Uri base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME};
        String sel;
        String[] args;
        if (Build.VERSION.SDK_INT >= 29) {
            sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? AND " + MediaStore.Images.Media.MIME_TYPE + "=?";
            args = new String[]{"Pictures/" + FOLDER + "%", "image/gif"};
        } else {
            sel = MediaStore.Images.Media.DATA + " LIKE ?";
            args = new String[]{"%/Pictures/" + FOLDER + "/%.gif"};
        }
        try (Cursor cur = c.getContentResolver().query(base, proj, sel, args,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                long id = cur.getLong(0);
                String name = cur.getString(1);
                if (name == null) continue;
                out.add(new Item(ContentUris.withAppendedId(base, id), name));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** 작은 미리보기 (첫 프레임) */
    public static Bitmap thumb(Context c, Uri uri, int maxPx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= maxPx) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 보낼 파일을 키보드 캐시로 복사 (다른 앱에 권한을 넘겨줄 수 있도록) */
    public static File copyToCache(Context c, Item item) throws Exception {
        File dir = new File(c.getCacheDir(), "send");
        if (!dir.exists()) dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null && old.length > 20) for (File f : old) f.delete();
        File f = new File(dir, item.name.replaceAll("[\\\\/]", "_"));
        try (InputStream in = c.getContentResolver().openInputStream(item.uri);
             OutputStream out = new FileOutputStream(f)) {
            if (in == null) throw new IllegalStateException("열 수 없음");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return f;
    }
}
