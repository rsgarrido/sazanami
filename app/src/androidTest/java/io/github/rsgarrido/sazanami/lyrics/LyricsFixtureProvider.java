package io.github.rsgarrido.sazanami.lyrics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Test APK only; serves synthetic fixtures through real cross-process ContentResolver calls.
 *
 * Android starts this provider in the test application's own process, outside the instrumented
 * target's combined classpath. Shared Kotlin runtime classes live in the target APK, so this
 * component must use only Android/Java APIs, including static initialization and file copying.
 */
public final class LyricsFixtureProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.rsgarrido.sazanami.test.lyrics";
    public static final String REGISTER_AUDIO = "register-audio";
    public static final String CLEAR_AUDIO = "clear-audio";
    public static final String DESCRIPTOR = "descriptor";
    public static final String REGISTERED = "registered";

    private final Map<String, ParcelFileDescriptor> audioDescriptors = new HashMap<>();

    public static final Set<String> FIXTURES = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("synthetic.mp3", "synthetic.flac", "synthetic.ogg", "synthetic.m4a",
                    "synthetic.wav", "synthetic.aiff", "multiple.mp3", "plain.mp3",
                    "sidecar.lrc", "plain.lrc")));

    public static Uri uri(String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + name);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "application/octet-stream";
    }

    /**
     * Borrow target-owned fixture inodes without opening their private paths in this process.
     * A Binder fd grant permits reading an already-open file, but does not grant permission to
     * reopen another app's private inode through /proc/self/fd. chmod cannot change SELinux labels.
     * Only audio needs reopening by the path-based tag library; sidecars remain provider-owned.
     */
    @Override
    public synchronized Bundle call(String method, String name, Bundle extras) {
        if (CLEAR_AUDIO.equals(method)) {
            for (ParcelFileDescriptor descriptor : audioDescriptors.values()) {
                closeDescriptor(descriptor);
            }
            audioDescriptors.clear();
            return new Bundle();
        }
        if (!REGISTER_AUDIO.equals(method)) return super.call(method, name, extras);
        if (!FIXTURES.contains(name) || name.endsWith(".lrc") || extras == null) {
            throw new IllegalArgumentException("Unknown audio fixture");
        }
        ParcelFileDescriptor incoming = extras.getParcelable(DESCRIPTOR);
        if (incoming == null) throw new IllegalArgumentException("Missing fixture descriptor");
        try (ParcelFileDescriptor received = incoming) {
            ParcelFileDescriptor retained = ParcelFileDescriptor.dup(received.getFileDescriptor());
            closeDescriptor(audioDescriptors.put(name, retained));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot register fixture: " + name, failure);
        }
        Bundle result = new Bundle();
        result.putBoolean(REGISTERED, true);
        return result;
    }

    private static void closeDescriptor(ParcelFileDescriptor descriptor) {
        if (descriptor == null) return;
        try {
            descriptor.close();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot close fixture descriptor", failure);
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("Read-only fixtures");
        String name = uri.getLastPathSegment();
        if (name == null) throw new FileNotFoundException("Missing fixture name");
        if ("denied".equals(name)) throw new SecurityException("Synthetic permission loss");
        if ("pipe".equals(name)) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                pipe[1].close();
                return pipe[0];
            } catch (IOException failure) {
                throw fileFailure(name, failure);
            }
        }
        if (!FIXTURES.contains(name)) throw new FileNotFoundException("Unknown fixture: " + name);

        if (!name.endsWith(".lrc")) {
            synchronized (this) {
                ParcelFileDescriptor descriptor = audioDescriptors.get(name);
                if (descriptor == null) throw new FileNotFoundException("Audio fixture not registered: " + name);
                try {
                    return ParcelFileDescriptor.dup(descriptor.getFileDescriptor());
                } catch (IOException failure) {
                    throw fileFailure(name, failure);
                }
            }
        }

        Context owner = getContext();
        if (owner == null) throw new IllegalStateException("Fixture provider is not attached");
        File file = new File(owner.getCacheDir(), "lyrics-fixture-" + name);
        try (InputStream input = owner.getAssets().open(name);
             FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        } catch (IOException failure) {
            // Preserve the underlying asset/copy exception when reporting a file failure.
            throw fileFailure(name, failure);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static FileNotFoundException fileFailure(String name, IOException cause) {
        FileNotFoundException failure = new FileNotFoundException("Cannot prepare fixture: " + name);
        failure.initCause(cause);
        return failure;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
