package io.github.rsgarrido.sazanami.lyrics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Java/Android only: Android also starts this test APK component outside instrumentation. */
public final class LyricsTransferFixtureProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.rsgarrido.sazanami.test.lyrics.transfer";
    public static Uri uri(String name) { return Uri.parse("content://" + AUTHORITY + "/" + name); }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "text/plain"; }

    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if ("denied".equals(name)) throw new SecurityException("Synthetic document permission loss");
        if ("broken-output".equals(name) && !"r".equals(mode)) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                pipe[0].close();
                return pipe[1];
            } catch (IOException failure) { throw failed(name, failure); }
        }
        Context context = getContext();
        if (context == null) throw new IllegalStateException("Provider not attached");
        boolean destination = "export.lrc".equals(name) || "export.txt".equals(name);
        if (destination) {
            File file = new File(context.getCacheDir(), "lyrics-transfer-" + name);
            if ("r".equals(mode)) return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            if (!"w".equals(mode) && !"wt".equals(mode)) throw new SecurityException("Unsupported fixture mode");
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY |
                    ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        if (!"r".equals(mode)) throw new SecurityException("Read-only source fixture");
        byte[] bytes;
        if ("plain.lrc".equals(name)) bytes = "Original plain text\r\nSecond original line".getBytes(StandardCharsets.UTF_8);
        else if ("timed.txt".equals(name)) bytes = "[00:01.250]Original timed line".getBytes(StandardCharsets.UTF_8);
        else if ("enhanced.txt".equals(name)) bytes = "[xx:Keep]\r\n[offset:-100]\r\n[00:01]<00:01>Paper <00:01.500>stars".getBytes(StandardCharsets.UTF_8);
        else if ("invalid".equals(name)) bytes = new byte[] {(byte) 0xff};
        else if ("empty".equals(name)) bytes = new byte[0];
        // One byte beyond the public editor input budget; no production-class dependency.
        else if ("oversized".equals(name)) bytes = new byte[256 * 1024 + 1];
        else throw new FileNotFoundException("Unknown transfer fixture");
        File file = new File(context.getCacheDir(), "lyrics-transfer-" + name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        } catch (IOException failure) { throw failed(name, failure); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    private static FileNotFoundException failed(String name, IOException cause) {
        FileNotFoundException result = new FileNotFoundException("Transfer fixture failed: " + name);
        result.initCause(cause);
        return result;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[] {"provider-" + uri.getLastPathSegment()});
        return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
