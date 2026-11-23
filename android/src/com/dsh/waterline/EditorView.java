package com.dsh.waterline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.dsh.waterline.game.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

/** Touch-first level editor. No physics runs here: every object stays frozen. */
public final class EditorView extends View {
    private static final int C_BG = Color.rgb(6, 16, 25);
    private static final int C_CYAN = Color.rgb(86, 215, 255);
    private static final int C_MINT = Color.rgb(100, 240, 200);
    private static final int C_GOLD = Color.rgb(255, 200, 92);
    private static final int C_CORAL = Color.rgb(255, 122, 122);
    private static final int C_TEXT = Color.rgb(232, 248, 255);
    private static final int C_DIM = Color.rgb(142, 176, 194);

    private static final int TOOL_CUP = 0;
    private static final int TOOL_FAUCET = 1;
    private static final int TOOL_MOVE = 2;
    private static final int TOOL_SEG = 3;
    private static final int TOOL_CIRCLE = 4;
    private static final int TOOL_POISON = 5;
    private static final int TOOL_ERASE = 6;
    private static final int TOOL_TEXT = 7;
    private static final int TOOL_MOTION = 8;
    private static final int TOOL_ARC = 9;
    private static final int REQ_EXPORT = 3001;
    private static final int REQ_IMPORT = 3002;

    private final Level level;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Path path = new Path();
    private final RectF tmpRect = new RectF();
    private final RectF playRect = new RectF();
    private final RectF btnPlay = new RectF();
    private final RectF btnTutorial = new RectF();
    private final RectF btnCover13 = new RectF();
    private final RectF[] toolRects = new RectF[7];
    private final RectF btnSave = new RectF();
    private final RectF btnExport = new RectF();
    private final RectF btnImport = new RectF();
    private final RectF btnUndo = new RectF();
    private final RectF btnNew = new RectF();
    private final ArrayDeque<String> undo = new ArrayDeque<>();

    private float density;
    private int safeTop, safeBottom;
    private float ppm = 1f;
    private int layoutW = -1, layoutH = -1;
    private int tool = TOOL_SEG;
    private int segKind = Level.SEG_WALL;
    private int arcKind = Level.ARC_WALL;
    private int circleKind = Level.CIRCLE_FIRE_RING;
    private int poisonCount = 24;
    private String pendingText = "";
    private String lastText = "";
    private boolean pathEditing;
    private Object pathObject;
    private Level.Motion pathBackup;
    private int pathDrag = -1;
    private float pathDownX, pathDownY;
    private final RectF btnPathFinish = new RectF();
    private final RectF btnPathUndo = new RectF();
    private final RectF btnPathCancel = new RectF();
    private boolean orbitEditing;
    private Object orbitObject;
    private Level.Motion orbitBackup;
    private double orbitCx, orbitCy, orbitRadius = 0.08, orbitSpeed = 0.12;
    private int orbitDrag = -1;
    private Level.Pt pending;
    private boolean draggingCup, draggingFaucet, draggingSegment, draggingCircle, draggingArc;
    private boolean endpointMoveMode;
    private boolean triggerPlacement;
    private double triggerAngle = 90, triggerSpeed = 90;
    private int triggerCount = 0;
    private int triggerMode = 0;
    private double triggerDistance = 0.05;
    private double triggerMoveSpeed = 0.10;
    private boolean triggerEndpointRelative = false;
    private double triggerEndX = 0.5, triggerEndY = 0.5;
    private Object endpointObject;
    private int endpointIndex = -1;
    private double dragStartX, dragStartY, dragEndX, dragEndY;
    private double snapMarkX, snapMarkY;
    private boolean snapHot;
    private Object movingObject;
    private double moveLastX, moveLastY;
    private final ArrayList<Float> dragPathX = new ArrayList<>();
    private final ArrayList<Float> dragPathY = new ArrayList<>();
    private boolean pressed;
    private float vTime;
    private Bitmap fireRingSprite;

    public EditorView(Context context) { this(context, null); }

