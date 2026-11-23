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
import java.io.IOException;

/**
 * 키보드가 보낼 GIF를 다른 앱(카톡)에 잠깐 읽게 해 주는 통로.
 * cache/send 폴더의 파일만 읽기 전용으로 제공한다.
 */
public class GifProvider extends ContentProvider {
    public static final String AUTH = "com.lig7oo7.gutgi.gifs";

    public static Uri uriFor(File f) {
        return Uri.parse("content://" + AUTH + "/" + Uri.encode(f.getName()));
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        File dir = new File(getContext().getCacheDir(), "send");
        String name = uri.getLastPathSegment();
        if (name == null) throw new FileNotFoundException();
        File f = new File(dir, name);
        try {
            if (!f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))
                throw new FileNotFoundException();
        } catch (IOException e) {
            throw new FileNotFoundException();
        }
        if (!f.exists()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "image/gif"; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File f = fileFor(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
