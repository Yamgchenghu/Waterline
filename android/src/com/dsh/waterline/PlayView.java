package com.dsh.waterline;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import com.dsh.waterline.game.GameWorld;
import com.dsh.waterline.game.Level;
import com.dsh.waterline.game.Tags;
import com.dsh.waterline.physics.Bar;
import com.dsh.waterline.physics.Drop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

/** Frozen-until-start playfield for the user's saved level. */
public final class PlayView extends View {
    private static final int C_BG = Color.rgb(6, 16, 25);
    private static final int C_CYAN = Color.rgb(86, 215, 255);
    private static final int C_MINT = Color.rgb(100, 240, 200);
    private static final int C_GOLD = Color.rgb(255, 200, 92);
    private static final int C_CORAL = Color.rgb(255, 122, 122);
    private static final int C_TEXT = Color.rgb(232, 248, 255);
    private static final int C_DIM = Color.rgb(142, 176, 194);

    private static final int OV_NONE = 0, OV_INFO = 1, OV_WIN = 2, OV_LOSE = 3;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Path path = new Path();
    private final RectF tmpRect = new RectF();
    private final RectF playRect = new RectF();
    private final RectF btnEdit = new RectF();
    private final RectF btnInfo = new RectF();
    private final RectF btnStart = new RectF();
    private final RectF btnClear = new RectF();
    private final RectF btnRestart = new RectF();
    private final RectF overlayPrimary = new RectF();
    private final RectF overlaySecondary = new RectF();
    private final RectF overlayCard = new RectF();
    private Paint waterPaint, poisonPaint, dropRim, dropHi;
    private Bitmap fireRingSprite;

    private Level level;
    private int levelIndex;
    private GameWorld world;
    private SharedPreferences prefs;
    private boolean winSaved;
    private final boolean editorMode;
    private final TutorialStore.Tutorial tutorial;
    private final boolean tutorialMode;
    private TutorialStore.Tutorial recording;
    private int tutorialAction;
    private boolean tutorialSaved;
    private float tutorialRealElapsed;
    private boolean tutorialInitialLines;
    private double lastKeyframeTime = -1;
    private boolean forceKeyframe;
    private final Map<Integer, double[]> prevDropV = new HashMap<>();
    private final Map<Integer, double[]> prevBarV = new HashMap<>();
    private String levelJson;
    private float density;
    private int safeTop, safeBottom;
    private float ppm = 1f;
    private int layoutW = -1, layoutH = -1;
    private boolean running, drawing;
    private float downX, downY, draftSx, draftSy, draftEx, draftEy;
    private float draftSnapAx, draftSnapAy, draftSnapBx, draftSnapBy;
    private int draftSupportA = -1, draftSupportB = -1;
    private int tapSupport = -1;
    private float tapX, tapY;
    private final ArrayList<Float> draftXs = new ArrayList<>();
    private final ArrayList<Float> draftYs = new ArrayList<>();
    private String pressed;
    private int overlay = OV_NONE;
    private float uiAnim;
    private GameWorld.State lastState = GameWorld.State.READY;
    private float resultDelay;
    private float vTime;
    private long lastFrame;

    public PlayView(Context c, Level level, int levelIndex) {
        this(c, level, levelIndex, false, null);
    }

    public PlayView(Context c, Level level, int levelIndex, boolean editorMode) {
        this(c, level, levelIndex, editorMode, null);
    }

