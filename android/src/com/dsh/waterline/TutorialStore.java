package com.dsh.waterline;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** JSON archive of official walkthrough recordings. */
public final class TutorialStore {
    public static final int ACTION_START = 1;
    public static final int ACTION_LINE = 2;
    public static final int ACTION_CLEAR = 3;
    public static final int ACTION_MANUAL_END = 4;

    private static final String FILE = "tutorials.json";
    private static final Object LOCK = new Object();
    public static boolean AUTO_EXPORT = false;
    public static boolean RECORD_OFFICIAL = false;

    private TutorialStore() {}

    public static final class Tutorial {
        public final String levelJson;
        public final long seed;
        public final JSONArray actions = new JSONArray();
        public final JSONArray keyframes = new JSONArray();
        public long savedAt;

        public Tutorial(String levelJson, long seed) {
            this.levelJson = levelJson;
            this.seed = seed;
            this.savedAt = System.currentTimeMillis();
        }

        public void addStart(double t) { add(ACTION_START, t, null); }
        public void addClear(double t) { add(ACTION_CLEAR, t, null); }
        public void addManualEnd(double t) { add(ACTION_MANUAL_END, t, null); }

        public void addLine(double t, double[] xs, double[] ys) {
            try {
                JSONArray pts = new JSONArray();
                int n = Math.min(xs.length, ys.length);
                for (int i = 0; i < n; i++) {
                    pts.put(xs[i]); pts.put(ys[i]);
                }
                add(ACTION_LINE, t, pts);
            } catch (Exception ignored) {}
        }

        public void addKeyframe(JSONObject frame) {
            if (frame != null) keyframes.put(frame);
        }

        private void add(int type, double t, JSONArray pts) {
            try {
                JSONArray a = new JSONArray();
                a.put(Math.max(0, t));
                a.put(type);
                a.put(pts == null ? new JSONArray() : pts);
                actions.put(a);
            } catch (Exception ignored) {}
        }

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("levelJson", levelJson);
                o.put("seed", seed);
                o.put("savedAt", savedAt);
                o.put("actions", actions);
                o.put("keyframes", keyframes);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    private static final int BUNDLED_VERSION = 7;
    private static boolean bundledChecked;
    private static JSONObject cache;
    private static void ensureBundled(Context c) {
        if (bundledChecked) return;
        synchronized (LOCK) {
            if (bundledChecked) return;
            bundledChecked = true;
            try {
                android.content.SharedPreferences p = c.getSharedPreferences(
                        "tutorial_archive", Context.MODE_PRIVATE);
                int version = p.getInt("bundled_version", 0);
                if (version >= BUNDLED_VERSION) return;
                InputStream in = c.getAssets().open("tutorials.json");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                in.close();
                JSONObject bundled = new JSONObject(new String(bo.toByteArray(), "UTF-8"));
                JSONObject root = loadRaw(c);
                JSONObject target = root.optJSONObject("official");
                if (target == null) { target = new JSONObject(); root.put("official", target); }
                JSONObject src = bundled.optJSONObject("official");
                if (src != null) {
                    java.util.Iterator<String> it = src.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        target.put(k, src.get(k));
                    }
                }
                store(c, root);
                p.edit().putInt("bundled_version", BUNDLED_VERSION).apply();
            } catch (Exception ignored) {}
        }
    }

    private static JSONObject load(Context c) {
        ensureBundled(c);
        if (cache != null) return cache;
        synchronized (LOCK) {
            if (cache == null) cache = loadRaw(c);
            return cache;
        }
    }

