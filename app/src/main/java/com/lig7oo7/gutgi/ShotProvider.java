package com.lig7oo7.gutgi;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** 캡처한 채팅 화면(cache/shots/shot.jpg)을 Gemini 앱이 잠깐 읽을 수 있게 내어주는 통로 */
public class ShotProvider extends ContentProvider {
    public static final String AUTH = "com.lig7oo7.gutgi.shots";

    public static File shotFile(android.content.Context c) {
        File dir = new File(c.getCacheDir(), "shots");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "shot.jpg");
    }

    public static Uri uri(long stamp) {
        return Uri.parse("content://" + AUTH + "/shot_" + stamp + ".jpg");
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/jpeg"; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = shotFile(getContext());
        if (!f.exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        File f = shotFile(getContext());
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{uri.getLastPathSegment(), f.length()});
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