    public PlayView(Context c, Level level, int levelIndex, boolean editorMode,
                    TutorialStore.Tutorial playback) {
        super(c, null);
        density = getResources().getDisplayMetrics().density;
        try (java.io.InputStream in = c.getAssets().open("fire_ring.png")) {
            fireRingSprite = BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {}
        this.editorMode = editorMode;
        this.levelIndex = levelIndex;
        this.tutorial = playback;
        this.tutorialMode = playback != null;
        this.prefs = c.getSharedPreferences("official_progress", Context.MODE_PRIVATE);
        if (playback != null) {
            Level replay = null;
            try { replay = LevelStore.fromJson(playback.levelJson); } catch (Exception ignored) {}
            this.level = replay != null ? replay : level;
            levelJson = playback.levelJson;
            world = new GameWorld(this.level, playback.seed);
        } else {
            this.level = level;
            try { levelJson = LevelStore.toJson(level); } catch (Exception ignored) {}
            long seed = new java.util.Random().nextLong();
            world = new GameWorld(level, seed);
            if (levelJson != null) recording = new TutorialStore.Tutorial(levelJson, seed);
        }
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(C_BG);
        setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                safeTop = insets.getDisplayCutout().getSafeInsetTop();
            } else safeTop = insets.getSystemWindowInsetTop();
            safeBottom = insets.getSystemWindowInsetBottom();
            layoutW = -1;
            updateLayout();
            return insets;
        });
    }

    public void onResumeGame() { running = true; lastFrame = 0; invalidate(); postInvalidateOnAnimation(); }
    public void onPauseGame() { running = false; drawing = false; }

    private float dp(float v) { return v * density; }
    private float X(double wx) { return (float) (playRect.left + wx * ppm); }
    private float Y(double wy) { return (float) (playRect.top + wy * ppm); }
    private float S(double m) { return (float) (m * ppm); }
    private float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutW = -1;
        updateLayout();
    }

    private void updateLayout() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (layoutW == w && layoutH == h) return;
        layoutW = w; layoutH = h;
        float top = safeTop + dp(60);
        float bottom = h - safeBottom - dp(86);
        float availH = Math.max(dp(240), bottom - top);
        float side = dp(8);
        float s = Math.min((w - side * 2) / 0.60f, availH);
        ppm = s;
        float pw = 0.60f * s, ph = s;
        playRect.set((w - pw) * 0.5f, top + (availH - ph) * 0.5f,
                (w + pw) * 0.5f, top + (availH - ph) * 0.5f + ph);
        btnEdit.set(dp(12), safeTop + dp(9), dp(12) + dp(92), safeTop + dp(49));
        btnInfo.set(w - dp(12) - dp(40), safeTop + dp(9), w - dp(12), safeTop + dp(49));
        float barY = h - safeBottom - dp(14);
        float total = w - dp(24), gap = dp(8);
        float startW = total * 0.56f, clearW = total * 0.20f;
        float restartW = total - startW - clearW - gap * 2;
        float x = dp(12);
        btnStart.set(x, barY - dp(52), x + startW, barY);
        x += startW + gap;
        btnClear.set(x, barY - dp(52), x + clearW, barY);
        x += clearW + gap;
        btnRestart.set(x, barY - dp(52), x + restartW, barY);
        layoutOverlay();
    }

    private void layoutOverlay() {
        float w = Math.min(getWidth() - dp(30), dp(378));
        float h = Math.min(getHeight() - safeTop - safeBottom - dp(24),
                overlay == OV_INFO ? dp(500) : dp(438));
        float cx = getWidth() * 0.5f;
        float cy = safeTop + (getHeight() - safeTop - safeBottom) * 0.5f;
        overlayCard.set(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f);
        float l = overlayCard.left + dp(24), r = overlayCard.right - dp(24);
        float b = overlayCard.bottom - dp(18), bh = dp(48);
        if (overlay == OV_INFO) {
            overlayPrimary.set(0, 0, 0, 0);
            overlaySecondary.set(l + dp(30), b - bh, r - dp(30), b);
        } else {
            float second = (r - l) * 0.36f;
            overlaySecondary.set(r - second, b - bh, r, b);
            overlayPrimary.set(l, b - bh, r - second - dp(9), b);
        }
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (layoutW != getWidth() || layoutH != getHeight()) updateLayout();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1_000_000_000f;
        lastFrame = now;
        if (dt > 0.05f) dt = 0.05f;
        vTime += dt;
        if (world.pixelsPerMeter != ppm) world.pixelsPerMeter = ppm;
        if (tutorialMode) {
            tutorialRealElapsed += dt;
            if (!tutorialInitialLines) {
                tutorialInitialLines = true;
                applyInitialTutorialLines();
            }
            if (tutorialRealElapsed >= 1.0f && overlay == OV_NONE) {
                applyTutorialActions();
                if (world.state == GameWorld.State.READY) world.startWater();
                double t = world.runTime;
                float speed;
                if (t < 2.5) speed = 0.5f;
                else if (t < 5.0) speed = 0.5f + 0.5f * (float) ((t - 2.5) / 2.5);
                else speed = 1.0f;
                world.update(dt * speed);
            }
        } else if (overlay == OV_NONE) {
            world.update(dt);
        }
        checkState(dt);
        recordKeyframe(false);
        drawAll(c);
        if (running) postInvalidateOnAnimation();
    }

    private void recordKeyframe(boolean force) {
        if (recording == null || tutorialMode) return;
        double now = world.runTime;
        if (!force && lastKeyframeTime >= 0 && now - lastKeyframeTime < 0.25) return;
        if (!force && lastKeyframeTime == 0 && now == 0) return;
        double dt = lastKeyframeTime < 0 ? 0 : now - lastKeyframeTime;
        try {
            JSONObject f = new JSONObject();
            f.put("t", now);
            f.put("state", world.state.name());
            f.put("dropsInCup", world.dropsInCup);
            f.put("spawned", world.spawned);

            JSONArray drops = new JSONArray();
            for (com.dsh.waterline.physics.Drop d : world.physics.drops) {
                double[] prev = prevDropV.get(d.index);
                double ax = 0, ay = 0;
                if (dt > 1e-6 && prev != null) {
                    ax = (d.vx - prev[0]) / dt;
                    ay = (d.vy - prev[1]) / dt;
                }
                prevDropV.put(d.index, new double[]{d.vx, d.vy});
                JSONArray o = new JSONArray();
                o.put(d.index);
                o.put(d.x); o.put(d.y);
                o.put(d.angle);
                o.put(d.vx); o.put(d.vy);
                o.put(Math.hypot(d.vx, d.vy));
                o.put(ax); o.put(ay); o.put(Math.hypot(ax, ay));
                o.put(d.poison ? 1 : 0);
                o.put(d.collected ? 1 : 0);
                o.put(d.removed ? 1 : 0);
                o.put(d.sleeping ? 1 : 0);
                drops.put(o);
            }
            f.put("drops", drops);

            JSONArray bars = new JSONArray();
            for (int i = 0; i < world.physics.bars.size(); i++) {
                com.dsh.waterline.physics.Bar b = world.physics.bars.get(i);
                double[] prev = prevBarV.get(i);
                double ax = 0, ay = 0, alpha = 0;
                if (dt > 1e-6 && prev != null) {
                    ax = (b.vx - prev[0]) / dt;
                    ay = (b.vy - prev[1]) / dt;
                    alpha = (b.omega - prev[2]) / dt;
                }
                prevBarV.put(i, new double[]{b.vx, b.vy, b.omega});
                JSONArray o = new JSONArray();
                o.put(i);
                o.put(b.tag);
                o.put(b.x); o.put(b.y); o.put(b.angle);
                o.put(b.vx); o.put(b.vy); o.put(Math.hypot(b.vx, b.vy));
                o.put(b.omega); o.put(alpha);
                o.put(ax); o.put(ay); o.put(Math.hypot(ax, ay));
                o.put(b.fixed ? 1 : 0);
                o.put(b.kinematic ? 1 : 0);
                o.put(b.sleeping ? 1 : 0);
                o.put(b.radius);
                o.put(b.pointCount);
                JSONArray pts = new JSONArray();
                for (int k = 0; k < b.pointCount; k++) {
                    pts.put(b.px(k)); pts.put(b.py(k));
                }
                o.put(pts);
                bars.put(o);
            }
            f.put("bars", bars);
            recording.addKeyframe(f);
            lastKeyframeTime = now;
            forceKeyframe = false;
        } catch (Exception ignored) {}
    }

    private void applyInitialTutorialLines() {
        if (tutorial == null) return;
        while (tutorialAction < tutorial.actions.length()) {
            try {
                JSONArray a = tutorial.actions.getJSONArray(tutorialAction);
                double at = a.getDouble(0);
                if (at > 0.0005) break;
                int type = a.getInt(1);
                if (type == TutorialStore.ACTION_START || type == TutorialStore.ACTION_MANUAL_END) break;
                if (type == TutorialStore.ACTION_CLEAR) {
                    world.clearLines();
                } else if (type == TutorialStore.ACTION_LINE) {
                    JSONArray pts = a.optJSONArray(2);
                    if (pts != null && pts.length() >= 4) {
                        int n = pts.length() / 2;
                        double[] xs = new double[n], ys = new double[n];
                        for (int i = 0; i < n; i++) {
                            xs[i] = pts.getDouble(i * 2);
                            ys[i] = pts.getDouble(i * 2 + 1);
                        }
                        world.addLinePath(xs, ys, n);
                    }
                }
                tutorialAction++;
            } catch (Exception e) {
                tutorialAction++;
            }
        }
    }

    private void applyTutorialActions() {
        if (tutorial == null) return;
        while (tutorialAction < tutorial.actions.length()) {
            try {
                org.json.JSONArray a = tutorial.actions.getJSONArray(tutorialAction);
                double at = a.getDouble(0);
                if (at > world.runTime + 0.0005) break;
                int type = a.getInt(1);
                if (type == TutorialStore.ACTION_START) {
                    world.startWater();
                } else if (type == TutorialStore.ACTION_CLEAR) {
                    world.clearLines();
                } else if (type == TutorialStore.ACTION_MANUAL_END) {
                    world.requestManualEnd();
                } else if (type == TutorialStore.ACTION_LINE) {
                    org.json.JSONArray pts = a.optJSONArray(2);
                    if (pts != null && pts.length() >= 4) {
                        int n = pts.length() / 2;
                        double[] xs = new double[n], ys = new double[n];
                        for (int i = 0; i < n; i++) {
                            xs[i] = pts.getDouble(i * 2);
                            ys[i] = pts.getDouble(i * 2 + 1);
                        }
                        world.addLinePath(xs, ys, n);
                    }
                }
                tutorialAction++;
            } catch (Exception e) {
                tutorialAction++;
            }
        }
        if (world.state == GameWorld.State.READY) world.startWater();
    }

    private void checkState(float dt) {
        if (world.state != lastState) {
            if (world.state == GameWorld.State.WON || world.state == GameWorld.State.LOST) {
                resultDelay = 0.55f;
                if (world.state == GameWorld.State.WON && !winSaved && !editorMode
                        && !tutorialMode) {
                    winSaved = true;
                    prefs.edit().putBoolean("done_" + (levelIndex + 1), true).apply();
                }
                if (world.state == GameWorld.State.WON && !tutorialMode
                        && recording != null && !tutorialSaved) {
                    recordKeyframe(true);
                    tutorialSaved = true;
                    if (editorMode) {
                        TutorialStore.saveCustom(getContext(), level.name, recording);
                    } else if (TutorialStore.RECORD_OFFICIAL) {
                        TutorialStore.saveOfficial(getContext(), levelIndex + 1, recording);
                    }
                    if (TutorialStore.AUTO_EXPORT) {
                        String path = TutorialStore.exportArchive(getContext());
                        if (path != null) {
                            android.widget.Toast.makeText(getContext(),
                                    "官方教程已录制：" + path, android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                }
                performHapticFeedback(world.state == GameWorld.State.WON
                        ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP);
            }
            lastState = world.state;
        }
        if (overlay == OV_NONE && (world.state == GameWorld.State.WON
                || world.state == GameWorld.State.LOST)) {
            if (resultDelay > 0) resultDelay -= dt;
            else { overlay = world.state == GameWorld.State.WON ? OV_WIN : OV_LOSE; layoutOverlay(); uiAnim = 0; }
        }
        if (overlay != OV_NONE) uiAnim = Math.min(1, uiAnim + dt * 5);
    }

    private void drawAll(Canvas c) {
        drawBackground(c);
        c.save();
        c.clipRect(playRect);
        drawField(c);
        drawCup(c);
        drawFaucet(c);
        drawMechanisms(c);
        drawAmbient(c);
        drawLines(c);
        drawDrops(c);
        drawDraft(c);
        drawTexts(c);
        c.restore();
        drawBorder(c);
        drawHud(c);
        if (overlay == OV_NONE) {
            if (world.state == GameWorld.State.READY) drawHint(c);
            else if (world.awaitingSettle()) drawSettle(c);
        }
        if (overlay != OV_NONE) drawOverlay(c);
    }

    private void drawBackground(Canvas c) {
        fill.setShader(new LinearGradient(0, 0, 0, getHeight(),
                new int[]{0xFF041019, 0xFF081E29, 0xFF06141E}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        fill.setShader(null);
    }

    private void drawField(Canvas c) {
        fill.setColor(0xE6081B27);
        c.drawRect(playRect, fill);
        fill.setShader(new LinearGradient(playRect.left, playRect.top, playRect.right, playRect.bottom,
                new int[]{0xFF0A202C, 0xFF0A1C28, 0xFF07151F}, null, Shader.TileMode.CLAMP));
        c.drawRect(playRect, fill);
        fill.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1);
        stroke.setColor(0x143FB3D8);
        for (int i = 1; i < 6; i++) {
            float x = playRect.left + playRect.width() * i / 6f;
            c.drawLine(x, playRect.top, x, playRect.bottom, stroke);
        }
        for (int i = 1; i < 10; i++) {
            float y = playRect.top + playRect.height() * i / 10f;
            c.drawLine(playRect.left, y, playRect.right, y, stroke);
        }
        float g = Y(0.944);
        fill.setShader(new LinearGradient(0, g, 0, playRect.bottom,
                new int[]{0xFF244A5A, 0xFF102A35, 0xFF081820}, null, Shader.TileMode.CLAMP));
        c.drawRect(playRect.left, g, playRect.right, playRect.bottom, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(dp(1.4f));
        stroke.setColor(0xAA65D8F4);
        c.drawLine(playRect.left, g, playRect.right, g, stroke);
    }

    private void drawCup(Canvas c) {
        Bar cup = world.cupBody;
        if (cup == null) return;
        float cx = X(cup.x), cy = Y(cup.y);
        c.save();
        c.translate(cx, cy);
        c.rotate((float) Math.toDegrees(cup.angle));
        float wall = S(Level.CUP_WALL);
        // glass body (the centreline of the U)
        path.reset();
        for (int i = 0; i < cup.pointCount; i++) {
            float lx = (float) (cup.localX[i] * ppm), ly = (float) (cup.localY[i] * ppm);
            if (i == 0) path.moveTo(lx, ly); else path.lineTo(lx, ly);
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(wall + dp(2));
        stroke.setColor(0xCCB7EBFA);
        c.drawPath(path, stroke);
        stroke.setStrokeWidth(Math.max(2, wall * 0.45f));
        stroke.setColor(0x5529BBE7);
        c.drawPath(path, stroke);

        // water volume, tied to the cup frame; count is independent of cup motion
        double f = world.fillFraction();
        if (f > 0.001) {
            float il = (float) (world.cupLocalInnerLeft * ppm);
            float ir = (float) (world.cupLocalInnerRight * ppm);
            float it = (float) (world.cupLocalTop * ppm);
            float ib = (float) (world.cupLocalBottom * ppm);
            float surf = (float) (ib - f * (ib - it));
            path.reset();
            path.moveTo(il, surf);
            for (int i = 1; i <= 14; i++) {
                float t = i / 14f;
                path.lineTo(il + (ir - il) * t,
                        surf + (float) Math.sin(t * 13 + vTime * 4) * dp(1.2f));
            }
            path.lineTo(ir, ib);
            path.lineTo(il, ib);
            path.close();
            c.save();
            c.clipRect(il, it, ir, ib);
            fill.setShader(new LinearGradient(0, surf, 0, ib,
                    new int[]{0xCC48D6FF, 0xDD168BC4, 0xEE0A5D91}, null, Shader.TileMode.CLAMP));
            c.drawPath(path, fill);
            fill.setShader(null);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.6f));
            stroke.setColor(0xBB9CEEFF);
            c.drawPath(path, stroke);
            c.restore();
        }
        // rim line
        if (cup.pointCount >= 2) {
            float ax = (float) (cup.localX[0] * ppm), ay = (float) (cup.localY[0] * ppm);
            float bx = (float) (cup.localX[cup.pointCount - 1] * ppm);
            float by = (float) (cup.localY[cup.pointCount - 1] * ppm);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(0xFFD8F8FF);
            c.drawLine(ax, ay, bx, by, stroke);
        }
        c.restore();
        drawText(c, "95%", X(cup.x) + S(0.080f), Y(cup.y), 8.5f,
                0xCCFFD36A, false);
        drawText(c, world.dropsInCup + " / " + GameWorld.CUP_FULL_DROPS,
                X(cup.x), Y(cup.y) + S(0.090f), 8, 0xAA8FC5D8, false,
                Paint.Align.CENTER);
    }

    private void drawFaucet(Canvas c) {
        float x = X(level.faucetX), top = Y(-0.03), bottom = Y(0.15), hw = S(0.020f);
        fill.setShader(new LinearGradient(x - hw, 0, x + hw, 0,
                new int[]{0xFF1A3848, 0xFFD7F4FF, 0xFF6C9FB2, 0xFF16313F},
                new float[]{0f, .25f, .62f, 1f}, Shader.TileMode.CLAMP));
        tmpRect.set(x - hw, top, x + hw, bottom);
        c.drawRoundRect(tmpRect, hw, hw, fill);
        fill.setShader(null);
        fill.setColor(0xFFBDDFEA);
        tmpRect.set(x - S(0.034f), Y(0.082f), x + S(0.034f), Y(0.108f));
        c.drawRoundRect(tmpRect, S(0.012f), S(0.012f), fill);
        fill.setColor(0xFFD7F4FF);
        tmpRect.set(x - S(0.030f), Y(0.144f), x + S(0.030f), Y(0.174f));
        c.drawRoundRect(tmpRect, S(0.011f), S(0.011f), fill);
        fill.setColor(0xFF08141B);
        tmpRect.set(x - S(0.020f), Y(0.166f), x + S(0.020f), Y(0.176f));
        c.drawRoundRect(tmpRect, S(0.005f), S(0.005f), fill);
        if (world.state == GameWorld.State.RUNNING && world.spawned < GameWorld.TOTAL_DROPS) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(S(0.005f));
            stroke.setColor(0x883FCBF4);
            c.drawLine(x, Y(0.176f), x + (float) Math.sin(vTime * 9) * 2, Y(0.27f), stroke);
        }
    }

    private void drawMechanisms(Canvas c) {
        for (Bar b : world.supportBars) {
            if (b.supportId >= 0 && b.supportId < level.supports.size()
                    && (level.supports.get(b.supportId).broken
                    || level.supports.get(b.supportId).hidden)) continue;
            path.reset();
            for (int i = 0; i < b.pointCount; i++) {
                float x = X(b.px(i)), y = Y(b.py(i));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            int kind = b.supportId >= 0 && b.supportId < level.supports.size()
                    ? level.supports.get(b.supportId).kind : Level.ARC_WALL;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(4));
            stroke.setColor(kind == Level.ARC_FIRE ? 0xFFFF7A3D
                    : kind == Level.ARC_WALL ? 0xFF9DB7C3
                    : kind == Level.ARC_BOUNCE ? 0xFF64F1CE
                    : kind == Level.ARC_BREAKABLE ? 0xFFB08A5E
                    : (kind == Level.ARC_ONE_WAY_IN || kind == Level.ARC_ONE_WAY_OUT)
                    ? 0xFFB48CFF : 0xFF6CF3CB);
            c.drawPath(path, stroke);
            Level.Support sd = level.supports.get(b.supportId);
            if (sd.kind == Level.ARC_ONE_WAY_IN || sd.kind == Level.ARC_ONE_WAY_OUT) {
                double dx = sd.x2 - sd.x1, dy = sd.y2 - sd.y1;
                double dl = Math.max(1e-6, Math.hypot(dx, dy));
                double cr = dx * (sd.ym - sd.y1) - dy * (sd.xm - sd.x1);
                double sg = cr >= 0 ? -1 : 1;
                double nx = -dy / dl * sg, ny = dx / dl * sg;
                if (sd.kind == Level.ARC_ONE_WAY_OUT) { nx = -nx; ny = -ny; }
                drawArrowAt(c, X(sd.xm), Y(sd.ym), nx, ny, 0xFFB48CFF);
            }
            if (sd.burning) {
                double ta = Math.atan2(sd.y2 - sd.y1, sd.x2 - sd.x1);
                drawWoodFire(c, sd.burnX, sd.burnY, sd.burnSpread, ta, sd.burnProgress);
            }
        }
        for (Level.SegObj s : level.segments) {
            if (s.broken || s.hidden) continue;
            if (s.kind == Level.SEG_WALL) {
                drawBarLine(c, s.x1, s.y1, s.x2, s.y2, 0xFF9DB7C3, 0.0065f);
            } else if (s.kind == Level.SEG_FIRE_LINE) {
                drawBarLine(c, s.x1, s.y1, s.x2, s.y2, 0xFFFF7A3D, 0.0055f);
            } else if (s.kind == Level.SEG_BOUNCE) {
                drawBarLine(c, s.x1, s.y1, s.x2, s.y2, 0xFF64F1CE, 0.0065f);
            } else if (s.kind == Level.SEG_ONE_WAY) {
                drawBarLine(c, s.x1, s.y1, s.x2, s.y2, 0xFFB48CFF, 0.0055f);
                drawOneWayArrow(c, s.x1, s.y1, s.x2, s.y2, 0xFFB48CFF);
            } else if (s.kind == Level.SEG_BREAKABLE) {
                drawBarLine(c, s.x1, s.y1, s.x2, s.y2, 0xFFB08A5E, 0.0065f);
            }
            if (s.burning) {
                double ta = Math.atan2(s.y2 - s.y1, s.x2 - s.x1);
                drawWoodFire(c, s.burnX, s.burnY, s.burnSpread, ta, s.burnProgress);
            }
        }
        for (Level.CircleObj o : level.circles) {
            if (o.hidden) continue;
            float x = X(o.cx), y = Y(o.cy), r = S(o.radius);
            if (o.kind == Level.CIRCLE_SOLID) {
                drawBall(c, x, y, r, 0xFF6C8794, 0xFFD7EDF6);
            } else if (o.kind == Level.CIRCLE_FIRE_RING) {
                drawFireRing(c, x, y, r);
            } else if (o.kind == Level.CIRCLE_BUBBLE) {
                if (!world.isBubbleUsed(o)) drawBubble(c, x, y, r, 0xFF57D8FF);
            } else if (o.kind == Level.CIRCLE_POISON_BALL) {
                if (!world.isBubbleUsed(o)) drawBubble(c, x, y, r, 0xFF7FE86B);
            } else if (o.kind == Level.CIRCLE_BALL) {
                drawBall(c, x, y, r, 0xFF4DA7D8, 0xFFD9F8FF);
            } else if (o.kind == Level.CIRCLE_PURIFIER) {
                drawPurifier(c, x, y, r);
            } else if (o.kind == Level.CIRCLE_ICE) {
                if (!o.melted) drawIce(c, x, y, r);
            }
        }
        if (world.fireBall != null) {
            float x = X(world.fireBall.x), y = Y(world.fireBall.y), r = S(world.fireBall.radius);
            drawBall(c, x, y, r, 0xFFFF6B35, 0xFFFFE0B0);
        }
        for (Level.PoisonFaucet p : level.poisonFaucets) {
            drawPoisonFaucet(c, X(p.x), Y(p.y), p.count);
        }
        for (Bar b : world.mudBars) {
            fill.setColor(0xFF7A5A3A);
            c.drawCircle(X(b.x), Y(b.y), Math.max(dp(3), S(b.radius)), fill);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(0xFFB08A5E);
            c.drawCircle(X(b.x), Y(b.y), Math.max(dp(3), S(b.radius)), stroke);
        }
        for (GameWorld.TriggerState ts : world.triggers) {
            if (ts.def.count > 0 && ts.remaining <= 0) continue;
            double cx, cy;
            if (ts.bar != null) { cx = ts.bar.x; cy = ts.bar.y; }
            else if (ts.target instanceof Level.CircleObj) {
                cx = ((Level.CircleObj) ts.target).cx; cy = ((Level.CircleObj) ts.target).cy;
            } else continue;
            float tx = X(cx + ts.def.offsetX), ty = Y(cy + ts.def.offsetY);
            float pr = dp(5) + (float) Math.sin(vTime * 5) * dp(1);
            int tc = ts.def.mode == 1 ? 0xCCB48CFF
                    : ts.def.mode == 2 ? 0xCCFF6B4A : 0xCCFFB347;
            fill.setColor(tc);
            c.drawCircle(tx, ty, pr, fill);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.5f));
            stroke.setColor(tc);
            c.drawCircle(tx, ty, pr + dp(3), stroke);
        }
    }

    private void drawPoisonFaucet(Canvas c, float x, float spout, int count) {
        float top = Y(-0.03), hw = S(0.020f);
        fill.setShader(new LinearGradient(x - hw, 0, x + hw, 0,
                new int[]{0xFF17331B, 0xFF9BFF7A, 0xFF4C9A42, 0xFF15301A},
                new float[]{0f, .25f, .62f, 1f}, Shader.TileMode.CLAMP));
        tmpRect.set(x - hw, top, x + hw, spout - S(0.010f));
        c.drawRoundRect(tmpRect, hw, hw, fill);
        fill.setShader(null);
        fill.setColor(0xFF8FE079);
        tmpRect.set(x - S(0.034f), spout - S(0.030f), x + S(0.034f), spout - S(0.014f));
        c.drawRoundRect(tmpRect, S(0.011f), S(0.011f), fill);
        fill.setColor(0xFF6CCB58);
        tmpRect.set(x - S(0.030f), spout - S(0.014f), x + S(0.030f), spout);
        c.drawRoundRect(tmpRect, S(0.008f), S(0.008f), fill);
        fill.setColor(0xFF0E1F11);
        tmpRect.set(x - S(0.018f), spout - S(0.003f), x + S(0.018f), spout + S(0.004f));
        c.drawRoundRect(tmpRect, S(0.004f), S(0.004f), fill);
        fill.setColor(0xDD8CFF70);
        c.drawCircle(x, spout + S(0.012f), S(0.005f), fill);
    }

    private void drawArrowAt(Canvas c, float x, float y, double nx, double ny, int color) {
        float ex = x + (float) nx * S(0.042f), ey = y + (float) ny * S(0.042f);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(color);
        c.drawLine(x, y, ex, ey, stroke);
        double ang = Math.atan2(ny, nx);
        c.drawLine(ex, ey, ex - (float) Math.cos(ang - .5) * dp(8),
                ey - (float) Math.sin(ang - .5) * dp(8), stroke);
        c.drawLine(ex, ey, ex - (float) Math.cos(ang + .5) * dp(8),
                ey - (float) Math.sin(ang + .5) * dp(8), stroke);
    }

    private void drawOneWayArrow(Canvas c, double x1, double y1, double x2, double y2,
                                 int color) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.max(1e-6, Math.hypot(dx, dy));
        double nx = -dy / len, ny = dx / len;
        float cx = X((x1 + x2) * .5), cy = Y((y1 + y2) * .5);
        float ex = cx + (float) nx * S(0.045f), ey = cy + (float) ny * S(0.045f);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(color);
        c.drawLine(cx, cy, ex, ey, stroke);
        double ang = Math.atan2(ny, nx);
        c.drawLine(ex, ey, ex - (float) Math.cos(ang - .5) * dp(8),
                ey - (float) Math.sin(ang - .5) * dp(8), stroke);
        c.drawLine(ex, ey, ex - (float) Math.cos(ang + .5) * dp(8),
                ey - (float) Math.sin(ang + .5) * dp(8), stroke);
    }

    private void drawPurifier(Canvas c, float x, float y, float r) {
        fill.setColor(0x333CFFC4);
        c.drawCircle(x, y, r, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(0xFF7FFFD8);
        c.drawCircle(x, y, r, stroke);
        c.drawCircle(x, y, r * .62f, stroke);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0xAAFFFFFF);
        c.drawLine(x - r * .75f, y, x + r * .75f, y, stroke);
        c.drawLine(x, y - r * .75f, x, y + r * .75f, stroke);
    }

    private void drawIce(Canvas c, float x, float y, float r) {
        drawBall(c, x, y, r, 0xFF7FDFFF, 0xFFE8FBFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0xAAFFFFFF);
        c.drawLine(x - r * .7f, y - r * .1f, x + r * .2f, y + r * .7f, stroke);
        c.drawLine(x - r * .2f, y - r * .7f, x + r * .7f, y + r * .1f, stroke);
    }

    private void drawWoodFire(Canvas c, double wx, double wy, double spread,
                              double angle, double progress) {
        float base = dp(5) + dp(10) * (float) Math.min(1, progress);
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        for (int s = -1; s <= 1; s++) {
            float x = X(wx + ca * spread * s), y = Y(wy + sa * spread * s);
            for (int i = 0; i < 4; i++) {
                double ph = vTime * 4 + i * 1.7 + s;
                float ox = (float) Math.sin(ph) * base * .55f;
                float oy = (float) Math.cos(ph * 1.3) * base * .25f;
                float rr = base * (.50f + .18f * (float) Math.sin(ph * 1.7));
                fill.setColor(i % 2 == 0 ? 0xCCFF6A1E : 0xAAFFC23D);
                c.drawCircle(x + ox, y + oy, rr, fill);
            }
            fill.setColor(0x55FF3D00);
            c.drawCircle(x, y, base * 1.4f, fill);
        }
    }

    private void drawBarLine(Canvas c, double x1, double y1, double x2, double y2, int color, float rad) {
        float ax = X(x1), ay = Y(y1), bx = X(x2), by = Y(y2);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(S(rad) * 2 + dp(8));
        stroke.setColor((color & 0x00FFFFFF) | 0x22000000);
        c.drawLine(ax, ay, bx, by, stroke);
        stroke.setStrokeWidth(S(rad) * 2);
        stroke.setColor(0xCC050D12);
        c.drawLine(ax, ay, bx, by, stroke);
        stroke.setStrokeWidth(S(rad) * 1.4f);
        stroke.setColor(color);
        c.drawLine(ax, ay, bx, by, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawBall(Canvas c, float x, float y, float r, int outer, int inner) {
        fill.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.35f, r * 1.2f,
                new int[]{inner, outer, 0xFF3A1208}, null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, fill);
        fill.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.3f));
        stroke.setColor(0xCCFFFFFF);
        c.drawCircle(x, y, r, stroke);
    }

    private void drawFireRing(Canvas c, float x, float y, float r) {
        if (fireRingSprite != null && !fireRingSprite.isRecycled()) {
            c.save();
            c.rotate(vTime * 55, x, y);
            float size = r * 3.05f;
            tmpRect.set(x - size * .5f, y - size * .5f, x + size * .5f, y + size * .5f);
            fill.setAlpha(255);
            c.drawBitmap(fireRingSprite, null, tmpRect, fill);
            c.restore();
            return;
        }
        c.save();
        c.rotate(vTime * 55, x, y);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(4));
            stroke.setColor(0xFFFF7A3D);
            c.drawLine(x + (float) Math.cos(a) * r * .8f, y + (float) Math.sin(a) * r * .8f,
                    x + (float) Math.cos(a) * r * 1.25f, y + (float) Math.sin(a) * r * 1.25f, stroke);
        }
        c.restore();
        drawBall(c, x, y, r, 0xFFFF6B35, 0xFFFFE0B0);
    }

    private void drawBubble(Canvas c, float x, float y, float r, int color) {
        fill.setColor((color & 0x00FFFFFF) | 0x33000000);
        c.drawCircle(x, y, r, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(color);
        c.drawCircle(x, y, r, stroke);
    }

    private void drawAmbient(Canvas c) {
        for (GameWorld.Steam s : world.steams) {
            float a = (float) (s.life / s.maxLife);
            fill.setColor(withAlpha(0xFFBFF5FF, a * .55f));
            c.drawCircle(X(s.x), Y(s.y), S(s.size) * (2 - a), fill);
        }
    }

    private void drawLines(Canvas c) {
        for (GameWorld.GameLine gl : world.lines) {
            Bar b = gl.bar;
            int main, light;
            if (gl.pinA && gl.pinB) { main = 0xFF54E7C5; light = 0xFFC9FFF4; }
            else if (gl.pinA || gl.pinB) { main = 0xFFFFC15C; light = 0xFFFFF0C8; }
            else { main = 0xFFFF8B7A; light = 0xFFFFD4CC; }
            float r = Math.max(dp(2.2f), S(GameWorld.LINE_RADIUS));
            path.reset();
            for (int i = 0; i < b.pointCount; i++) {
                if (i == 0) path.moveTo(X(b.px(i)), Y(b.py(i)));
                else path.lineTo(X(b.px(i)), Y(b.py(i)));
            }
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(r * 2 + dp(9));
            stroke.setColor(0x22FFFFFF);
            c.drawPath(path, stroke);
            stroke.setStrokeWidth(r * 2 + dp(3));
            stroke.setColor(0xCC040C11);
            c.drawPath(path, stroke);
            stroke.setStrokeWidth(r * 2);
            stroke.setShader(new LinearGradient(X(b.ax()), Y(b.ay()), X(b.bx()), Y(b.by()),
                    light, main, Shader.TileMode.CLAMP));
            c.drawPath(path, stroke);
            stroke.setShader(null);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            stroke.setStrokeJoin(Paint.Join.MITER);
            if (gl.pinA) drawPin(c, X(gl.pinAx), Y(gl.pinAy));
            if (gl.pinB) drawPin(c, X(gl.pinBx), Y(gl.pinBy));
        }
    }

    private void drawPin(Canvas c, float x, float y) {
        fill.setColor(0x332FFFF0);
        c.drawCircle(x, y, dp(7), fill);
        fill.setColor(0xFF07181D);
        c.drawCircle(x, y, dp(4.5f), fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.4f));
        stroke.setColor(0xFF6CF3CB);
        c.drawCircle(x, y, dp(4.5f), stroke);
    }

    private void drawDrops(Canvas c) {
        float r = S(GameWorld.DROP_RADIUS);
        if (r < 1) return;
        if (waterPaint == null) {
            waterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            poisonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dropRim = new Paint(Paint.ANTI_ALIAS_FLAG);
            dropRim.setStyle(Paint.Style.STROKE);
            dropRim.setStrokeWidth(Math.max(1, dp(0.7f)));
            dropHi = new Paint(Paint.ANTI_ALIAS_FLAG);
            dropHi.setColor(0xCCFFFFFF);
            waterPaint.setShader(new RadialGradient(0, 0, r,
                    new int[]{0xFFE1FCFF, 0xFF71DDFF, 0xFF1A9BD5, 0xFF0B5A8E},
                    new float[]{0f, .22f, .7f, 1f}, Shader.TileMode.CLAMP));
            poisonPaint.setShader(new RadialGradient(0, 0, r,
                    new int[]{0xFFE6FFC9, 0xFF9AF25A, 0xFF3E9A1F, 0xFF1C5B12},
                    new float[]{0f, .25f, .7f, 1f}, Shader.TileMode.CLAMP));
        }
        for (Drop d : world.physics.drops) {
            if (d.removed) continue;
            float fade = d.collected ? clamp((float) (d.fade / GameWorld.ENTER_FADE_SECONDS), 0, 1) : 1;
            if (fade <= 0.01f) continue;
            int a = (int) (255 * fade);
            Paint water = d.poison ? poisonPaint : waterPaint;
            dropRim.setColor(d.poison ? 0xAA8CFF76 : 0xAA8BE9FF);
            water.setAlpha(a);
            dropRim.setAlpha((int) (a * .66f));
            dropHi.setAlpha((int) (a * .8f));
            float x = X(d.x), y = Y(d.y);
            float grow = 1 + (1 - fade) * .35f;
            c.drawCircle(x, y, r * grow, water);
            c.drawCircle(x, y, r * grow - dropRim.getStrokeWidth() * .5f, dropRim);
            c.drawCircle(x - r * .3f, y - r * .34f, r * .2f, dropHi);
        }
    }

    private void drawDraft(Canvas c) {
        if (!drawing) return;
        path.reset();
        double len = 0;
        float px = 0, py = 0;
        for (int i = 0; i < draftXs.size(); i++) {
            float wx = draftXs.get(i), wy = draftYs.get(i);
            if (i == 0) path.moveTo(X(wx), Y(wy));
            else { path.lineTo(X(wx), Y(wy)); len += Math.hypot(wx - px, wy - py); }
            px = wx; py = wy;
        }
        if (Math.hypot(draftEx - px, draftEy - py) > .004) {
            path.lineTo(X(draftEx), Y(draftEy));
            len += Math.hypot(draftEx - px, draftEy - py);
        }
        boolean valid = len >= GameWorld.LINE_MIN_LEN && len <= GameWorld.LINE_MAX_LEN;
        int col = valid ? 0xFF8AFFF2 : 0xFFFF8D8D;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(S(0.011f));
        stroke.setColor(0x551FE2D1);
        c.drawPath(path, stroke);
        stroke.setStrokeWidth(S(0.0055f));
        stroke.setColor(col);
        stroke.setPathEffect(new DashPathEffect(new float[]{dp(9), dp(7)}, -vTime * 30));
        c.drawPath(path, stroke);
        stroke.setPathEffect(null);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeJoin(Paint.Join.MITER);
    }

    private void drawTexts(Canvas c) {
        for (Level.TextObj t : level.texts) {
            RichText.draw(c, t.text, X(t.x), Y(t.y), dp(13), 0xFFE8F8FF, dp(4));
        }
    }

    private void drawBorder(Canvas c) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.2f));
        stroke.setColor(0x5537B5DA);
        c.drawRoundRect(playRect, dp(12), dp(12), stroke);
    }

    private void drawHud(Canvas c) {
        fill.setShader(new LinearGradient(0, 0, 0, safeTop + dp(72),
                new int[]{0xEE041019, 0xBB041019, 0x00041019}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, getWidth(), safeTop + dp(72), fill);
        fill.setShader(null);
        fill.setColor(0xDD0A2431);
        c.drawRoundRect(btnEdit, dp(13), dp(13), fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x5539C8EE);
        c.drawRoundRect(btnEdit, dp(13), dp(13), stroke);
        drawText(c, tutorialMode ? "‹ 退出" : editorMode ? "‹ 编辑" : "‹ 关卡",
                btnEdit.centerX(), btnEdit.centerY() + dp(4.5f), 12, C_CYAN, true,
                Paint.Align.CENTER);
        float left = btnEdit.right + dp(7), right = btnInfo.left - dp(7);
        fill.setColor(0xCC0A2431);
        tmpRect.set(left, btnEdit.top, right, btnEdit.bottom);
        c.drawRoundRect(tmpRect, dp(13), dp(13), fill);
        stroke.setColor(0x4439C8EE);
        c.drawRoundRect(tmpRect, dp(13), dp(13), stroke);
        String label = "杯量 " + world.dropsInCup + " / " + GameWorld.CUP_FULL_DROPS
                + " · " + world.progressPercent() + "% (95%通关)";
        drawText(c, label, left + dp(11), btnEdit.top + dp(16), 10.5f, 0xFFDCF6FF, false);
        float bl = left + dp(11), br = right - dp(11), by = btnEdit.top + dp(26), bh = dp(6);
        fill.setColor(0x552C6F86);
        tmpRect.set(bl, by, br, by + bh);
        c.drawRoundRect(tmpRect, bh / 2, bh / 2, fill);
        float f = (float) world.fillFraction();
        if (f > 0) {
            fill.setShader(new LinearGradient(bl, 0, bl + (br - bl) * f, 0, C_CYAN, C_MINT,
                    Shader.TileMode.CLAMP));
            tmpRect.set(bl, by, bl + (br - bl) * f, by + bh);
            c.drawRoundRect(tmpRect, bh / 2, bh / 2, fill);
            fill.setShader(null);
        }
        fill.setColor(0xDD0A2431);
        c.drawRoundRect(btnInfo, dp(13), dp(13), fill);
        drawText(c, "i", btnInfo.centerX(), btnInfo.centerY() + dp(5), 16, C_CYAN, true,
                Paint.Align.CENTER);
        float barTop = getHeight() - safeBottom - dp(92);
        fill.setShader(new LinearGradient(0, barTop, 0, getHeight(),
                new int[]{0x00041019, 0xDD041019, 0xFF041019}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, barTop, getWidth(), getHeight(), fill);
        fill.setShader(null);
        boolean ready = world.state == GameWorld.State.READY;
        boolean running = world.state == GameWorld.State.RUNNING;
        String startLabel;
        if (ready) startLabel = "放水 · 225 滴";
        else if (running) startLabel = world.manualEndRequested
                ? String.format(Locale.US, "结算 %.1fs", Math.max(0, world.manualEndTimer))
                : "结束 · 5s";
        else startLabel = "实验结束";
        drawButton(c, btnStart, startLabel, ready || running);
        drawGhost(c, btnClear, "清线 " + world.lineCount() + "/3");
        drawGhost(c, btnRestart, "重来");
    }

    private void drawButton(Canvas c, RectF r, String s, boolean enabled) {
        if (enabled) {
            fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{0xFF44D4FF, 0xFF63F1CE}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, dp(17), dp(17), fill);
            fill.setShader(null);
            drawText(c, s, r.centerX(), r.centerY() + dp(5), 14, 0xFF06202A, true, Paint.Align.CENTER);
        } else {
            fill.setColor(0xFF0B212C);
            c.drawRoundRect(r, dp(17), dp(17), fill);
            drawText(c, s, r.centerX(), r.centerY() + dp(5), 13, 0x887FA8B8, false, Paint.Align.CENTER);
        }
    }

    private void drawGhost(Canvas c, RectF r, String s) {
        fill.setColor(0xDD0E2836);
        c.drawRoundRect(r, dp(17), dp(17), fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x553BC5E8);
        c.drawRoundRect(r, dp(17), dp(17), stroke);
        drawText(c, s, r.centerX(), r.centerY() + dp(4.5f), 11.5f, 0xFFBFE7F2, false,
                Paint.Align.CENTER);
    }

    private void drawHint(Canvas c) {
        String s = world.lines.isEmpty()
                ? "拖动画自由曲线引导水流 · 点「放水」开始物理"
                : "点「放水」开始 · 绘制阶段物体不会掉落";
        drawPill(c, s, playRect.bottom - dp(20), 0xAA061A23, 0xCCB9E7F4);
    }

    private void drawSettle(Canvas c) {
        drawPill(c, world.endingStatus(), playRect.bottom - dp(22), 0xEE0A2731, 0xFFD6FFF8);
    }

    private void drawPill(Canvas c, String s, float y, int bg, int fg) {
        txt.setTypeface(tfRegular);
        txt.setTextSize(dp(10));
        txt.setTextAlign(Paint.Align.CENTER);
        float w = Math.min(playRect.width() - dp(24), txt.measureText(s) + dp(24));
        tmpRect.set(playRect.centerX() - w / 2, y - dp(14), playRect.centerX() + w / 2, y + dp(14));
        fill.setColor(bg);
        c.drawRoundRect(tmpRect, dp(14), dp(14), fill);
        txt.setColor(fg);
        c.drawText(s, playRect.centerX(), y + dp(3.4f), txt);
    }

    private void drawOverlay(Canvas c) {
        layoutOverlay();
        fill.setColor(withAlpha(0xCC02080D, uiAnim));
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        fill.setShader(new LinearGradient(0, overlayCard.top, 0, overlayCard.bottom,
                new int[]{0xFF102934, 0xFF0A1B25}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(overlayCard, dp(22), dp(22), fill);
        fill.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.2f));
        stroke.setColor(0x664EDCFF);
        c.drawRoundRect(overlayCard, dp(22), dp(22), stroke);
        float cx = overlayCard.centerX();
        if (overlay == OV_INFO) {
            drawText(c, "规则与机关", cx, overlayCard.top + dp(38), 20, C_TEXT, true, Paint.Align.CENTER);
            String[][] rows = {
                    {"通关", "装满 95%（190 / 200 颗）"},
                    {"入杯", "水珠碰到杯内水面才消失并计数"},
                    {"出水", "225 颗 · 每颗 0.5% 杯量"},
                    {"水珠", "物理 2/3 · 12.5 g"},
                    {"杯子", "体积 7/17 · 可被推动，存水不变"},
                    {"火线/火环", "蒸发接触的水珠"},
                    {"火球", "掉落，入杯立即失败"},
                    {"泡泡水球", "触碰释放面积×0.1% 水珠"},
                    {"毒水球", "触碰释放面积×0.2% 毒水"},
                    {"毒水", "入杯扣水；碰到水变泥珠"},
                    {"画线", "试玩画线为自由曲线，不自动吸附弧挡板"},
                    {"结算", "静止5s / 离屏5s / 手动5s / 完成15s"},
                    {"线材计分", "第一根线免费，之后每多10%长度 +0.1s"},
                    {"物理", "准备阶段暂停，放水后开始"},
            };
            float y = overlayCard.top + dp(78);
            for (String[] row : rows) {
                drawText(c, row[0], overlayCard.left + dp(24), y, 10, 0xFF8FBECE, false);
                drawText(c, row[1], overlayCard.right - dp(24), y, 10, 0xFFD8F5FF, false,
                        Paint.Align.RIGHT);
                y += dp(27);
            }
            drawSec(c, overlaySecondary, "知道了");
            return;
        }
        boolean win = overlay == OV_WIN;
        if (tutorialMode) {
            drawText(c, win ? "教程演示完成" : "教程演示结束", cx,
                    overlayCard.top + dp(44), 22, win ? C_MINT : C_GOLD, true,
                    Paint.Align.CENTER);
            drawText(c, "官方通关教程 · 模拟演示", cx, overlayCard.top + dp(76), 11,
                    0xFF9AC4D2, false, Paint.Align.CENTER);
            drawText(c, world.progressPercent() + "%", cx, overlayCard.top + dp(128), 36,
                    C_CYAN, true, Paint.Align.CENTER);
            drawText(c, "杯量 " + world.dropsInCup + " / " + GameWorld.CUP_FULL_DROPS,
                    cx, overlayCard.top + dp(154), 11, 0xFFBFE7F2, false, Paint.Align.CENTER);
            drawPrimary(c, overlayPrimary, "重播教程");
            drawSec(c, overlaySecondary, editorMode ? "返回编辑" : "返回选关");
            return;
        }
        drawText(c, win ? "杯子已装满" : "挑战失败", cx, overlayCard.top + dp(40), 22,
                win ? C_MINT : C_CORAL, true, Paint.Align.CENTER);
        drawText(c, world.progressPercent() + "%", cx, overlayCard.top + dp(105), 40,
                win ? C_CYAN : 0xFFFFA28D, true, Paint.Align.CENTER);
        drawText(c, "杯量 " + world.dropsInCup + " / " + GameWorld.CUP_FULL_DROPS,
                cx, overlayCard.top + dp(128), 11, 0xFFBFE7F2, false, Paint.Align.CENTER);
        if (!win) {
            String reason = world.failReason.isEmpty()
                    ? "水没有装满杯子，再调整一下路线" : world.failReason;
            drawText(c, reason, cx, overlayCard.top + dp(170), 10.5f, 0xFFFFC6B8, false,
                    Paint.Align.CENTER);
            drawText(c, "95% 才通关；毒水扣水，火会蒸发水珠", cx, overlayCard.top + dp(194), 9.5f,
                    0xAA9AC4D2, false, Paint.Align.CENTER);
        } else {
            double penalty = world.lineTimePenalty();
            int materialPct = world.lineMaterialPercent();
            String time = materialPct > 0
                    ? String.format(Locale.US, "%.1fs（%.1fs+%d%%线材）",
                    world.finalTime(), world.elapsedAtWin, materialPct)
                    : String.format(Locale.US, "用时 %.1f s", world.finalTime());
            drawText(c, time + " · 用线 " + world.linesAtWin + " 根",
                    cx, overlayCard.top + dp(164), 10.0f, 0xFFBFE7F2, false, Paint.Align.CENTER);
            if (materialPct > 0) {
                drawText(c, String.format(Locale.US, "线材总量 %.0f cm / 基准 %.0f cm",
                                world.totalLineLength * 100, world.firstLineLength * 100),
                        cx, overlayCard.top + dp(182), 8.8f, 0xAA9AC4D2, false,
                        Paint.Align.CENTER);
            }
            if (!world.endReason.isEmpty()) {
                drawText(c, world.endReason, cx, overlayCard.top + dp(202), 9.0f,
                        0xAA9AC4D2, false, Paint.Align.CENTER);
            }
        }
        if (editorMode) {
            drawPrimary(c, overlayPrimary, "再玩一次");
            drawSec(c, overlaySecondary, "返回编辑");
        } else {
            drawPrimary(c, overlayPrimary, win
                    ? (levelIndex + 1 < OfficialLevels.COUNT ? "下一关" : "返回选关")
                    : "再试一次");
            drawSec(c, overlaySecondary, "选关");
        }
    }

    private void drawPrimary(Canvas c, RectF r, String s) {
        fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{0xFF45D6FF, 0xFF64F1CE}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, dp(16), dp(16), fill);
        fill.setShader(null);
        drawText(c, s, r.centerX(), r.centerY() + dp(5), 13, 0xFF06202A, true, Paint.Align.CENTER);
    }

    private void drawSec(Canvas c, RectF r, String s) {
        fill.setColor(0xEE102B38);
        c.drawRoundRect(r, dp(16), dp(16), fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x6647C8E8);
        c.drawRoundRect(r, dp(16), dp(16), stroke);
        drawText(c, s, r.centerX(), r.centerY() + dp(4.5f), 11.5f, 0xFFC3EAF5, false,
                Paint.Align.CENTER);
    }

    private void drawTag(Canvas c, float x, float y, String s, int color, int bg) {
        txt.setTypeface(tfMedium);
        txt.setTextSize(dp(8));
        txt.setTextAlign(Paint.Align.LEFT);
        float w = txt.measureText(s) + dp(9);
        tmpRect.set(x - w / 2, y - dp(7), x + w / 2, y + dp(7));
        fill.setColor(bg);
        c.drawRoundRect(tmpRect, dp(7), dp(7), fill);
        txt.setColor(color);
        c.drawText(s, x - w / 2 + dp(4.5f), y + dp(2.8f), txt);
    }

    private void drawText(Canvas c, String s, float x, float y, float sp, int color, boolean bold,
                          Paint.Align align) {
        txt.setTypeface(bold ? tfMedium : tfRegular);
        txt.setTextSize(dp(sp));
        txt.setColor(color);
        txt.setTextAlign(align);
        c.drawText(s, x, y, txt);
    }

    private void drawText(Canvas c, String s, float x, float y, float sp, int color, boolean bold) {
        drawText(c, s, x, y, sp, color, bold, Paint.Align.LEFT);
    }

    private static int withAlpha(int color, float a) {
        return (color & 0x00FFFFFF) | (((int) (Color.alpha(color) * a)) << 24);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (overlay != OV_NONE) {
                layoutOverlay();
                if (overlay == OV_INFO) {
                    if (overlaySecondary.contains(x, y) || !overlayCard.contains(x, y)) overlay = OV_NONE;
                } else if (overlayPrimary.contains(x, y)) {
                    if (tutorialMode) restart();
                    else if (editorMode) restart();
                    else if (world.state == GameWorld.State.WON) {
                        if (levelIndex + 1 < OfficialLevels.COUNT) nextLevel();
                        else openLevelSelect();
                    } else {
                        restart();
                    }
                } else if (overlaySecondary.contains(x, y)) {
                    if (tutorialMode) exitTutorial();
                    else if (editorMode) openEditor();
                    else openLevelSelect();
                }
                invalidate();
                return true;
            }
            if (btnEdit.contains(x, y)) {
                if (tutorialMode) exitTutorial();
                else if (editorMode) openEditor(); else openLevelSelect();
                return true;
            }
            if (tutorialMode) return true;
            if (btnInfo.contains(x, y)) { overlay = OV_INFO; uiAnim = 0; layoutOverlay(); invalidate(); return true; }
            if (btnStart.contains(x, y)) {
                if (world.state == GameWorld.State.READY) {
                    world.startWater();
                    if (recording != null) { recording.addStart(world.runTime); forceKeyframe = true; }
                } else if (world.state == GameWorld.State.RUNNING) {
                    world.requestManualEnd();
                    if (recording != null) { recording.addManualEnd(world.runTime); forceKeyframe = true; }
                }
                invalidate();
                return true;
            }
            if (btnClear.contains(x, y)) {
                world.clearLines();
                if (recording != null) { recording.addClear(world.runTime); forceKeyframe = true; }
                invalidate();
                return true;
            }
            if (btnRestart.contains(x, y)) { restart(); return true; }
            if (!playRect.contains(x, y)) return true;
            if (world.state == GameWorld.State.WON || world.state == GameWorld.State.LOST) return true;
            drawing = true;
            downX = x; downY = y;
            draftXs.clear(); draftYs.clear();
            float wx = (x - playRect.left) / ppm, wy = (y - playRect.top) / ppm;
            draftSx = draftEx = wx; draftSy = draftEy = wy;
            draftXs.add(draftSx); draftYs.add(draftSy);
            invalidate();
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && drawing) {
            float wx = clamp((x - playRect.left) / ppm, 0, .60f);
            float wy = clamp((y - playRect.top) / ppm, 0, 1f);
            draftEx = wx; draftEy = wy;
            if (!draftXs.isEmpty()) {
                float lx = draftXs.get(draftXs.size() - 1), ly = draftYs.get(draftYs.size() - 1);
                if (Math.hypot(wx - lx, wy - ly) > .008) {
                    draftXs.add(wx); draftYs.add(wy);
                    if (draftXs.size() > 26) { draftXs.remove(1); draftYs.remove(1); }
                }
            }
            invalidate();
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP && drawing) {
            drawing = false;
            float moved = (float) Math.hypot(x - downX, y - downY);
            if (moved > dp(9)) {
                float wx = clamp((x - playRect.left) / ppm, 0, .60f);
                float wy = clamp((y - playRect.top) / ppm, 0, 1f);
                if (!draftXs.isEmpty()) {
                    float lx = draftXs.get(draftXs.size() - 1), ly = draftYs.get(draftYs.size() - 1);
                    if (Math.hypot(wx - lx, wy - ly) > .004) { draftXs.add(wx); draftYs.add(wy); }
                }
                double[] xs = new double[draftXs.size()], ys = new double[draftYs.size()];
                for (int i = 0; i < xs.length; i++) { xs[i] = draftXs.get(i); ys[i] = draftYs.get(i); }
                GameWorld.GameLine added = world.addLinePath(xs, ys, xs.length);
                if (added != null && recording != null) {
                    recording.addLine(world.runTime, xs, ys);
                    forceKeyframe = true;
                }
            }
            invalidate();
            return true;
        }
        return true;
    }

    private void restart() {
        overlay = OV_NONE;
        tutorialAction = 0;
        tutorialRealElapsed = 0;
        tutorialInitialLines = false;
        if (tutorialMode && tutorial != null) {
            world = new GameWorld(level, tutorial.seed);
            lastState = GameWorld.State.READY;
            resultDelay = 0;
            world.startWater();
            invalidate();
            return;
        }
        if (levelJson != null) {
            try { level = LevelStore.fromJson(levelJson); } catch (Exception ignored) {}
        }
        long seed = new java.util.Random().nextLong();
        world = new GameWorld(level, seed);
        if (levelJson != null) recording = new TutorialStore.Tutorial(levelJson, seed);
        lastState = GameWorld.State.READY;
        resultDelay = 0;
        winSaved = false;
        tutorialSaved = false;
        invalidate();
    }

    private void exitTutorial() {
        if (getContext() instanceof Activity) ((Activity) getContext()).finish();
    }

    private void openEditor() {
        if (getContext() instanceof Activity) ((Activity) getContext()).finish();
    }

    private void nextLevel() {
        Intent i = new Intent(getContext(), PlayActivity.class);
        i.putExtra("level_index", levelIndex + 1);
        getContext().startActivity(i);
        if (getContext() instanceof Activity) ((Activity) getContext()).finish();
    }

    private void openLevelSelect() {
        Intent i = new Intent(getContext(), OfficialMainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        getContext().startActivity(i);
        if (getContext() instanceof Activity) ((Activity) getContext()).finish();
    }
}