    public EditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        lastText = getContext().getSharedPreferences("editor_ui", Context.MODE_PRIVATE)
                .getString("last_text", "");
        try (java.io.InputStream in = context.getAssets().open("fire_ring.png")) {
            fireRingSprite = BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {}
        level = LevelStore.load(context);
        for (int i = 0; i < toolRects.length; i++) toolRects[i] = new RectF();
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(C_BG);
        setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                safeTop = insets.getDisplayCutout().getSafeInsetTop();
            } else {
                safeTop = insets.getSystemWindowInsetTop();
            }
            safeBottom = insets.getSystemWindowInsetBottom();
            layoutW = -1;
            updateLayout();
            return insets;
        });
    }

    public void onResumeEditor() {
        vTime = 0;
        invalidate();
    }

    public void onPauseEditor() {
        draggingCup = draggingFaucet = draggingSegment = draggingCircle = draggingArc = false;
        movingObject = null;
        endpointObject = null;
        endpointIndex = -1;
        orbitEditing = false;
        orbitObject = null;
        save();
    }

    public boolean handleBack() {
        if (triggerPlacement) { triggerPlacement = false; invalidate(); return true; }
        if (orbitEditing) { cancelOrbitEditing(); return true; }
        if (pathEditing) { cancelPathEditing(); return true; }
        if (pending != null) {
            pending = null;
            invalidate();
            return true;
        }
        return false;
    }

    private float dp(float v) { return v * density; }
    private float X(double wx) { return (float) (playRect.left + wx * ppm); }
    private float Y(double wy) { return (float) (playRect.top + wy * ppm); }
    private float S(double m) { return (float) (m * ppm); }
    private float clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }
    private double clamp(double v, double a, double b) { return v < a ? a : (v > b ? b : v); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutW = -1;
        updateLayout();
    }

    private void updateLayout() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (layoutW == w && layoutH == h) return;
        layoutW = w; layoutH = h;
        float top = safeTop + dp(68);
        float bottom = h - safeBottom - dp(150);
        float side = dp(8);
        float availH = Math.max(dp(240), bottom - top);
        float s = Math.min((w - side * 2) / 0.60f, availH);
        ppm = s;
        float pw = 0.60f * s, ph = s;
        playRect.set((w - pw) * 0.5f, top + (availH - ph) * 0.5f,
                (w + pw) * 0.5f, top + (availH - ph) * 0.5f + ph);

        btnPlay.set(w - dp(12) - dp(82), safeTop + dp(10),
                w - dp(12), safeTop + dp(46));
        btnTutorial.set(btnPlay.left - dp(6) - dp(58), safeTop + dp(10),
                btnPlay.left - dp(6), safeTop + dp(46));
        btnCover13.set(btnTutorial.left - dp(6) - dp(64), safeTop + dp(10),
                btnTutorial.left - dp(6), safeTop + dp(46));
        float rowTop = h - safeBottom - dp(142);
        float gap = dp(6);
        float total = w - dp(24);
        float bw;
        bw = (total - gap * 6) / 7f;
        for (int i = 0; i < 7; i++) {
            float x = dp(12) + i * (bw + gap);
            toolRects[i].set(x, rowTop, x + bw, rowTop + dp(54));
        }
        float actionTop = h - safeBottom - dp(78);
        float aw = (total - gap * 5) / 6f;
        btnSave.set(dp(12), actionTop, dp(12) + aw, actionTop + dp(46));
        btnExport.set(btnSave.right + gap, actionTop, btnSave.right + gap + aw, actionTop + dp(46));
        btnImport.set(btnExport.right + gap, actionTop, btnExport.right + gap + aw, actionTop + dp(46));
        btnUndo.set(btnImport.right + gap, actionTop, btnImport.right + gap + aw, actionTop + dp(46));
        btnNew.set(btnUndo.right + gap, actionTop, btnUndo.right + gap + aw, actionTop + dp(46));
        float py = safeTop + dp(60);
        btnPathCancel.set(dp(12), py, dp(12) + dp(74), py + dp(32));
        btnPathUndo.set(btnPathCancel.right + dp(6), py,
                btnPathCancel.right + dp(6) + dp(74), py + dp(32));
        btnPathFinish.set(w - dp(12) - dp(86), py, w - dp(12), py + dp(32));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (layoutW != getWidth() || layoutH != getHeight()) updateLayout();
        vTime += 0.016f;
        drawBackground(c);
        c.save();
        c.clipRect(playRect);
        drawGrid(c);
        drawGround(c);
        drawCup(c);
        drawFaucet(c);
        drawMechanisms(c);
        if (tool == TOOL_MOVE && endpointMoveMode) drawEndpointHandles(c);
        drawMotionPaths(c);
        drawTriggers(c);
        drawTexts(c);
        drawPending(c);
        if (orbitEditing) drawOrbitEdit(c);
        c.restore();
        drawBorder(c);
        drawTopBar(c);
        if (pathEditing) drawPathBar(c);
        else if (orbitEditing) drawOrbitBar(c);
        else drawToolbar(c);
        postInvalidateOnAnimation();
    }

    private void drawBackground(Canvas c) {
        fill.setShader(new LinearGradient(0, 0, 0, getHeight(),
                new int[]{0xFF041019, 0xFF0A202C, 0xFF06141E}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        fill.setShader(null);
        fill.setColor(0xE6081B27);
        c.drawRect(playRect, fill);
        fill.setShader(new RadialGradient(playRect.left + playRect.width() * 0.5f,
                playRect.top + playRect.height() * 0.25f, playRect.width() * 0.8f,
                new int[]{0x222B9EC9, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawRect(playRect, fill);
        fill.setShader(null);
    }

    private void drawGrid(Canvas c) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1f);
        stroke.setColor(0x143FB3D8);
        for (int i = 1; i < 6; i++) {
            float x = playRect.left + playRect.width() * i / 6f;
            c.drawLine(x, playRect.top, x, playRect.bottom, stroke);
        }
        for (int i = 1; i < 10; i++) {
            float y = playRect.top + playRect.height() * i / 10f;
            c.drawLine(playRect.left, y, playRect.right, y, stroke);
        }
    }

    private void drawGround(Canvas c) {
        float top = Y(0.944);
        fill.setShader(new LinearGradient(0, top, 0, playRect.bottom,
                new int[]{0xFF244A5A, 0xFF102A35, 0xFF081820}, null, Shader.TileMode.CLAMP));
        c.drawRect(playRect.left, top, playRect.right, playRect.bottom, fill);
        fill.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.4f));
        stroke.setColor(0xAA65D8F4);
        c.drawLine(playRect.left, top, playRect.right, top, stroke);
    }

    private void drawCup(Canvas c) {
        float l = X(level.cupCx - level.cupW() * 0.5);
        float r = X(level.cupCx + level.cupW() * 0.5);
        float rim = Y(level.cupRimY());
        float bottom = Y(level.cupBottomY);
        float rad = S(0.012f);
        path.reset();
        path.moveTo(l, rim);
        path.lineTo(l, bottom - rad);
        path.quadTo(l, bottom, l + rad, bottom);
        path.lineTo(r - rad, bottom);
        path.quadTo(r, bottom, r, bottom - rad);
        path.lineTo(r, rim);
        path.close();
        fill.setShader(new LinearGradient(0, rim, 0, bottom,
                new int[]{0x332E8AA8, 0x22174255}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(0xE6C8F4FF);
        c.drawPath(path, stroke);
        tmpRect.set(l, rim - S(0.007f), r, rim + S(0.007f));
        stroke.setStrokeWidth(dp(1.8f));
        stroke.setColor(0xFFD8F8FF);
        c.drawOval(tmpRect, stroke);
        drawTag(c, (l + r) * 0.5f, rim - dp(24), "拖动杯子", 0xCCDFF9FF, 0xCC0A2431);
        drawText(c, "杯量 200 颗 · 95% 通关", (l + r) * 0.5f, bottom + dp(18), 8.5f,
                0xAA8FC5D8, false, Paint.Align.CENTER);
    }

    private void drawFaucet(Canvas c) {
        float x = X(level.faucetX);
        float top = Y(-0.03);
        float bottom = Y(0.15);
        float hw = S(0.020f);
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
        drawTag(c, x + dp(52), Y(0.095f), "拖动龙头", 0xCCDFF9FF, 0xCC0A2431);
    }

    private void drawMechanisms(Canvas c) {
        for (Level.Support s : level.supports) {
            drawSupport(c, s);
        }
        for (Level.SegObj s : level.segments) {
            if (s.kind == Level.SEG_WALL) {
                drawLineObj(c, s.x1, s.y1, s.x2, s.y2, 0xFF9DB7C3, "挡板");
            } else if (s.kind == Level.SEG_FIRE_LINE) {
                drawLineObj(c, s.x1, s.y1, s.x2, s.y2, 0xFFFF7A3D, "火线 · 蒸发水");
            } else if (s.kind == Level.SEG_BOUNCE) {
                drawLineObj(c, s.x1, s.y1, s.x2, s.y2, 0xFF64F1CE, "弹性挡板 · 反弹");
            } else if (s.kind == Level.SEG_ONE_WAY) {
                drawLineObj(c, s.x1, s.y1, s.x2, s.y2, 0xFFB48CFF, "单向阀 · 箭头方向可过");
                drawOneWayArrow(c, s.x1, s.y1, s.x2, s.y2, 0xFFB48CFF);
            } else if (s.kind == Level.SEG_BREAKABLE) {
                drawLineObj(c, s.x1, s.y1, s.x2, s.y2, 0xFFB08A5E, "可破坏木板 · 球撞/火烧");
            }
        }
        for (Level.CircleObj o : level.circles) {
            float x = X(o.cx), y = Y(o.cy), r = S(o.radius);
            if (o.kind == Level.CIRCLE_FIRE_RING) {
                drawFireRing(c, x, y, r);
                drawTag(c, x, y - r - dp(16), "火环 · 蒸发水", 0xFFFFB08A, 0xCC36150C);
            } else if (o.kind == Level.CIRCLE_FIRE_BALL) {
                drawBall(c, x, y, r, 0xFFFF6B35, 0xFFFFD4A8);
                drawTag(c, x, y - r - dp(16), "火球 · 掉落 · 入杯失败", 0xFFFFB08A, 0xCC36150C);
            } else if (o.kind == Level.CIRCLE_BUBBLE) {
                drawBubble(c, x, y, r, 0xFF57D8FF, "泡泡水球");
            } else if (o.kind == Level.CIRCLE_POISON_BALL) {
                drawBubble(c, x, y, r, 0xFF7FE86B, "毒水球");
            } else if (o.kind == Level.CIRCLE_BALL) {
                drawBall(c, x, y, r, 0xFF4DA7D8, 0xFFD9F8FF);
                drawTag(c, x, y - r - dp(16), "圆球 · 动态碰撞", 0xFFBEEBFF, 0xCC102630);
            } else if (o.kind == Level.CIRCLE_PURIFIER) {
                drawPurifier(c, x, y, r);
                drawTag(c, x, y - r - dp(16), "毒水净化器", 0xFF9BFFE4, 0xCC0A2A27);
            } else if (o.kind == Level.CIRCLE_ICE) {
                drawIce(c, x, y, r);
                drawTag(c, x, y - r - dp(16), "冰砖 · 动态 · 遇火融化", 0xFFBDEFFF, 0xCC102630);
            } else {
                drawBall(c, x, y, r, 0xFF6C8794, 0xFFD7EDF6);
                drawTag(c, x, y - r - dp(16), "固定圆", 0xFFD8F1FA, 0xCC102630);
            }
        }
        for (Level.PoisonFaucet p : level.poisonFaucets) {
            drawPoisonFaucet(c, X(p.x), Y(p.y), p.count);
        }
    }

    private void drawPoisonFaucet(Canvas c, float x, float spout, int count) {
        float top = Y(-0.03);
        float hw = S(0.020f);
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
        drawTag(c, x, top + dp(15), "毒水龙头 ×" + count, 0xFFB9FF9C, 0xCC123017);
    }

    private void drawSupport(Canvas c, Level.Support s) {
        path.reset();
        for (int i = 0; i <= 16; i++) {
            float t = i / 16f;
            float x = X(s.px(t)), y = Y(s.py(t));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(S(0.010f));
        stroke.setColor(0xCC050D12);
        c.drawPath(path, stroke);
        int color = s.kind == Level.ARC_FIRE ? 0xFFFF7A3D
                : s.kind == Level.ARC_WALL ? 0xFF9DB7C3
                : s.kind == Level.ARC_BOUNCE ? 0xFF64F1CE
                : s.kind == Level.ARC_BREAKABLE ? 0xFFB08A5E
                : (s.kind == Level.ARC_ONE_WAY_IN || s.kind == Level.ARC_ONE_WAY_OUT)
                ? 0xFFB48CFF : 0xFFFFFFFF;
        stroke.setStrokeWidth(S(s.kind == Level.ARC_FIRE ? 0.0055f : 0.005f));
        stroke.setColor(color);
        c.drawPath(path, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        String label = s.kind == Level.ARC_FIRE ? "弧线火线 · 蒸发水"
                : s.kind == Level.ARC_WALL ? "弧线挡板"
                : s.kind == Level.ARC_BOUNCE ? "弹性弧板 · 反弹"
                : s.kind == Level.ARC_BREAKABLE ? "可破坏弧板"
                : s.kind == Level.ARC_ONE_WAY_IN ? "单向弧 · 只进弧内"
                : s.kind == Level.ARC_ONE_WAY_OUT ? "单向弧 · 只进弧外" : "弧挡板";
        drawTag(c, X(s.xm), Y(s.ym) - dp(16), label, color, 0xCC071B20);
        if (s.kind == Level.ARC_ONE_WAY_IN || s.kind == Level.ARC_ONE_WAY_OUT) {
            double dx = s.x2 - s.x1, dy = s.y2 - s.y1;
            double dl = Math.max(1e-6, Math.hypot(dx, dy));
            double cr = dx * (s.ym - s.y1) - dy * (s.xm - s.x1);
            double sg = cr >= 0 ? -1 : 1;
            double nx = -dy / dl * sg, ny = dx / dl * sg;
            if (s.kind == Level.ARC_ONE_WAY_OUT) { nx = -nx; ny = -ny; }
            drawArrowAt(c, X(s.xm), Y(s.ym), nx, ny, color);
        }
    }

    private void drawOneWayArrow(Canvas c, double x1, double y1, double x2, double y2,
                                 int color) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.max(1e-6, Math.hypot(dx, dy));
        double nx = -dy / len, ny = dx / len; // allowed direction
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

    private void drawLineObj(Canvas c, double x1, double y1, double x2, double y2,
                             int color, String label) {
        float ax = X(x1), ay = Y(y1), bx = X(x2), by = Y(y2);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(S(0.010f));
        stroke.setColor(0xCC050D12);
        c.drawLine(ax, ay, bx, by, stroke);
        stroke.setStrokeWidth(S(0.005f));
        stroke.setColor(color);
        c.drawLine(ax, ay, bx, by, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        drawTag(c, (ax + bx) * 0.5f, (ay + by) * 0.5f - dp(18), label, color, 0xCC071B20);
    }

    private void drawBall(Canvas c, float x, float y, float r, int outer, int inner) {
        fill.setShader(new RadialGradient(x - r * 0.3f, y - r * 0.35f, r * 1.2f,
                new int[]{inner, outer, 0xFF3A1208}, null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, fill);
        fill.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(0xCCFFFFFF);
        c.drawCircle(x, y, r, stroke);
    }

    private void drawFireRing(Canvas c, float x, float y, float r) {
        if (fireRingSprite != null && !fireRingSprite.isRecycled()) {
            c.save();
            c.rotate(vTime * 55f, x, y);
            float size = r * 3.05f;
            tmpRect.set(x - size * 0.5f, y - size * 0.5f, x + size * 0.5f, y + size * 0.5f);
            fill.setAlpha(255);
            c.drawBitmap(fireRingSprite, null, tmpRect, fill);
            c.restore();
            return;
        }
        c.save();
        c.rotate(vTime * 55f, x, y);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(4));
            stroke.setColor(0xFFFF7A3D);
            c.drawLine(x + (float) Math.cos(a) * r * 0.8f, y + (float) Math.sin(a) * r * 0.8f,
                    x + (float) Math.cos(a) * r * 1.25f, y + (float) Math.sin(a) * r * 1.25f, stroke);
        }
        c.restore();
        drawBall(c, x, y, r, 0xFFFF6B35, 0xFFFFE0B0);
    }

    private void drawBubble(Canvas c, float x, float y, float r, int color, String label) {
        fill.setColor((color & 0x00FFFFFF) | 0x33000000);
        c.drawCircle(x, y, r, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(color);
        c.drawCircle(x, y, r, stroke);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0xAAFFFFFF);
        c.drawArc(new RectF(x - r * .7f, y - r * .7f, x + r * .7f, y + r * .7f),
                200, 110, false, stroke);
        drawTag(c, x, y - r - dp(16), label, color, 0xCC071B20);
    }

    private void drawTexts(Canvas c) {
        for (Level.TextObj t : level.texts) {
            RichText.draw(c, t.text, X(t.x), Y(t.y), dp(13), 0xFFE8F8FF, dp(4));
        }
    }

    private void drawPending(Canvas c) {
        if (!draggingArc && !draggingSegment && !draggingCircle) return;
        if (draggingArc || draggingSegment) {
            float ax = X(dragStartX), ay = Y(dragStartY);
            float bx = X(dragEndX), by = Y(dragEndY);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeWidth(S(0.008f));
            stroke.setColor(0xAA8AFFF2);
            stroke.setPathEffect(new DashPathEffect(new float[]{dp(9), dp(6)}, -vTime * 30));
            if (draggingArc) {
                double[] mid = supportMid(dragStartX, dragStartY, dragEndX, dragEndY);
                path.reset();
                Level.Support tmp = new Level.Support(dragStartX, dragStartY,
                        mid[0], mid[1], dragEndX, dragEndY, arcKind);
                for (int i = 0; i <= 20; i++) {
                    float t = i / 20f;
                    float x = X(tmp.px(t)), y = Y(tmp.py(t));
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                c.drawPath(path, stroke);
            } else {
                c.drawLine(ax, ay, bx, by, stroke);
            }
            stroke.setPathEffect(null);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            fill.setColor(0xCC061A22);
            c.drawCircle(ax, ay, dp(6), fill);
            c.drawCircle(bx, by, dp(6), fill);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.5f));
            stroke.setColor(0xFF8AFFF2);
            c.drawCircle(ax, ay, dp(6), stroke);
            c.drawCircle(bx, by, dp(6), stroke);
            drawTag(c, (ax + bx) * .5f, (ay + by) * .5f - dp(16),
                    String.format(Locale.US, "%.0f cm", Math.hypot(dragEndX - dragStartX,
                            dragEndY - dragStartY) * 100), 0xFFC8FFF8, 0xDD061E24);
        } else {
            float cx = X(dragStartX), cy = Y(dragStartY);
            float rad = (float) Math.hypot(dragEndX - dragStartX, dragEndY - dragStartY) * ppm;
            fill.setColor(0x224DF4D0);
            c.drawCircle(cx, cy, rad, fill);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.5f));
            stroke.setColor(0xFF8AFFF2);
            stroke.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(6)}, -vTime * 30));
            c.drawCircle(cx, cy, rad, stroke);
            stroke.setPathEffect(null);
            c.drawLine(cx, cy, cx + rad, cy, stroke);
            drawTag(c, cx, cy - rad - dp(14), String.format(Locale.US, "R %.0f cm", rad / ppm * 100),
                    0xFFC8FFF8, 0xDD061E24);
        }
        if (snapHot) {
            float x = X(snapMarkX), y = Y(snapMarkY);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(0xFFFFE08A);
            c.drawCircle(x, y, dp(9), stroke);
            c.drawLine(x - dp(12), y, x + dp(12), y, stroke);
            c.drawLine(x, y - dp(12), x, y + dp(12), stroke);
        }
    }

    private void drawBorder(Canvas c) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.2f));
        stroke.setColor(0x5537B5DA);
        float r = dp(12);
        c.drawRoundRect(playRect, r, r, stroke);
    }

    private void drawTopBar(Canvas c) {
        drawText(c, "关卡编辑器", dp(16), safeTop + dp(30), 22, C_TEXT, true);
        drawText(c, currentHint(), dp(16), safeTop + dp(50), 9.5f, C_DIM, false);
        fill.setShader(new LinearGradient(btnPlay.left, btnPlay.top, btnPlay.right, btnPlay.bottom,
                new int[]{0xFF44D4FF, 0xFF63F1CE}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(btnPlay, dp(14), dp(14), fill);
        fill.setShader(null);
        drawText(c, "试玩 ▶", btnPlay.centerX(), btnPlay.centerY() + dp(5), 12, 0xFF06202A, true,
                Paint.Align.CENTER);
        boolean hasTut = TutorialStore.hasCustom(getContext(), level.name);
        drawSmallButton(c, btnTutorial, "教程", hasTut ? 0xFF1E4B5C : 0xFF152B34);
        drawSmallButton(c, btnCover13, "导出教程", 0xFF4B2A1E);
    }

    private void drawPathBar(Canvas c) {
        drawText(c, "运动路径 · 点空白加节点，拖动节点调整", dp(14), safeTop + dp(52),
                9.0f, 0xFF9CC8D8, false);
        drawSmallButton(c, btnPathCancel, "取消", 0xFF3F2B20);
        drawSmallButton(c, btnPathUndo, "撤销点", 0xFF1E3B4A);
        drawSmallButton(c, btnPathFinish, "完成", 0xFF1E4B5C);
    }

    private void drawToolbar(Canvas c) {
        String[] names = {"杯子", "龙头", "移动", "添加", "毒龙头", "删除", "运动"};
        String[] subs = {"拖动", "拖动", endpointMoveMode ? "端点" : "整体", addLabel(),
                "×" + poisonCount, "点选", "设置"};
        for (int i = 0; i < 7; i++) {
            RectF r = toolRects[i];
            boolean active = i == 3 ? isAddTool() : tool == barTool(i);
            fill.setColor(active ? 0xEE123B4A : 0xDD0A2431);
            c.drawRoundRect(r, dp(12), dp(12), fill);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(active ? dp(1.8f) : dp(1));
            stroke.setColor(active ? 0xFF68E8FF : 0x4439C8EE);
            c.drawRoundRect(r, dp(12), dp(12), stroke);
            drawText(c, names[i], r.centerX(), r.top + dp(20), 11,
                    active ? 0xFFDFFBFF : 0xFFBFE7F2, true, Paint.Align.CENTER);
            drawText(c, subs[i], r.centerX(), r.top + dp(37), 8.5f,
                    active ? 0xFF8FE8FF : 0x887FA8B8, false, Paint.Align.CENTER);
        }
        drawSmallButton(c, btnSave, "保存", 0xFF1E4B5C);
        drawSmallButton(c, btnExport, "导出", 0xFF204030);
        drawSmallButton(c, btnImport, "导入", 0xFF203A4A);
        drawSmallButton(c, btnUndo, "撤销", 0xFF1E3B4A);
        drawSmallButton(c, btnNew, "新建", 0xFF3F2B20);
    }

    private int barTool(int i) {
        if (i == 0) return TOOL_CUP;
        if (i == 1) return TOOL_FAUCET;
        if (i == 2) return TOOL_MOVE;
        if (i == 4) return TOOL_POISON;
        if (i == 5) return TOOL_ERASE;
        if (i == 6) return TOOL_MOTION;
        return -1;
    }

    private boolean isAddTool() {
        return tool == TOOL_SEG || tool == TOOL_ARC || tool == TOOL_CIRCLE || tool == TOOL_TEXT;
    }

    private String addLabel() {
        if (tool == TOOL_SEG) return segKind == Level.SEG_FIRE_LINE ? "火线" : "挡板";
        if (tool == TOOL_ARC) {
            if (arcKind == Level.ARC_FIRE) return "弧火线";
            if (arcKind == Level.ARC_WALL) return "弧挡板";
            if (arcKind == Level.ARC_BOUNCE) return "弹性弧";
            if (arcKind == Level.ARC_BREAKABLE) return "破弧板";
            if (arcKind == Level.ARC_ONE_WAY_IN) return "进内弧";
            if (arcKind == Level.ARC_ONE_WAY_OUT) return "出内弧";
            return "弧挡板";
        }
        if (tool == TOOL_CIRCLE) return circleName(circleKind);
        if (tool == TOOL_TEXT) return "文字";
        return "线/圆/字";
    }

    private void onBarTool(int i) {
        triggerPlacement = false;
        if (i == 3) { showAddMenu(); return; }
        int t = barTool(i);
        if (t < 0) return;
        if (t == TOOL_POISON) { chooseTool(TOOL_POISON); return; }
        if (t == TOOL_MOVE) { showMoveMenu(); return; }
        tool = t;
        pending = null;
        endpointObject = null; endpointIndex = -1;
        if (t == TOOL_MOTION) toast("点一个机关，设置运动路径");
        invalidate();
    }

    private void showMoveMenu() {
        String[] items = {"整体移动（拖动整个机关）", "仅端点移动（拖动控制点改变形状）"};
        new AlertDialog.Builder(getContext()).setTitle("移动方式")
                .setItems(items, (d, w) -> {
                    endpointMoveMode = w == 1;
                    tool = TOOL_MOVE;
                    endpointObject = null;
                    endpointIndex = -1;
                    pending = null;
                    toast(endpointMoveMode ? "拖动端点/半径控制点" : "拖动整个机关");
                    invalidate();
                }).show();
    }

    private void showTriggerDialog(int mode) {
        EditText angle = numberField(mode == 1 ? 0 : triggerAngle);
        EditText speed = numberField(mode == 1 ? triggerMoveSpeed : triggerSpeed);
        EditText count = new EditText(getContext());
        count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        count.setText(String.valueOf(triggerCount));
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) dp(18), (int) dp(4), (int) dp(18), 0);
        EditText endX = null, endY = null;
        CheckBox relative = null;
        if (mode == 1) {
            relative = new CheckBox(getContext());
            relative.setText("终点使用相对坐标");
            relative.setTextSize(11);
            relative.setChecked(triggerEndpointRelative);
            endX = numberField(triggerEndX);
            endY = numberField(triggerEndY);
            box.addView(relative);
            box.addView(label("终点 X（绝对坐标，或相对偏移）"));
            box.addView(endX);
            box.addView(label("终点 Y（绝对坐标，或相对偏移）"));
            box.addView(endY);
            box.addView(label("平移速度 m/s"));
            box.addView(speed);
        } else {
            box.addView(label("旋转角度 °（可负，正=顺时针）"));
            box.addView(angle);
            box.addView(label("角速度 °/s"));
            box.addView(speed);
        }
        box.addView(label("触发次数（0 = 无限）"));
        box.addView(count);
        final EditText fx = endX, fy = endY;
        final CheckBox frel = relative;
        new AlertDialog.Builder(getContext()).setTitle(
                        mode == 1 ? "平移触发器" : mode == 2 ? "定点旋转触发器" : "旋转触发器")
                .setMessage("设置后点一个无物理机关放置触发点；终点可在端点模式拖动")
                .setView(box)
                .setPositiveButton("去放置", (d, w) -> {
                    triggerMode = mode;
                    triggerAngle = clampDouble(parse(angle, mode == 1 ? 0 : 90), -3600, 3600);
                    triggerSpeed = clampDouble(parse(speed, 90), -1440, 1440);
                    triggerMoveSpeed = clampDouble(parse(speed, 0.10), 0.01, 2.0);
                    if (mode == 1 && fx != null && fy != null) {
                        triggerEndpointRelative = frel != null && frel.isChecked();
                        triggerEndX = parse(fx, 0.5);
                        triggerEndY = parse(fy, 0.5);
                    }
                    try { triggerCount = Math.max(0, Integer.parseInt(count.getText().toString().trim())); }
                    catch (Exception e) { triggerCount = 0; }
                    triggerPlacement = true;
                    tool = TOOL_MOVE;
                    endpointMoveMode = false;
                    toast("点一个无物理机关放置触发器");
                    invalidate();
                }).setNegativeButton("取消", null).show();
    }

    private void placeTrigger(double wx, double wy) {
        Object hit = findObject(wx, wy);
        if (hit == null) { toast("点中一个机关"); return; }
        if (hit instanceof Level.CircleObj && isDynamicCircle((Level.CircleObj) hit)) {
            toast("触发器只能附着在无物理机关上");
            return;
        }
        int type;
        int index;
        double cx, cy;
        if (hit instanceof Level.SegObj) {
            type = 0; index = level.segments.indexOf(hit);
            cx = ((Level.SegObj) hit).centerX(); cy = ((Level.SegObj) hit).centerY();
        } else if (hit instanceof Level.Support) {
            type = 1; index = level.supports.indexOf(hit);
            cx = ((Level.Support) hit).centerX(); cy = ((Level.Support) hit).centerY();
        } else if (hit instanceof Level.CircleObj) {
            type = 2; index = level.circles.indexOf(hit);
            cx = ((Level.CircleObj) hit).cx; cy = ((Level.CircleObj) hit).cy;
        } else {
            toast("只能附着在线、弧线或圆形机关上");
            return;
        }
        pushUndo();
        level.triggers.add(new Level.RotateTrigger(type, index, wx - cx, wy - cy,
                triggerAngle, triggerSpeed, triggerCount, triggerMode,
                triggerDistance, triggerMoveSpeed, triggerMode == 1,
                triggerEndpointRelative, triggerEndX, triggerEndY));
        save();
        triggerPlacement = false;
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        invalidate();
    }

    private double[] triggerCenter(Level.RotateTrigger t) {
        if (t.targetType == 0 && t.targetIndex >= 0 && t.targetIndex < level.segments.size()) {
            Level.SegObj s = level.segments.get(t.targetIndex);
            return new double[]{s.centerX(), s.centerY()};
        }
        if (t.targetType == 1 && t.targetIndex >= 0 && t.targetIndex < level.supports.size()) {
            Level.Support s = level.supports.get(t.targetIndex);
            return new double[]{s.centerX(), s.centerY()};
        }
        if (t.targetType == 2 && t.targetIndex >= 0 && t.targetIndex < level.circles.size()) {
            Level.CircleObj c = level.circles.get(t.targetIndex);
            return new double[]{c.cx, c.cy};
        }
        return null;
    }

    private void drawTriggers(Canvas c) {
        for (Level.RotateTrigger t : level.triggers) {
            double[] ctr = triggerCenter(t);
            if (ctr == null) continue;
            double sx = ctr[0] + t.offsetX, sy = ctr[1] + t.offsetY;
            float tx = X(sx), ty = Y(sy);
            int tc = t.mode == 1 ? 0xFFB48CFF : t.mode == 2 ? 0xFFFF6B4A : 0xFFFFB347;
            if (t.mode == 1 && t.hasEndpoint) {
                double ex = t.endpointRelative ? sx + t.endX : t.endX;
                double ey = t.endpointRelative ? sy + t.endY : t.endY;
                int ec = desaturate(tc, 0.5f);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(1.5f));
                stroke.setColor(ec);
                stroke.setPathEffect(t.endpointRelative ? null
                        : new DashPathEffect(new float[]{dp(7), dp(5)}, -vTime * 16));
                c.drawLine(tx, ty, X(ex), Y(ey), stroke);
                stroke.setPathEffect(null);
                fill.setColor(ec);
                c.drawCircle(X(ex), Y(ey), dp(6), fill);
                stroke.setColor(0xFFFFFFFF);
                c.drawCircle(X(ex), Y(ey), dp(6), stroke);
            }
            float cx = X(ctr[0]), cy = Y(ctr[1]);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(0x55FFFFFF);
            c.drawLine(cx, cy, tx, ty, stroke);
            path.reset();
            path.moveTo(tx, ty - dp(7));
            path.lineTo(tx + dp(7), ty);
            path.lineTo(tx, ty + dp(7));
            path.lineTo(tx - dp(7), ty);
            path.close();
            fill.setColor(tc);
            c.drawPath(path, fill);
            drawText(c, String.format(Locale.US, "%s %s", t.mode == 1 ? "平移"
                            : t.mode == 2 ? "定点" : "旋转",
                            t.count == 0 ? "∞" : ("×" + t.count)),
                    tx + dp(10), ty - dp(8), 8, tc, false);
        }
    }

    private int desaturate(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] *= factor;
        return Color.HSVToColor(0xFF, hsv);
    }


    private void showAddMenu() {
        String[] items = {"直线", "弧线", "圆", "文字",
                "平移触发器（紫）", "旋转触发器·中心（橙）",
                "定点旋转触发器（橙红）"};
        new AlertDialog.Builder(getContext()).setTitle("添加")
                .setItems(items, (d, w) -> {
                    if (w == 0) showStraightMenu();
                    else if (w == 1) showArcMenu();
                    else if (w == 2) chooseTool(TOOL_CIRCLE);
                    else if (w == 3) chooseTool(TOOL_TEXT);
                    else if (w == 4) showTriggerDialog(1);
                    else if (w == 5) showTriggerDialog(0);
                    else showTriggerDialog(2);
                }).show();
    }

    private boolean grabEndpoint(double x, double y) {
        double best = 0.026;
        Object hit = null;
        int idx = -1;
        for (Level.SegObj s : level.segments) {
            double[][] pts = {{s.x1, s.y1}, {s.x2, s.y2}};
            for (int i = 0; i < 2; i++) {
                double d = Math.hypot(pts[i][0] - x, pts[i][1] - y);
                if (d < best) { best = d; hit = s; idx = i; }
            }
        }
        for (Level.Support s : level.supports) {
            double[][] pts = {{s.x1, s.y1}, {s.xm, s.ym}, {s.x2, s.y2}};
            for (int i = 0; i < 3; i++) {
                double d = Math.hypot(pts[i][0] - x, pts[i][1] - y);
                if (d < best) { best = d; hit = s; idx = i; }
            }
        }
        for (Level.CircleObj o : level.circles) {
            double d = Math.hypot(o.cx - x, o.cy - y);
            if (d < best) { best = d; hit = o; idx = 0; }
            d = Math.hypot(o.cx + o.radius - x, o.cy - y);
            if (d < best) { best = d; hit = o; idx = 1; }
        }
        for (Level.RotateTrigger t : level.triggers) {
            double[] ctr = triggerCenter(t);
            if (ctr == null) continue;
            double sx = ctr[0] + t.offsetX, sy = ctr[1] + t.offsetY;
            double d = Math.hypot(sx - x, sy - y);
            if (d < best) { best = d; hit = t; idx = 0; }
            if (t.mode == 1 && t.hasEndpoint) {
                double ex = t.endpointRelative ? sx + t.endX : t.endX;
                double ey = t.endpointRelative ? sy + t.endY : t.endY;
                d = Math.hypot(ex - x, ey - y);
                if (d < best) { best = d; hit = t; idx = 1; }
            }
        }
        if (hit == null) return false;
        pushUndo();
        endpointObject = hit;
        endpointIndex = idx;
        moveLastX = x; moveLastY = y;
        invalidate();
        return true;
    }

    private void updateEndpoint(double x, double y) {
        if (endpointObject instanceof Level.RotateTrigger) {
            Level.RotateTrigger t = (Level.RotateTrigger) endpointObject;
            double[] ctr = triggerCenter(t);
            if (ctr == null) return;
            double sx = ctr[0] + t.offsetX, sy = ctr[1] + t.offsetY;
            if (endpointIndex == 0) {
                t.offsetX = x - ctr[0];
                t.offsetY = y - ctr[1];
            } else if (t.mode == 1) {
                t.hasEndpoint = true;
                if (t.endpointRelative) {
                    t.endX = x - sx; t.endY = y - sy;
                } else {
                    t.endX = x; t.endY = y;
                }
            }
            return;
        }
        if (endpointObject instanceof Level.CircleObj) {
            Level.CircleObj o = (Level.CircleObj) endpointObject;
            if (endpointIndex == 0) {
                o.cx = clamp((float) x, 0.02f, 0.58f);
                o.cy = clamp((float) y, 0.02f, 0.92f);
            } else {
                o.radius = clamp((float) Math.hypot(x - o.cx, y - o.cy), 0.012f, 0.16f);
            }
            return;
        }
        double sx = x, sy = y;
        double[] snap = snapPointExcluding(x, y, endpointObject);
        if (Math.hypot(snap[0] - x, snap[1] - y) < 0.030) { sx = snap[0]; sy = snap[1]; }
        if (endpointObject instanceof Level.SegObj) {
            Level.SegObj s = (Level.SegObj) endpointObject;
            if (endpointIndex == 0) { s.x1 = sx; s.y1 = sy; }
            else { s.x2 = sx; s.y2 = sy; }
            if (Math.hypot(s.x2 - s.x1, s.y2 - s.y1) < 0.012) {
                if (endpointIndex == 0) { s.x1 = x; s.y1 = y; }
                else { s.x2 = x; s.y2 = y; }
            }
        } else if (endpointObject instanceof Level.Support) {
            Level.Support s = (Level.Support) endpointObject;
            if (endpointIndex == 0) { s.x1 = sx; s.y1 = sy; }
            else if (endpointIndex == 1) { s.xm = clamp((float) x, 0.015f, 0.585f);
                s.ym = clamp((float) y, 0.02f, 0.95f); }
            else { s.x2 = sx; s.y2 = sy; }
        }
    }

    private void drawEndpointHandles(Canvas c) {
        for (Level.SegObj s : level.segments) {
            handle(c, s.x1, s.y1, 0xFFB48CFF);
            handle(c, s.x2, s.y2, 0xFFB48CFF);
        }
        for (Level.Support s : level.supports) {
            handle(c, s.x1, s.y1, 0xFF6CF3CB);
            handle(c, s.xm, s.ym, 0xFFFFC85C);
            handle(c, s.x2, s.y2, 0xFF6CF3CB);
        }
        for (Level.CircleObj o : level.circles) {
            handle(c, o.cx, o.cy, 0xFF7FDFFF);
            float hx = (float) (o.cx + o.radius), hy = (float) o.cy;
            fill.setColor(0x66101E28);
            c.drawCircle(X(hx), Y(hy), dp(7), fill);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.5f));
            stroke.setColor(0xFFBDEFFF);
            c.drawCircle(X(hx), Y(hy), dp(7), stroke);
            c.drawLine(X(o.cx), Y(o.cy), X(hx), Y(hy), stroke);
        }
    }

    private void handle(Canvas c, double x, double y, int color) {
        fill.setColor(0x88101E28);
        c.drawCircle(X(x), Y(y), dp(7), fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(color);
        c.drawCircle(X(x), Y(y), dp(7), stroke);
    }

    private void showStraightMenu() {
        String[] items = {"直线挡板", "直线火线", "弹性挡板（直线）",
                "单向阀（直线，箭头方向可过）", "可破坏木板（直线）"};
        new AlertDialog.Builder(getContext()).setTitle("添加直线")
                .setItems(items, (d, w) -> {
                    pending = null;
                    tool = TOOL_SEG;
                    if (w == 0) segKind = Level.SEG_WALL;
                    else if (w == 1) segKind = Level.SEG_FIRE_LINE;
                    else if (w == 2) segKind = Level.SEG_BOUNCE;
                    else if (w == 3) segKind = Level.SEG_ONE_WAY;
                    else segKind = Level.SEG_BREAKABLE;
                    invalidate();
                }).show();
    }

    private void showArcMenu() {
        String[] items = {"弧线挡板（手绘）", "弧线火线（手绘）", "弹性弧板（手绘）",
                "可破坏弧板（手绘）", "单向弧 · 只进弧内",
                "单向弧 · 只进弧外"};
        new AlertDialog.Builder(getContext()).setTitle("添加弧线")
                .setItems(items, (d, w) -> {
                    pending = null;
                    tool = TOOL_ARC;
                    if (w == 0) arcKind = Level.ARC_WALL;
                    else if (w == 1) arcKind = Level.ARC_FIRE;
                    else if (w == 2) arcKind = Level.ARC_BOUNCE;
                    else if (w == 3) arcKind = Level.ARC_BREAKABLE;
                    else if (w == 4) arcKind = Level.ARC_ONE_WAY_IN;
                    else arcKind = Level.ARC_ONE_WAY_OUT;
                    invalidate();
                }).show();
    }

    private String currentHint() {
        if (tool == TOOL_SEG) return "拖出" + segName(segKind) + " · 直线";
        if (tool == TOOL_ARC) return "按住并画出弧线 · 松手生成"
                + (arcKind == Level.ARC_FIRE ? "弧线火线"
                : arcKind == Level.ARC_WALL ? "弧线挡板"
                : arcKind == Level.ARC_BOUNCE ? "弹性弧板"
                : arcKind == Level.ARC_BREAKABLE ? "可破坏弧板"
                : arcKind == Level.ARC_ONE_WAY_IN ? "单向弧·只进内"
                : arcKind == Level.ARC_ONE_WAY_OUT ? "单向弧·只进外" : "弧挡板");
        if (tool == TOOL_CIRCLE) return "点圆心再点圆周，放置" + circleName(circleKind);
        if (tool == TOOL_POISON) return "在上方拖动毒水龙头（" + poisonCount + " 颗）";
        if (tool == TOOL_ERASE) return "点机关、弧线或文字删除";
        if (tool == TOOL_TEXT) return "点地图放置文字；先用移动工具调整位置";
        if (tool == TOOL_MOTION) return "点机关设置运动路径";
        if (tool == TOOL_MOVE) return "拖动已有机关调整位置";
        if (tool == TOOL_CUP) return "拖动杯子改变位置";
        return "拖动水龙头改变位置";
    }

    private void drawSmallButton(Canvas c, RectF r, String s, int color) {
        fill.setColor(color);
        c.drawRoundRect(r, dp(12), dp(12), fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x553BC5E8);
        c.drawRoundRect(r, dp(12), dp(12), stroke);
        drawText(c, s, r.centerX(), r.centerY() + dp(4.5f), 11, 0xFFD3EFF8, false,
                Paint.Align.CENTER);
    }

    private void drawTag(Canvas c, float x, float y, String s, int color, int bg) {
        txt.setTypeface(tfMedium);
        txt.setTextSize(dp(8.5f));
        txt.setTextAlign(Paint.Align.LEFT);
        float w = txt.measureText(s) + dp(10);
        tmpRect.set(x - w * .5f, y - dp(8), x + w * .5f, y + dp(8));
        fill.setColor(bg);
        c.drawRoundRect(tmpRect, dp(8), dp(8), fill);
        txt.setColor(color);
        c.drawText(s, x - w * .5f + dp(5), y + dp(3), txt);
    }

    private void drawText(Canvas c, String s, float x, float y, float sp, int color,
                          boolean bold, Paint.Align align) {
        txt.setTypeface(bold ? tfMedium : tfRegular);
        txt.setTextSize(dp(sp));
        txt.setColor(color);
        txt.setTextAlign(align);
        c.drawText(s, x, y, txt);
    }

    private void drawText(Canvas c, String s, float x, float y, float sp, int color, boolean bold) {
        drawText(c, s, x, y, sp, color, bold, Paint.Align.LEFT);
    }

    private String segName(int kind) {
        if (kind == Level.SEG_FIRE_LINE) return "火线";
        if (kind == Level.SEG_WALL) return "挡板";
        if (kind == Level.SEG_BOUNCE) return "弹性挡板";
        if (kind == Level.SEG_ONE_WAY) return "单向阀";
        if (kind == Level.SEG_BREAKABLE) return "可破坏木板";
        return "挡板";
    }

    private String circleName(int kind) {
        switch (kind) {
            case Level.CIRCLE_FIRE_RING: return "火环";
            case Level.CIRCLE_FIRE_BALL: return "火球";
            case Level.CIRCLE_BUBBLE: return "泡泡";
            case Level.CIRCLE_POISON_BALL: return "毒球";
            case Level.CIRCLE_BALL: return "圆球";
            case Level.CIRCLE_PURIFIER: return "净化器";
            case Level.CIRCLE_ICE: return "冰砖";
            default: return "固定圆";
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (orbitEditing) { handleOrbitTouch(e); return true; }
        if (pathEditing) { handlePathTouch(e); return true; }
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (btnPlay.contains(x, y)) {
                    save();
                    Intent i = new Intent(getContext(), PlayActivity.class);
                    i.putExtra("custom_level", true);
                    i.putExtra("editor_mode", true);
                    ((Activity) getContext()).startActivity(i);
                    return true;
                }
                if (btnCover13.contains(x, y)) {
                    String path = TutorialStore.exportArchive(getContext());
                    toast(path == null ? "导出失败" : "教程 JSON 已导出：" + path);
                    return true;
                }
                if (btnTutorial.contains(x, y)) {
                    if (TutorialStore.hasCustom(getContext(), level.name)) {
                        Intent i = new Intent(getContext(), PlayActivity.class);
                        i.putExtra("custom_level", true);
                        i.putExtra("editor_mode", true);
                        i.putExtra("tutorial", true);
                        i.putExtra("tutorial_name", level.name);
                        ((Activity) getContext()).startActivity(i);
                    } else {
                        toast("先试玩通关一次，会自动录制官方教程");
                    }
                    return true;
                }
                if (btnSave.contains(x, y)) { save(); toast("已保存"); return true; }
                if (btnExport.contains(x, y)) { exportFile(); return true; }
                if (btnImport.contains(x, y)) { importFile(); return true; }
                if (btnUndo.contains(x, y)) { undo(); return true; }
                if (btnNew.contains(x, y)) { confirmNew(); return true; }
                for (int i = 0; i < 7; i++) {
                    if (toolRects[i].contains(x, y)) { onBarTool(i); return true; }
                }
                if (!playRect.contains(x, y)) return true;
                handleCanvasDown(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleCanvasMove(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                handleCanvasUp();
                return true;
            case MotionEvent.ACTION_CANCEL:
                draggingCup = draggingFaucet = draggingSegment = draggingCircle = draggingArc = false;
                movingObject = null;
                endpointObject = null;
                endpointIndex = -1;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void handleCanvasDown(float sx, float sy) {
        float wx = clamp((sx - playRect.left) / ppm, 0.015f, 0.585f);
        float wy = clamp((sy - playRect.top) / ppm, 0.02f, 0.95f);
        if (triggerPlacement) {
            placeTrigger(wx, wy);
            return;
        }
        if (tool == TOOL_CUP) {
            draggingCup = true;
            level.cupCx = clamp(wx, 0.12f, 0.48f);
            level.cupBottomY = clamp(wy, 0.52f, 0.940f);
            invalidate();
        } else if (tool == TOOL_FAUCET) {
            draggingFaucet = true;
            level.faucetX = clamp(wx, 0.04f, 0.56f);
            invalidate();
        } else if (tool == TOOL_MOVE) {
            if (endpointMoveMode) {
                if (!grabEndpoint(wx, wy)) toast("点中端点/半径控制点后再拖动");
                return;
            }
            movingObject = findObject(wx, wy);
            if (movingObject == null) { toast("点中机关后再拖动"); return; }
            pushUndo();
            moveLastX = wx; moveLastY = wy;
            invalidate();
        } else if (tool == TOOL_ARC) {
            double[] p = snapPoint(wx, wy);
            dragStartX = p[0]; dragStartY = p[1];
            dragEndX = dragStartX; dragEndY = dragStartY;
            dragPathX.clear(); dragPathY.clear();
            dragPathX.add((float) dragStartX); dragPathY.add((float) dragStartY);
            draggingArc = true;
            invalidate();
        } else if (tool == TOOL_SEG) {
            double[] p = snapPoint(wx, wy);
            dragStartX = p[0]; dragStartY = p[1];
            dragEndX = dragStartX; dragEndY = dragStartY;
            draggingSegment = true;
            invalidate();
        } else if (tool == TOOL_CIRCLE) {
            double[] p = snapPoint(wx, wy);
            dragStartX = p[0]; dragStartY = p[1];
            dragEndX = dragStartX; dragEndY = dragStartY;
            draggingCircle = true;
            invalidate();
        } else if (tool == TOOL_POISON) {
            Object hit = findObject(wx, wy);
            if (hit instanceof Level.PoisonFaucet) {
                pushUndo();
                movingObject = hit;
                moveLastX = wx; moveLastY = wy;
            } else {
                pushUndo();
                Level.PoisonFaucet p = new Level.PoisonFaucet(
                        clamp(wx, 0.04f, 0.56f), Level.POISON_FAUCET_Y, poisonCount);
                level.poisonFaucets.add(p);
                movingObject = p;
                moveLastX = wx; moveLastY = wy;
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            invalidate();
        } else if (tool == TOOL_MOTION) {
            Object hit = findObject(wx, wy);
            if (hit instanceof Level.CircleObj && isDynamicCircle((Level.CircleObj) hit)) {
                toast("有物理的机关不能设置运动");
            } else if (hit instanceof Level.SegObj) configureMotion(hit,
                    ((Level.SegObj) hit).centerX(), ((Level.SegObj) hit).centerY());
            else if (hit instanceof Level.CircleObj) configureMotion(hit,
                    ((Level.CircleObj) hit).cx, ((Level.CircleObj) hit).cy);
            else if (hit instanceof Level.Support) configureMotion(hit,
                    ((Level.Support) hit).centerX(), ((Level.Support) hit).centerY());
            else toast("只能给挡板、火线、弧线或静态圆形机关设置运动");
        } else if (tool == TOOL_TEXT) {
            Object hit = findObject(wx, wy);
            if (hit instanceof Level.TextObj) {
                editText((Level.TextObj) hit);
            } else if (!pendingText.isEmpty()) {
                pushUndo();
                lastText = pendingText;
                getContext().getSharedPreferences("editor_ui", Context.MODE_PRIVATE)
                        .edit().putString("last_text", lastText).apply();
                level.texts.add(new Level.TextObj(wx, wy, pendingText));
                save();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                tool = TOOL_MOVE;
                pendingText = "";
            } else {
                toast("先点「文字」输入内容");
            }
            invalidate();
        } else if (tool == TOOL_ERASE) {
            eraseAt(wx, wy);
        }
    }

    private void handleCanvasMove(float sx, float sy) {
        float wx = clamp((sx - playRect.left) / ppm, 0.015f, 0.585f);
        float wy = clamp((sy - playRect.top) / ppm, 0.02f, 0.95f);
        if (endpointObject != null) {
            updateEndpoint(wx, wy);
            invalidate();
            return;
        }
        if (draggingCup) {
            level.cupCx = clamp(wx, 0.12f, 0.48f);
            level.cupBottomY = clamp(wy, 0.52f, 0.940f);
            invalidate();
        } else if (draggingFaucet) {
            level.faucetX = clamp(wx, 0.04f, 0.56f);
            invalidate();
        } else if (draggingArc) {
            if (dragPathX.size() < 120) {
                dragPathX.add(wx); dragPathY.add(wy);
            }
            dragEndX = wx; dragEndY = wy;
            invalidate();
        } else if (draggingSegment) {
            double[] p = snapPoint(wx, wy);
            dragEndX = p[0]; dragEndY = p[1];
            invalidate();
        } else if (draggingCircle) {
            dragEndX = wx; dragEndY = wy;
            invalidate();
        } else if (movingObject != null) {
            double dx = wx - moveLastX, dy = wy - moveLastY;
            if (movingObject instanceof Level.PoisonFaucet) {
                Level.PoisonFaucet p = (Level.PoisonFaucet) movingObject;
                p.x = clamp(wx, 0.04f, 0.56f);
                p.y = Level.POISON_FAUCET_Y;
            } else if (movingObject instanceof Level.CircleObj) {
                Level.CircleObj o = (Level.CircleObj) movingObject;
                o.cx = clamp(o.cx + dx, 0.02f, 0.58f);
                o.cy = clamp(o.cy + dy, 0.02f, 0.92f);
            } else if (movingObject instanceof Level.TextObj) {
                Level.TextObj t = (Level.TextObj) movingObject;
                t.x = clamp(t.x + dx, 0.02f, 0.58f);
                t.y = clamp(t.y + dy, 0.02f, 0.92f);
            } else if (movingObject instanceof Level.Support) {
                Level.Support su = (Level.Support) movingObject;
                translateMotion(su.motion, dx, dy);
                su.x1 = clamp(su.x1 + dx, 0.015f, 0.585f);
                su.x2 = clamp(su.x2 + dx, 0.015f, 0.585f);
                su.xm = clamp(su.xm + dx, 0.015f, 0.585f);
                su.y1 = clamp(su.y1 + dy, 0.02f, 0.95f);
                su.y2 = clamp(su.y2 + dy, 0.02f, 0.95f);
                su.ym = clamp(su.ym + dy, 0.02f, 0.95f);
            } else if (movingObject instanceof Level.Seg) {
                Level.Seg sg = (Level.Seg) movingObject;
                sg.x1 = clamp(sg.x1 + dx, 0.015f, 0.585f);
                sg.x2 = clamp(sg.x2 + dx, 0.015f, 0.585f);
                sg.y1 = clamp(sg.y1 + dy, 0.02f, 0.95f);
                sg.y2 = clamp(sg.y2 + dy, 0.02f, 0.95f);
                if (sg instanceof Level.SegObj) translateMotion(((Level.SegObj) sg).motion, dx, dy);
                double[] q = snapPointExcluding(sg.x1, sg.y1, sg);
                if (Math.hypot(q[0] - sg.x1, q[1] - sg.y1) < 0.026) {
                    double sdx = q[0] - sg.x1, sdy = q[1] - sg.y1;
                    sg.x1 += sdx; sg.x2 += sdx; sg.y1 += sdy; sg.y2 += sdy;
                } else {
                    q = snapPointExcluding(sg.x2, sg.y2, sg);
                    if (Math.hypot(q[0] - sg.x2, q[1] - sg.y2) < 0.026) {
                        double sdx = q[0] - sg.x2, sdy = q[1] - sg.y2;
                        sg.x1 += sdx; sg.x2 += sdx; sg.y1 += sdy; sg.y2 += sdy;
                    }
                }
            }
            moveLastX = wx; moveLastY = wy;
            invalidate();
        }
    }

    private void handleCanvasUp() {
        if (draggingArc) {
            draggingArc = false;
            if (!dragPathX.isEmpty()) {
                int last = dragPathX.size() - 1;
                dragStartX = dragPathX.get(0);
                dragStartY = dragPathY.get(0);
                dragEndX = dragPathX.get(last);
                dragEndY = dragPathY.get(last);
            }
            double len = Math.hypot(dragEndX - dragStartX, dragEndY - dragStartY);
            if (len > 0.02) {
                pushUndo();
                double[] mid = supportMid(dragStartX, dragStartY, dragEndX, dragEndY);
                level.supports.add(new Level.Support(dragStartX, dragStartY,
                        mid[0], mid[1], dragEndX, dragEndY, arcKind));
                save();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            snapHot = false;
            invalidate();
        } else if (draggingSegment) {
            draggingSegment = false;
            double len = Math.hypot(dragEndX - dragStartX, dragEndY - dragStartY);
            if (len > 0.02) {
                pushUndo();
                level.segments.add(new Level.SegObj(segKind, dragStartX, dragStartY,
                        dragEndX, dragEndY));
                save();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            snapHot = false;
            invalidate();
        } else if (draggingCircle) {
            draggingCircle = false;
            double rad = Math.hypot(dragEndX - dragStartX, dragEndY - dragStartY);
            if (rad > 0.012) {
                pushUndo();
                level.circles.add(new Level.CircleObj(circleKind, dragStartX, dragStartY,
                        Math.min(0.16, rad)));
                save();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            snapHot = false;
            invalidate();
        } else if (draggingCup || draggingFaucet || movingObject != null || endpointObject != null) {
            draggingCup = false; draggingFaucet = false;
            movingObject = null;
            endpointObject = null;
            endpointIndex = -1;
            save();
        }
    }

    /** Snap a new endpoint to nearby endpoints / segments. */
    private double[] snapPoint(double x, double y) {
        double best = 0.036;
        double bx = x, by = y;
        for (Level.Support s : level.supports) {
            if (s.kind != Level.ARC_WALL) continue;
            double[] q = nearestOnSupport(x, y, s);
            double d = Math.hypot(q[0] - x, q[1] - y);
            if (d < best) { best = d; bx = q[0]; by = q[1]; }
        }
        for (Level.SegObj s : level.segments) {
            double[] q = nearestOnSeg(x, y, s);
            double d = Math.hypot(q[0] - x, q[1] - y);
            if (d < best) { best = d; bx = q[0]; by = q[1]; }
        }
        snapHot = best < 0.036;
        snapMarkX = bx; snapMarkY = by;
        return new double[]{bx, by};
    }

    private double[] snapPointExcluding(double x, double y, Object ignore) {
        double best = 0.030, bx = x, by = y;
        for (Level.Support s : level.supports) {
            if (s == ignore || s.kind != Level.ARC_WALL) continue;
            double[] q = nearestOnSupport(x, y, s);
            double d = Math.hypot(q[0] - x, q[1] - y);
            if (d < best) { best = d; bx = q[0]; by = q[1]; }
        }
        for (Level.SegObj s : level.segments) {
            if (s == ignore) continue;
            double[] q = nearestOnSeg(x, y, s);
            double d = Math.hypot(q[0] - x, q[1] - y);
            if (d < best) { best = d; bx = q[0]; by = q[1]; }
        }
        return new double[]{bx, by};
    }

    private double[] supportMid(double x1, double y1, double x2, double y2) {
        double best = -1, mx = (x1 + x2) * 0.5, my = (y1 + y2) * 0.5;
        double abx = x2 - x1, aby = y2 - y1;
        double len = Math.max(1e-6, Math.hypot(abx, aby));
        double nx = -aby / len, ny = abx / len;
        int n = dragPathX.size();
        for (int i = 1; i < n - 1; i++) {
            double px = dragPathX.get(i), py = dragPathY.get(i);
            double d = Math.abs((px - x1) * nx + (py - y1) * ny);
            if (d > best) {
                best = d; mx = px; my = py;
            }
        }
        if (best < 0.004) {
            // A genuinely straight freehand stroke stays straight.
            mx = (x1 + x2) * 0.5;
            my = (y1 + y2) * 0.5;
        }
        return new double[]{mx, my};
    }

    private double[] nearestOnSupport(double x, double y, Level.Support s) {
        double best = Double.MAX_VALUE, bx = s.x1, by = s.y1;
        double prevX = s.px(0), prevY = s.py(0);
        for (int k = 1; k <= 16; k++) {
            double t = k / 16.0;
            double nx = s.px(t), ny = s.py(t);
            double abx = nx - prevX, aby = ny - prevY;
            double len2 = abx * abx + aby * aby;
            double u = len2 > 1e-12 ? ((x - prevX) * abx + (y - prevY) * aby) / len2 : 0;
            u = Math.max(0, Math.min(1, u));
            double qx = prevX + abx * u, qy = prevY + aby * u;
            double d = Math.hypot(x - qx, y - qy);
            if (d < best) { best = d; bx = qx; by = qy; }
            prevX = nx; prevY = ny;
        }
        return new double[]{bx, by};
    }

    private double[] nearestOnSeg(double x, double y, Level.Seg s) {
        double abx = s.x2 - s.x1, aby = s.y2 - s.y1;
        double len2 = abx * abx + aby * aby;
        double t = len2 > 1e-12 ? ((x - s.x1) * abx + (y - s.y1) * aby) / len2 : 0;
        t = Math.max(0, Math.min(1, t));
        return new double[]{s.x1 + abx * t, s.y1 + aby * t};
    }

    private Object findObject(double x, double y) {
        double best = 0.055;
        Object hit = null;
        for (Level.Support s : level.supports) {
            double[] q = nearestOnSupport(x, y, s);
            double d = Math.hypot(x - q[0], y - q[1]);
            if (d < best) { best = d; hit = s; }
        }
        for (Level.SegObj s : level.segments) {
            double d = distSeg(x, y, s);
            if (d < best) { best = d; hit = s; }
        }
        for (Level.CircleObj o : level.circles) {
            double dc = Math.hypot(x - o.cx, y - o.cy);
            double d = dc < o.radius ? 0 : Math.abs(dc - o.radius);
            if (d < best) { best = d; hit = o; }
        }
        for (Level.PoisonFaucet p : level.poisonFaucets) {
            double d = Math.hypot(x - p.x, y - p.y);
            if (d < best) { best = d; hit = p; }
        }
        for (Level.TextObj t : level.texts) {
            double d = Math.hypot(x - t.x, y - t.y);
            if (d < best) { best = d; hit = t; }
        }
        return hit;
    }

    private void chooseTool(int i) {
        if (i == TOOL_SEG) {
            String[] items = {"挡板（固定碰撞）", "火线（蒸发水）"};
            int[] kinds = {Level.SEG_WALL, Level.SEG_FIRE_LINE};
            new AlertDialog.Builder(getContext()).setTitle("线段类型")
                    .setItems(items, (d, w) -> { segKind = kinds[w]; tool = TOOL_SEG; pending = null; invalidate(); })
                    .show();
        } else if (i == TOOL_CIRCLE) {
            String[] items = {"固定圆", "火环（旋转/蒸发水）", "火球（掉落/入杯失败）",
                    "泡泡水球（碰撞释放水）", "毒水球（碰撞释放毒水）", "圆球（动态碰撞）",
                    "毒水净化器（毒水变普通水）", "冰砖（动态/遇火融化）"};
            int[] kinds = {Level.CIRCLE_SOLID, Level.CIRCLE_FIRE_RING, Level.CIRCLE_FIRE_BALL,
                    Level.CIRCLE_BUBBLE, Level.CIRCLE_POISON_BALL, Level.CIRCLE_BALL,
                    Level.CIRCLE_PURIFIER, Level.CIRCLE_ICE};
            new AlertDialog.Builder(getContext()).setTitle("圆类型")
                    .setItems(items, (d, w) -> { circleKind = kinds[w]; tool = TOOL_CIRCLE; pending = null; invalidate(); })
                    .show();
        } else if (i == TOOL_POISON) {
            EditText et = new EditText(getContext());
            et.setText(String.valueOf(poisonCount));
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            new AlertDialog.Builder(getContext()).setTitle("毒水龙头个数")
                    .setView(et)
                    .setPositiveButton("确定", (d, w) -> {
                        try { poisonCount = Math.max(1, Math.min(500, Integer.parseInt(et.getText().toString().trim()))); }
                        catch (Exception ignored) {}
                        tool = TOOL_POISON; pending = null; invalidate();
                    })
                    .setNegativeButton("取消", null).show();
        } else if (i == TOOL_MOTION) {
            tool = TOOL_MOTION;
            pending = null;
            toast("点一个机关，设置运动路径");
            invalidate();
        } else if (i == TOOL_TEXT) {
            EditText et = newEditText(lastText.isEmpty() ? "说明文字" : lastText);
            new AlertDialog.Builder(getContext()).setTitle("添加文字")
                    .setMessage("可直接输入，或用下方按钮快速插入 <> 富文本标签。")
                    .setView(buildTextEditor(et))
                    .setPositiveButton("确定", (d, w) -> {
                        pendingText = et.getText().toString();
                        if (pendingText.trim().isEmpty()) pendingText = "说明文字";
                        lastText = pendingText;
                        getContext().getSharedPreferences("editor_ui", Context.MODE_PRIVATE)
                                .edit().putString("last_text", lastText).apply();
                        tool = TOOL_TEXT;
                        pending = null;
                        invalidate();
                    })
                    .setNegativeButton("取消", null).show();
        } else {
            tool = i;
            pending = null;
            invalidate();
        }
    }

    private EditText newEditText(String text) {
        EditText et = new EditText(getContext());
        et.setSingleLine(false);
        et.setMinLines(3);
        et.setMaxLines(7);
        et.setText(text);
        et.setSelectAllOnFocus(true);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return et;
    }

    private LinearLayout buildTextEditor(EditText et) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(getContext());
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[][] tags = {
                {"<", "<"}, {">", ">"},
                {"color", "<color 255100200255></color>"},
                {"bold", "<bold></bold>"},
                {"close", "</color></bold>"}
        };
        for (String[] tag : tags) {
            android.widget.Button b = new android.widget.Button(getContext());
            b.setText(tag[0]);
            b.setTextSize(10);
            b.setAllCaps(false);
            b.setPadding((int) dp(8), 0, (int) dp(8), 0);
            b.setOnClickListener(v -> insertAtCursor(et, tag[1]));
            row.addView(b);
        }
        hs.addView(row);
        box.addView(hs);
        box.addView(et);
        return box;
    }

    private void insertAtCursor(EditText et, String text) {
        int start = Math.max(0, et.getSelectionStart());
        int end = Math.max(start, et.getSelectionEnd());
        String old = et.getText().toString();
        String ins = text;
        if (text.equals("<color 255100200255></color>")
                || text.equals("<bold></bold>")) {
            String open = text.substring(0, text.indexOf('>') + 1);
            String close = text.substring(text.indexOf('>') + 1);
            ins = open + old.substring(start, end) + close;
        }
        et.getText().replace(start, end, ins);
        int cursor = start + ins.length();
        if (text.equals("<color 255100200255></color>")
                || text.equals("<bold></bold>")) {
            cursor = start + ins.indexOf('>') + 1;
        }
        et.setSelection(Math.min(cursor, et.getText().length()));
    }

    private void editText(Level.TextObj t) {
        EditText et = newEditText(t.text);
        new AlertDialog.Builder(getContext()).setTitle("编辑文字")
                .setMessage("可直接输入，或用下方按钮快速插入 <> 富文本标签。")
                .setView(buildTextEditor(et))
                .setPositiveButton("保存", (d, w) -> {
                    String s = et.getText().toString();
                    if (s.trim().isEmpty()) s = "说明文字";
                    lastText = s;
                    getContext().getSharedPreferences("editor_ui", Context.MODE_PRIVATE)
                            .edit().putString("last_text", lastText).apply();
                    pushUndo();
                    t.text = s;
                    save();
                    invalidate();
                })
                .setNegativeButton("取消", null).show();
    }

    private boolean isDynamicCircle(Level.CircleObj o) {
        return o.kind == Level.CIRCLE_FIRE_BALL || o.kind == Level.CIRCLE_BALL
                || o.kind == Level.CIRCLE_ICE;
    }

    private void configureMotion(Object hit, double cx, double cy) {
        if (hit instanceof Level.CircleObj && isDynamicCircle((Level.CircleObj) hit)) {
            toast("有物理的机关不能设置运动");
            return;
        }
        String[] items = {"停止运动", "绕圈运动（可视化）", "多段路径（A→B→C…）",
                "原地旋转", "闪烁设置"};
        new AlertDialog.Builder(getContext()).setTitle("机关运动")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        setMotionRaw(hit, null);
                        save(); invalidate();
                    } else if (which == 1) {
                        startOrbitEditing(hit, cx, cy);
                    } else if (which == 2) {
                        startPathEditing(hit, cx, cy);
                    } else if (which == 3) {
                        showSpinMotion(hit, cx, cy);
                    } else {
                        showBlinkSettings(hit, cx, cy);
                    }
                }).show();
    }

    private void startOrbitEditing(Object hit, double cx, double cy) {
        pushUndo();
        orbitObject = hit;
        orbitBackup = copyMotion(motionOf(hit));
        if (orbitBackup != null && orbitBackup.type == Level.MOVE_ORBIT) {
            orbitCx = orbitBackup.x1; orbitCy = orbitBackup.y1;
            orbitRadius = orbitBackup.radius; orbitSpeed = orbitBackup.speed;
        } else {
            orbitRadius = 0.08; orbitCx = cx - orbitRadius; orbitCy = cy;
            orbitSpeed = 0.12;
        }
        orbitEditing = true;
        tool = TOOL_MOTION;
        orbitDrag = -1;
        toast("拖动圆心或半径点设置绕圈，完成后设速度");
        invalidate();
    }

    private void handleOrbitTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (btnPathFinish.contains(x, y)) { finishOrbitEditing(); return; }
            if (btnPathCancel.contains(x, y)) { cancelOrbitEditing(); return; }
            if (!playRect.contains(x, y)) return;
            double wx = (x - playRect.left) / ppm, wy = (y - playRect.top) / ppm;
            double dc = Math.hypot(wx - orbitCx, wy - orbitCy);
            if (dc * ppm < dp(20)) { orbitDrag = 0; return; }
            double dr = Math.hypot(wx - (orbitCx + orbitRadius), wy - orbitCy);
            if (dr * ppm < dp(20)) { orbitDrag = 1; return; }
            // Tap on the circle itself adjusts radius.
            if (Math.abs(dc - orbitRadius) * ppm < dp(18)) {
                orbitRadius = clamp((float) dc, 0.015f, 0.20f);
                invalidate();
            }
        } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (orbitDrag < 0) return;
            double wx = clamp((x - playRect.left) / ppm, 0.015, 0.585);
            double wy = clamp((y - playRect.top) / ppm, 0.02, 0.95);
            if (orbitDrag == 0) {
                orbitCx = wx; orbitCy = wy;
            } else {
                orbitRadius = clamp((float) Math.hypot(wx - orbitCx, wy - orbitCy),
                        0.015f, 0.20f);
            }
            invalidate();
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            orbitDrag = -1;
        }
    }

    private void finishOrbitEditing() {
        EditText sp = numberField(orbitSpeed > 0 ? orbitSpeed : 0.12);
        new AlertDialog.Builder(getContext()).setTitle("绕圈速度 m/s")
                .setView(sp)
                .setPositiveButton("完成", (d, w) -> {
                    double speed = clampDouble(parse(sp, 0.12), 0.01, 0.8);
                    Level.Motion m = new Level.Motion(Level.MOVE_ORBIT,
                            orbitCx, orbitCy, 0, 0, orbitRadius, speed);
                    copyBlink(orbitBackup, m);
                    setMotion(orbitObject, m);
                    orbitEditing = false;
                    orbitObject = null;
                    orbitBackup = null;
                    invalidate();
                }).setNegativeButton("返回", null).show();
    }

    private void cancelOrbitEditing() {
        if (orbitObject != null) setMotionRaw(orbitObject, orbitBackup);
        orbitEditing = false;
        orbitObject = null;
        orbitBackup = null;
        save();
        invalidate();
    }

    private void drawOrbitEdit(Canvas c) {
        float cx = X(orbitCx), cy = Y(orbitCy), r = S(orbitRadius);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(0xCCB48CFF);
        stroke.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(6)}, -vTime * 20));
        c.drawCircle(cx, cy, r, stroke);
        stroke.setPathEffect(null);
        c.drawLine(cx, cy, cx + r, cy, stroke);
        fill.setColor(0xCCB48CFF);
        c.drawCircle(cx, cy, dp(7), fill);
        fill.setColor(0xCC7FDFFF);
        c.drawCircle(cx + r, cy, dp(7), fill);
        drawText(c, String.format(Locale.US, "R %.0f cm", orbitRadius * 100),
                cx + r * .55f, cy - dp(12), 9, 0xFFD7F8FF, false);
    }

    private void drawOrbitBar(Canvas c) {
        drawText(c, "绕圈路径 · 拖动圆心/半径点", dp(14), safeTop + dp(52),
                9.0f, 0xFF9CC8D8, false);
        drawSmallButton(c, btnPathCancel, "取消", 0xFF3F2B20);
        drawSmallButton(c, btnPathFinish, "完成", 0xFF1E4B5C);
    }

    private void showBlinkSettings(Object hit, double cx, double cy) {
        Level.Motion old = motionOf(hit);
        boolean initialVisible = old == null || old.blinkInitialVisible;
        double vt = old == null ? 1.0 : old.blinkVisibleTime;
        double ht = old == null ? 1.0 : old.blinkHiddenTime;
        boolean collision = old != null && old.blinkCollideHidden;
        CheckBox visible = new CheckBox(getContext());
        visible.setText("初始显示（取消则初始隐藏）");
        visible.setTextSize(11);
        visible.setChecked(initialVisible);
        CheckBox collide = new CheckBox(getContext());
        collide.setText("隐藏时仍保留碰撞");
        collide.setTextSize(11);
        collide.setChecked(collision);
        EditText v = numberField(vt);
        EditText h = numberField(ht);
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) dp(18), (int) dp(4), (int) dp(18), 0);
        box.addView(visible);
        box.addView(label("显示时长 s"));
        box.addView(v);
        box.addView(label("隐藏时长 s"));
        box.addView(h);
        box.addView(collide);
        new AlertDialog.Builder(getContext()).setTitle("闪烁运动")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    Level.Motion m = old;
                    if (m == null) {
                        m = new Level.Motion(Level.MOVE_NONE, cx, cy, 0, 0, 0, 0);
                    }
                    m.blink = true;
                    m.blinkInitialVisible = visible.isChecked();
                    m.blinkVisibleTime = clampDouble(parse(v, 1.0), 0.05, 60);
                    m.blinkHiddenTime = clampDouble(parse(h, 1.0), 0.05, 60);
                    m.blinkCollideHidden = collide.isChecked();
                    setMotion(hit, m);
                })
                .setNeutralButton("关闭闪烁", (d, w) -> {
                    Level.Motion m = motionOf(hit);
                    if (m != null) m.blink = false;
                    if (m != null && m.type == Level.MOVE_NONE) setMotionRaw(hit, null);
                    save();
                    invalidate();
                })
                .setNegativeButton("取消", null).show();
    }

    private void copyBlink(Level.Motion from, Level.Motion to) {
        if (from == null || to == null) return;
        to.blink = from.blink;
        to.blinkInitialVisible = from.blinkInitialVisible;
        to.blinkVisibleTime = from.blinkVisibleTime;
        to.blinkHiddenTime = from.blinkHiddenTime;
        to.blinkCollideHidden = from.blinkCollideHidden;
    }

    private void startPathEditing(Object hit, double cx, double cy) {
        pushUndo();
        pathObject = hit;
        pathBackup = copyMotion(motionOf(hit));
        Level.Motion m = motionOf(hit);
        if (m == null || m.type != Level.MOVE_PATH) {
            Level.Motion old = m;
            m = new Level.Motion(Level.MOVE_PATH, cx, cy, 0, 0, 0, 0.12);
            copyBlink(old, m);
            m.points.add(new Level.Pt(cx, cy));
            setMotionRaw(hit, m);
        } else if (m.points.isEmpty()) {
            m.points.add(new Level.Pt(cx, cy));
        }
        pathEditing = true;
        tool = TOOL_MOTION;
        pathDrag = -1;
        toast("点空白添加路径点，拖动节点调整，完成后设速度");
        invalidate();
    }

    private void handlePathTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (btnPathFinish.contains(x, y)) { finishPathEditing(); return; }
            if (btnPathUndo.contains(x, y)) {
                Level.Motion m = motionOf(pathObject);
                if (m != null && m.points.size() > 1) {
                    m.points.remove(m.points.size() - 1);
                    invalidate();
                }
                return;
            }
            if (btnPathCancel.contains(x, y)) { cancelPathEditing(); return; }
            if (playRect.contains(x, y)) {
                double wx = clamp((x - playRect.left) / ppm, 0.01, 0.59);
                double wy = clamp((y - playRect.top) / ppm, 0.01, 0.96);
                Level.Motion m = motionOf(pathObject);
                pathDrag = -1;
                if (m != null) {
                    for (int i = 1; i < m.points.size(); i++) {
                        Level.Pt p = m.points.get(i);
                        if (Math.hypot(p.x - wx, p.y - wy) * ppm < dp(18)) {
                            pathDrag = i;
                            break;
                        }
                    }
                }
                if (pathDrag >= 0) {
                    updatePathPoint(wx, wy);
                } else {
                    pathDownX = x; pathDownY = y;
                }
                invalidate();
            }
        } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (pathDrag >= 0) {
                double wx = clamp((x - playRect.left) / ppm, 0.01, 0.59);
                double wy = clamp((y - playRect.top) / ppm, 0.01, 0.96);
                updatePathPoint(wx, wy);
                invalidate();
            }
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            if (pathDrag >= 0) {
                pathDrag = -1;
                invalidate();
            } else if (playRect.contains(x, y)
                    && Math.hypot(x - pathDownX, y - pathDownY) < dp(18)) {
                Level.Motion m = motionOf(pathObject);
                if (m != null && m.points.size() < 40) {
                    m.points.add(new Level.Pt(
                            clamp((x - playRect.left) / ppm, 0.01, 0.59),
                            clamp((y - playRect.top) / ppm, 0.01, 0.96)));
                    invalidate();
                }
            }
        }
    }

    private void updatePathPoint(double wx, double wy) {
        Level.Motion m = motionOf(pathObject);
        if (m == null || pathDrag < 0 || pathDrag >= m.points.size()) return;
        m.points.get(pathDrag).x = wx;
        m.points.get(pathDrag).y = wy;
    }

    private void finishPathEditing() {
        Level.Motion m = motionOf(pathObject);
        if (m == null || m.points.size() < 2) {
            toast("至少需要两个路径点");
            return;
        }
        EditText sp = numberField(m.speed <= 0 ? 0.12 : m.speed);
        EditText spin = numberField(Math.toDegrees(m.spin));
        android.widget.CheckBox loop = new android.widget.CheckBox(getContext());
        loop.setText("循环（最后节点后直线回起点）");
        loop.setTextSize(11);
        loop.setChecked(m.loop);
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) dp(18), (int) dp(4), (int) dp(18), 0);
        box.addView(label("移动速度 m/s"));
        box.addView(sp);
        box.addView(label("旋转速度 °/s"));
        box.addView(spin);
        box.addView(loop);
        new AlertDialog.Builder(getContext()).setTitle("多段路径")
                .setView(box)
                .setPositiveButton("完成", (d, w) -> {
                    m.type = Level.MOVE_PATH;
                    m.loop = loop.isChecked();
                    m.speed = clampDouble(parse(sp, 0.12), 0.01, 0.8);
                    m.spin = Math.toRadians(clampDouble(parse(spin, 0), -720, 720));
                    pathEditing = false;
                    pathObject = null;
                    pathBackup = null;
                    save();
                    invalidate();
                })
                .setNegativeButton("返回", null).show();
    }

    private void cancelPathEditing() {
        if (pathObject != null) setMotionRaw(pathObject, pathBackup);
        pathEditing = false;
        pathObject = null;
        pathBackup = null;
        save();
        invalidate();
    }

    private void setMotionRaw(Object hit, Level.Motion m) {
        if (hit instanceof Level.SegObj) ((Level.SegObj) hit).motion = m;
        else if (hit instanceof Level.CircleObj) ((Level.CircleObj) hit).motion = m;
        else if (hit instanceof Level.Support) ((Level.Support) hit).motion = m;
    }

    private Level.Motion copyMotion(Level.Motion m) {
        if (m == null) return null;
        Level.Motion c = new Level.Motion(m.type, m.x1, m.y1, m.x2, m.y2,
                m.radius, m.speed);
        c.phase = m.phase;
        c.loop = m.loop;
        c.spin = m.spin;
        c.blink = m.blink;
        c.blinkInitialVisible = m.blinkInitialVisible;
        c.blinkVisibleTime = m.blinkVisibleTime;
        c.blinkHiddenTime = m.blinkHiddenTime;
        c.blinkCollideHidden = m.blinkCollideHidden;
        for (Level.Pt p : m.points) c.points.add(new Level.Pt(p.x, p.y));
        return c;
    }

    private void showSpinMotion(Object hit, double cx, double cy) {
        Level.Motion old = motionOf(hit);
        double deg = old != null && old.type == Level.MOVE_SPIN ? Math.toDegrees(old.spin) : 90;
        EditText sp = numberField(deg);
        new AlertDialog.Builder(getContext()).setTitle("原地旋转（°/s，可为负）")
                .setView(sp)
                .setPositiveButton("确定", (d, w) -> {
                    double v = clampDouble(parse(sp, 90), -720, 720);
                    Level.Motion m = new Level.Motion(Level.MOVE_SPIN, cx, cy, 0, 0, 0, 0);
                    copyBlink(old, m);
                    m.spin = Math.toRadians(v);
                    setMotion(hit, m);
                }).setNegativeButton("取消", null).show();
    }

    private void showLinearMotion(Object hit, double cx, double cy) {
        Level.Motion old = motionOf(hit);
        EditText ex = numberField(old != null && old.type == Level.MOVE_LINEAR
                ? old.x2 : cx + 0.12);
        EditText ey = numberField(old != null && old.type == Level.MOVE_LINEAR
                ? old.y2 : cy);
        EditText sp = numberField(old != null ? old.speed : 0.12);
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) dp(18), (int) dp(4), (int) dp(18), 0);
        box.addView(label("终点 X"));
        box.addView(ex);
        box.addView(label("终点 Y"));
        box.addView(ey);
        box.addView(label("速度 m/s"));
        box.addView(sp);
        new AlertDialog.Builder(getContext()).setTitle("来回移动（起点为当前中心）")
                .setView(box)
                .setPositiveButton("确定", (d, w) -> {
                    double x2 = parse(ex, cx + 0.12), y2 = parse(ey, cy);
                    double speed = clampDouble(parse(sp, 0.12), 0.01, 0.8);
                    Level.Motion m = new Level.Motion(Level.MOVE_LINEAR, cx, cy,
                            clampDouble(x2, 0.02, 0.58), clampDouble(y2, 0.02, 0.92),
                            0, speed);
                    setMotion(hit, m);
                }).setNegativeButton("取消", null).show();
    }

    private void showOrbitMotion(Object hit, double cx, double cy) {
        Level.Motion old = motionOf(hit);
        double radius = old != null && old.type == Level.MOVE_ORBIT ? old.radius : 0.08;
        EditText ecx = numberField(old != null && old.type == Level.MOVE_ORBIT
                ? old.x1 : cx - radius);
        EditText ecy = numberField(old != null && old.type == Level.MOVE_ORBIT
                ? old.y1 : cy);
        EditText er = numberField(radius);
        EditText sp = numberField(old != null ? old.speed : 0.12);
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) dp(18), (int) dp(4), (int) dp(18), 0);
        box.addView(label("圆心 X"));
        box.addView(ecx);
        box.addView(label("圆心 Y"));
        box.addView(ecy);
        box.addView(label("半径 m"));
        box.addView(er);
        box.addView(label("速度 m/s"));
        box.addView(sp);
        new AlertDialog.Builder(getContext()).setTitle("绕圈移动")
                .setView(box)
                .setPositiveButton("确定", (d, w) -> {
                    double centerX = clampDouble(parse(ecx, cx - radius), 0.02, 0.58);
                    double centerY = clampDouble(parse(ecy, cy), 0.02, 0.92);
                    double rr = clampDouble(parse(er, radius), 0.015, 0.20);
                    double speed = clampDouble(parse(sp, 0.12), 0.01, 0.8);
                    Level.Motion m = new Level.Motion(Level.MOVE_ORBIT,
                            centerX, centerY, 0, 0, rr, speed);
                    setMotion(hit, m);
                }).setNegativeButton("取消", null).show();
    }

    private EditText numberField(double value) {
        EditText et = new EditText(getContext());
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        et.setKeyListener(android.text.method.DigitsKeyListener
                .getInstance("-0123456789."));
        et.setText(String.format(Locale.US, "%.3f", value));
        et.setSelectAllOnFocus(true);
        return et;
    }

    private android.widget.TextView label(String s) {
        android.widget.TextView tv = new android.widget.TextView(getContext());
        tv.setText(s);
        tv.setTextSize(11);
        tv.setPadding(0, (int) dp(6), 0, 0);
        return tv;
    }

    private double parse(EditText et, double fallback) {
        try { return Double.parseDouble(et.getText().toString().trim()); }
        catch (Exception e) { return fallback; }
    }

    private double clampDouble(double v, double a, double b) {
        return v < a ? a : (v > b ? b : v);
    }

    private Level.Motion motionOf(Object hit) {
        if (hit instanceof Level.SegObj) return ((Level.SegObj) hit).motion;
        if (hit instanceof Level.CircleObj) return ((Level.CircleObj) hit).motion;
        if (hit instanceof Level.Support) return ((Level.Support) hit).motion;
        return null;
    }

    private void setMotion(Object hit, Level.Motion m) {
        if (hit instanceof Level.SegObj) ((Level.SegObj) hit).motion = m;
        else if (hit instanceof Level.CircleObj) ((Level.CircleObj) hit).motion = m;
        else if (hit instanceof Level.Support) ((Level.Support) hit).motion = m;
        save();
        invalidate();
    }

    private void translateMotion(Level.Motion m, double dx, double dy) {
        if (m == null) return;
        m.x1 += dx; m.y1 += dy;
        if (m.type == Level.MOVE_LINEAR) { m.x2 += dx; m.y2 += dy; }
        for (Level.Pt p : m.points) { p.x += dx; p.y += dy; }
    }

    private void drawMotionPaths(Canvas c) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.2f));
        stroke.setColor(0x99FFD36A);
        stroke.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(6)}, -vTime * 18));
        for (Level.SegObj s : level.segments) {
            if (s.motion != null) drawMotionPath(c, s.motion);
        }
        for (Level.CircleObj o : level.circles) {
            if (o.motion != null) drawMotionPath(c, o.motion);
        }
        for (Level.Support s : level.supports) {
            if (s.motion != null) drawMotionPath(c, s.motion);
        }
        stroke.setPathEffect(null);
    }

    private void drawMotionPath(Canvas c, Level.Motion m) {
        if (m.type == Level.MOVE_NONE) return;
        if (m.type == Level.MOVE_LINEAR) {
            c.drawLine(X(m.x1), Y(m.y1), X(m.x2), Y(m.y2), stroke);
            fill.setColor(0xCCFFD36A);
            c.drawCircle(X(m.x1), Y(m.y1), dp(3), fill);
            c.drawCircle(X(m.x2), Y(m.y2), dp(3), fill);
        } else if (m.type == Level.MOVE_PATH) {
            path.reset();
            for (int i = 0; i < m.points.size(); i++) {
                Level.Pt p = m.points.get(i);
                if (i == 0) path.moveTo(X(p.x), Y(p.y));
                else path.lineTo(X(p.x), Y(p.y));
            }
            if (m.loop && m.points.size() > 2) path.close();
            c.drawPath(path, stroke);
            for (int i = 0; i < m.points.size(); i++) {
                Level.Pt p = m.points.get(i);
                fill.setColor(i == pathDrag ? 0xFFFFE08A : 0xCCFFD36A);
                c.drawCircle(X(p.x), Y(p.y), dp(5), fill);
                drawText(c, String.valueOf((char) ('A' + Math.min(25, i))),
                        X(p.x) + dp(6), Y(p.y) - dp(6), 8, 0xFFFFE8B0, false);
            }
        } else if (m.type == Level.MOVE_SPIN) {
            c.drawCircle(X(m.x1), Y(m.y1), S(0.040f), stroke);
            fill.setColor(0xCCFFD36A);
            c.drawCircle(X(m.x1), Y(m.y1), dp(3), fill);
        } else {
            c.drawCircle(X(m.x1), Y(m.y1), S(m.radius), stroke);
            fill.setColor(0xCCFFD36A);
            c.drawCircle(X(m.x1), Y(m.y1), dp(3), fill);
        }
    }

    private void eraseAt(double wx, double wy) {
        for (int i = level.triggers.size() - 1; i >= 0; i--) {
            Level.RotateTrigger t = level.triggers.get(i);
            double cx, cy;
            if (t.targetType == 0 && t.targetIndex < level.segments.size()) {
                Level.SegObj s = level.segments.get(t.targetIndex);
                cx = s.centerX(); cy = s.centerY();
            } else if (t.targetType == 1 && t.targetIndex < level.supports.size()) {
                Level.Support s = level.supports.get(t.targetIndex);
                cx = s.centerX(); cy = s.centerY();
            } else if (t.targetType == 2 && t.targetIndex < level.circles.size()) {
                Level.CircleObj c = level.circles.get(t.targetIndex);
                cx = c.cx; cy = c.cy;
            } else continue;
            if (Math.hypot(wx - (cx + t.offsetX), wy - (cy + t.offsetY)) < 0.025) {
                pushUndo();
                level.triggers.remove(i);
                save(); invalidate();
                return;
            }
        }
        double best = 0.045;
        Object hit = null;
        for (Level.Support s : level.supports) {
            double[] q = nearestOnSupport(wx, wy, s);
            double d = Math.hypot(wx - q[0], wy - q[1]);
            if (d < best) { best = d; hit = s; }
        }
        for (Level.SegObj s : level.segments) if (distSeg(wx, wy, s) < best) { best = distSeg(wx, wy, s); hit = s; }
        for (Level.CircleObj o : level.circles) {
            double d = Math.abs(Math.hypot(wx - o.cx, wy - o.cy) - o.radius);
            if (Math.hypot(wx - o.cx, wy - o.cy) < o.radius) d = 0;
            if (d < best) { best = d; hit = o; }
        }
        for (Level.PoisonFaucet p : level.poisonFaucets) {
            double d = Math.hypot(wx - p.x, wy - p.y);
            if (d < best) { best = d; hit = p; }
        }
        for (Level.TextObj t : level.texts) {
            double d = Math.hypot(wx - t.x, wy - t.y);
            if (d < best) { best = d; hit = t; }
        }
        if (hit == null) { toast("附近没有可删除的机关或文字"); return; }
        pushUndo();
        if (hit instanceof Level.Support) level.supports.remove(hit);
        else if (hit instanceof Level.SegObj) level.segments.remove(hit);
        else if (hit instanceof Level.CircleObj) level.circles.remove(hit);
        else if (hit instanceof Level.PoisonFaucet) level.poisonFaucets.remove(hit);
        else if (hit instanceof Level.TextObj) level.texts.remove(hit);
        save();
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        invalidate();
    }

    private double distSeg(double px, double py, Level.Seg s) {
        double abx = s.x2 - s.x1, aby = s.y2 - s.y1;
        double len2 = abx * abx + aby * aby;
        double t = len2 > 1e-12 ? ((px - s.x1) * abx + (py - s.y1) * aby) / len2 : 0;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (s.x1 + abx * t), py - (s.y1 + aby * t));
    }

    private void pushUndo() {
        try {
            undo.push(LevelStore.toJson(level));
            while (undo.size() > 30) undo.removeLast();
        } catch (Exception ignored) {}
    }

    private void undo() {
        if (undo.isEmpty()) { toast("没有可撤销的操作"); return; }
        try {
            Level L = LevelStore.fromJson(undo.pop());
            level.supports.clear(); level.supports.addAll(L.supports);
            level.segments.clear(); level.segments.addAll(L.segments);
            level.circles.clear(); level.circles.addAll(L.circles);
            level.poisonFaucets.clear(); level.poisonFaucets.addAll(L.poisonFaucets);
            level.texts.clear(); level.texts.addAll(L.texts);
            level.faucetX = L.faucetX; level.cupCx = L.cupCx; level.cupBottomY = L.cupBottomY;
            save();
            invalidate();
        } catch (Exception ignored) {}
    }

    private void confirmNew() {
        new AlertDialog.Builder(getContext()).setTitle("新建关卡")
                .setMessage("清空当前所有机关，恢复默认杯子和水龙头？")
                .setPositiveButton("新建", (d, w) -> {
                    pushUndo();
                    Level L = Level.createDefault();
                    level.supports.clear(); level.supports.addAll(L.supports);
                    level.segments.clear(); level.segments.addAll(L.segments);
                    level.circles.clear(); level.circles.addAll(L.circles);
                    level.poisonFaucets.clear(); level.poisonFaucets.addAll(L.poisonFaucets);
                    level.texts.clear(); level.texts.addAll(L.texts);
                    level.faucetX = L.faucetX; level.cupCx = L.cupCx; level.cupBottomY = L.cupBottomY;
                    save(); invalidate();
                }).setNegativeButton("取消", null).show();
    }

    private void exportFile() {
        save();
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "流水入杯-" + level.name + ".flowlevel");
            ((Activity) getContext()).startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            toast("无法打开保存页面");
        }
    }

    private void importFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            ((Activity) getContext()).startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast("无法打开文件选择器");
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (requestCode == REQ_EXPORT) {
                java.io.OutputStream out = getContext().getContentResolver()
                        .openOutputStream(data.getData());
                out.write(LevelStore.toJson(level).getBytes("UTF-8"));
                out.close();
                toast("关卡文件已导出");
            } else if (requestCode == REQ_IMPORT) {
                java.io.InputStream in = getContext().getContentResolver()
                        .openInputStream(data.getData());
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && bos.size() < 200000) bos.write(buf, 0, n);
                in.close();
                Level L = LevelStore.fromJson(new String(bos.toByteArray(), "UTF-8"));
                applyLevel(L);
                save();
                toast("关卡文件已导入");
            }
        } catch (Exception e) {
            toast("关卡文件读取/保存失败");
        }
    }

    private void applyLevel(Level L) {
        level.name = L.name;
        level.faucetX = L.faucetX;
        level.cupCx = L.cupCx;
        level.cupBottomY = L.cupBottomY;
        level.supports.clear(); level.supports.addAll(L.supports);
        level.segments.clear(); level.segments.addAll(L.segments);
        level.circles.clear(); level.circles.addAll(L.circles);
        level.poisonFaucets.clear(); level.poisonFaucets.addAll(L.poisonFaucets);
        level.texts.clear(); level.texts.addAll(L.texts);
        invalidate();
    }

    private void save() {
        LevelStore.save(getContext(), level);
    }

    private void toast(String s) {
        android.widget.Toast.makeText(getContext(), s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