    private static JSONObject loadRaw(Context c) {
        try {
            File f = file(c);
            if (!f.isFile()) return new JSONObject();
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            return new JSONObject(new String(bo.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void store(Context c, JSONObject root) {
        try {
            root.put("version", 2);
            File f = file(c);
            File tmp = new File(c.getFilesDir(), FILE + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(root.toString().getBytes("UTF-8"));
            out.close();
            if (f.exists() && !f.delete()) { /* overwrite below */ }
            cache = root;
            if (!tmp.renameTo(f)) {
                FileOutputStream direct = new FileOutputStream(f);
                FileInputStream in = new FileInputStream(tmp);
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) direct.write(b, 0, n);
                in.close(); direct.close();
                tmp.delete();
            }
        } catch (Exception ignored) {}
    }

    public static void saveOfficial(Context c, int levelNumber, Tutorial t) {
        synchronized (LOCK) {
            JSONObject root = load(c);
            try {
                JSONObject official = root.optJSONObject("official");
                if (official == null) { official = new JSONObject(); root.put("official", official); }
                official.put(String.valueOf(levelNumber), t.toJson());
                store(c, root);
            } catch (Exception ignored) {}
        }
    }

    public static void saveCustom(Context c, String name, Tutorial t) {
        synchronized (LOCK) {
            JSONObject root = load(c);
            try {
                JSONObject custom = root.optJSONObject("custom");
                if (custom == null) { custom = new JSONObject(); root.put("custom", custom); }
                custom.put(name == null || name.isEmpty() ? "未命名" : name, t.toJson());
                store(c, root);
            } catch (Exception ignored) {}
        }
    }

    public static boolean hasOfficial(Context c, int n) {
        synchronized (LOCK) {
            JSONObject o = load(c).optJSONObject("official");
            return o != null && o.has(String.valueOf(n));
        }
    }

    public static boolean hasCustom(Context c, String name) {
        synchronized (LOCK) {
            JSONObject o = load(c).optJSONObject("custom");
            return o != null && o.has(name == null ? "未命名" : name);
        }
    }

    public static Tutorial getOfficial(Context c, int n) {
        synchronized (LOCK) {
            JSONObject o = load(c).optJSONObject("official");
            return parse(o == null ? null : o.optJSONObject(String.valueOf(n)));
        }
    }

    public static Tutorial getCustom(Context c, String name) {
        synchronized (LOCK) {
            JSONObject o = load(c).optJSONObject("custom");
            return parse(o == null ? null : o.optJSONObject(name == null ? "未命名" : name));
        }
    }

    private static Tutorial parse(JSONObject o) {
        if (o == null) return null;
        try {
            Tutorial t = new Tutorial(o.getString("levelJson"), o.getLong("seed"));
            t.savedAt = o.optLong("savedAt", 0);
            JSONArray a = o.optJSONArray("actions");
            if (a != null) for (int i = 0; i < a.length(); i++) t.actions.put(a.get(i));
            JSONArray k = o.optJSONArray("keyframes");
            if (k != null) for (int i = 0; i < k.length(); i++) t.keyframes.put(k.get(i));
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    public static int countOfficial(Context c) {
        synchronized (LOCK) {
            JSONObject o = load(c).optJSONObject("official");
            return o == null ? 0 : o.length();
        }
    }

    public static String archiveJson(Context c) {
        synchronized (LOCK) { return load(c).toString(); }
    }

    /** Copy the archive to Download/DSH-Go/流水教程/tutorials.json. */
    public static String exportArchive(Context c) {
        String json = archiveJson(c);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = c.getContentResolver();
                Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                String rel = Environment.DIRECTORY_DOWNLOADS + "/DSH-Go/流水教程/";
                Uri existing = null;
                try (android.database.Cursor cur = cr.query(collection,
                        new String[]{MediaStore.Downloads._ID},
                        MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                                + MediaStore.Downloads.RELATIVE_PATH + "=?",
                        new String[]{"tutorials.json", rel}, null)) {
                    if (cur != null && cur.moveToFirst()) {
                        existing = Uri.withAppendedPath(collection,
                                String.valueOf(cur.getLong(0)));
                    }
                }
                if (existing != null) {
                    try (OutputStream out = cr.openOutputStream(existing, "wt")) {
                        if (out != null) out.write(json.getBytes("UTF-8"));
                    }
                    return rel + "tutorials.json";
                }
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, "tutorials.json");
                v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                v.put(MediaStore.Downloads.RELATIVE_PATH, rel);
                Uri uri = cr.insert(collection, v);
                if (uri != null) {
                    try (OutputStream out = cr.openOutputStream(uri, "wt")) {
                        if (out != null) out.write(json.getBytes("UTF-8"));
                    }
                    return rel + "tutorials.json";
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "DSH-Go/流水教程");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "tutorials.json");
                FileOutputStream out = new FileOutputStream(f);
                out.write(json.getBytes("UTF-8"));
                out.close();
                return f.getAbsolutePath();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Keep old tutorial slot numbers in sync when 2.9 becomes position 3. */
    public static void migrateForInsertedLevel(Context c) {
        synchronized (LOCK) {
            android.content.SharedPreferences p = c.getSharedPreferences(
                    "tutorial_archive", Context.MODE_PRIVATE);
            if (p.getInt("levels_version", 1) >= 2) return;
            JSONObject root = loadRaw(c);
            JSONObject old = root.optJSONObject("official");
            if (old != null) {
                try {
                    JSONObject shifted = new JSONObject();
                    for (int i = 12; i >= 4; i--) {
                        String from = String.valueOf(i - 1);
                        if (old.has(from)) shifted.put(String.valueOf(i), old.get(from));
                    }
                    if (old.has("1")) shifted.put("1", old.get("1"));
                    if (old.has("2")) shifted.put("2", old.get("2"));
                    root.put("official", shifted);
                    store(c, root);
                } catch (Exception ignored) {}
            }
            p.edit().putInt("levels_version", 2).apply();
        }
    }
}
