package com.dsh.waterline;

import android.content.Context;
import android.content.SharedPreferences;

import com.dsh.waterline.game.Level;

import org.json.JSONArray;
import org.json.JSONObject;

/** Persists the single user-created level in SharedPreferences. */
public final class LevelStore {
    private static final String PREF = "flow_editor_v2";
    private static final String KEY = "level_json_v2";

    private LevelStore() {}

    public static void save(Context ctx, Level L) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString(KEY, toJson(L)).apply();
        } catch (Exception ignored) {}
    }

    public static Level load(Context ctx) {
        try {
            String s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getString(KEY, null);
            if (s != null) {
                Level L = fromJson(s);
                if (L != null) return L;
            }
        } catch (Exception ignored) {}
        return Level.createDefault();
    }

    public static String toJson(Level L) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("name", L.name);
        root.put("faucetX", L.faucetX);
        root.put("cupCx", L.cupCx);
        root.put("cupBottomY", L.cupBottomY);
        JSONArray su = new JSONArray();
        for (Level.Support s : L.supports) {
            JSONArray a = new JSONArray().put(s.x1).put(s.y1).put(s.xm).put(s.ym)
                    .put(s.x2).put(s.y2);
            if (s.motion != null) a.put(motionJson(s.motion));
            if (s.kind != Level.ARC_SUPPORT) {
                if (s.motion == null) a.put(JSONObject.NULL);
                a.put(s.kind);
            }
            su.put(a);
        }
        root.put("supports", su);
        JSONArray sg = new JSONArray();
        for (Level.SegObj s : L.segments) {
            JSONArray a = new JSONArray().put(s.kind).put(s.x1).put(s.y1).put(s.x2).put(s.y2);
            if (s.motion != null) a.put(motionJson(s.motion));
            sg.put(a);
        }
        root.put("segments", sg);
        JSONArray ci = new JSONArray();
        for (Level.CircleObj c : L.circles) {
            JSONArray a = new JSONArray().put(c.kind).put(c.cx).put(c.cy).put(c.radius);
            if (c.motion != null) a.put(motionJson(c.motion));
            ci.put(a);
        }
        root.put("circles", ci);
        JSONArray pf = new JSONArray();
        for (Level.PoisonFaucet p : L.poisonFaucets) {
            pf.put(new JSONArray().put(p.x).put(p.y).put(p.count));
        }
        root.put("poisonFaucets", pf);
        JSONArray tx = new JSONArray();
        for (Level.TextObj t : L.texts) {
            tx.put(new JSONArray().put(t.x).put(t.y).put(t.text));
        }
        root.put("texts", tx);
        JSONArray tr = new JSONArray();
        for (Level.RotateTrigger r : L.triggers) {
            tr.put(new JSONArray().put(r.targetType).put(r.targetIndex)
                    .put(r.offsetX).put(r.offsetY).put(r.angleDeg)
                    .put(r.speedDeg).put(r.count).put(r.mode)
                    .put(r.distance).put(r.moveSpeed)
                    .put(r.hasEndpoint).put(r.endpointRelative)
                    .put(r.endX).put(r.endY));
        }
        root.put("triggers", tr);
        return root.toString();
    }

    public static Level fromJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        Level L = new Level();
        L.name = root.optString("name", "我的关卡");
        L.faucetX = root.optDouble("faucetX", 0.30);
        L.cupCx = root.optDouble("cupCx", 0.30);
        L.cupBottomY = root.optDouble("cupBottomY", 0.930);
        JSONArray su = root.optJSONArray("supports");
        if (su != null) for (int i = 0; i < su.length(); i++) {
            JSONArray a = su.getJSONArray(i);
            if (a.length() >= 6) {
                int kind = a.length() > 7 ? a.optInt(7, Level.ARC_WALL)
                        : Level.ARC_WALL;
                if (kind == Level.ARC_SUPPORT) kind = Level.ARC_WALL;
                Level.Support sp = new Level.Support(a.getDouble(0), a.getDouble(1),
                        a.getDouble(2), a.getDouble(3), a.getDouble(4), a.getDouble(5), kind);
                sp.motion = readMotion(a.optJSONArray(6));
                L.supports.add(sp);
            } else if (a.length() >= 4) {
                double x1 = a.getDouble(0), y1 = a.getDouble(1);
                double x2 = a.getDouble(2), y2 = a.getDouble(3);
                L.supports.add(new Level.Support(x1, y1,
                        (x1 + x2) * 0.5, (y1 + y2) * 0.5 - 0.03, x2, y2));
            }
        }
        JSONArray sg = root.optJSONArray("segments");
        if (sg != null) for (int i = 0; i < sg.length(); i++) {
            JSONArray a = sg.getJSONArray(i);
            Level.SegObj so = new Level.SegObj(a.getInt(0), a.getDouble(1),
                    a.getDouble(2), a.getDouble(3), a.getDouble(4));
            so.motion = readMotion(a.optJSONArray(5));
            L.segments.add(so);
        }
        JSONArray ci = root.optJSONArray("circles");
        if (ci != null) for (int i = 0; i < ci.length(); i++) {
            JSONArray a = ci.getJSONArray(i);
            Level.CircleObj co = new Level.CircleObj(a.getInt(0), a.getDouble(1),
                    a.getDouble(2), a.getDouble(3));
            co.motion = readMotion(a.optJSONArray(4));
            if (co.kind == Level.CIRCLE_FIRE_BALL || co.kind == Level.CIRCLE_BALL
                    || co.kind == Level.CIRCLE_ICE) {
                co.motion = null;
            }
            L.circles.add(co);
        }
        JSONArray pf = root.optJSONArray("poisonFaucets");
        if (pf != null) for (int i = 0; i < pf.length(); i++) {
            JSONArray a = pf.getJSONArray(i);
            L.poisonFaucets.add(new Level.PoisonFaucet(a.getDouble(0),
                    Level.POISON_FAUCET_Y, a.getInt(2)));
        }
        JSONArray tr = root.optJSONArray("triggers");
        if (tr != null) for (int i = 0; i < tr.length(); i++) {
            JSONArray a = tr.getJSONArray(i);
            if (a.length() >= 7) {
                int mode = a.length() > 7 ? a.optInt(7, 0) : 0;
                double dist = a.length() > 8 ? a.optDouble(8, 0) : 0;
                double ms = a.length() > 9 ? a.optDouble(9, 0) : 0;
                boolean hasEnd = a.length() > 10 && a.optBoolean(10, false);
                boolean endRel = a.length() > 11 && a.optBoolean(11, false);
                double endX = a.length() > 12 ? a.optDouble(12, 0) : 0;
                double endY = a.length() > 13 ? a.optDouble(13, 0) : 0;
                L.triggers.add(new Level.RotateTrigger(a.getInt(0), a.getInt(1),
                        a.getDouble(2), a.getDouble(3), a.getDouble(4),
                        a.getDouble(5), a.getInt(6), mode, dist, ms,
                        hasEnd, endRel, endX, endY));
            }
        }
        JSONArray tx = root.optJSONArray("texts");
        if (tx != null) for (int i = 0; i < tx.length(); i++) {
            JSONArray a = tx.getJSONArray(i);
            L.texts.add(new Level.TextObj(a.getDouble(0), a.getDouble(1), a.getString(2)));
        }
        L.cupCx = clamp(L.cupCx, 0.12, 0.48);
        L.cupBottomY = clamp(L.cupBottomY, 0.52, 0.940);
        L.faucetX = clamp(L.faucetX, 0.04, 0.56);
        return L;
    }

    private static JSONArray motionJson(Level.Motion m) throws Exception {
        JSONArray a = new JSONArray().put(m.type).put(m.x1).put(m.y1).put(m.x2).put(m.y2)
                .put(m.radius).put(m.speed).put(m.phase).put(m.loop);
        JSONArray pts = new JSONArray();
        for (Level.Pt p : m.points) pts.put(new JSONArray().put(p.x).put(p.y));
        a.put(pts);
        a.put(m.spin);
        a.put(m.blink);
        a.put(m.blinkInitialVisible);
        a.put(m.blinkVisibleTime);
        a.put(m.blinkHiddenTime);
        a.put(m.blinkCollideHidden);
        return a;
    }

    private static Level.Motion readMotion(JSONArray a) {
        if (a == null || a.length() < 7) return null;
        try {
            Level.Motion m = new Level.Motion(a.getInt(0), a.getDouble(1), a.getDouble(2),
                    a.getDouble(3), a.getDouble(4), a.getDouble(5), a.getDouble(6));
            m.phase = a.optDouble(7, 0);
            m.loop = a.optBoolean(8, false);
            m.spin = a.optDouble(10, 0);
            m.blink = a.optBoolean(11, false);
            m.blinkInitialVisible = a.optBoolean(12, true);
            m.blinkVisibleTime = a.optDouble(13, 1.0);
            m.blinkHiddenTime = a.optDouble(14, 1.0);
            m.blinkCollideHidden = a.optBoolean(15, false);
            JSONArray pts = a.optJSONArray(9);
            if (pts != null) for (int i = 0; i < pts.length(); i++) {
                JSONArray q = pts.getJSONArray(i);
                m.points.add(new Level.Pt(q.getDouble(0), q.getDouble(1)));
            }
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    private static double clamp(double v, double a, double b) {
        return v < a ? a : (v > b ? b : v);
    }
}
