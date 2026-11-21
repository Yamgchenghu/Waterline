package com.dsh.waterline;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.Locale;

public final class OfficialMainActivity extends Activity {
    private SelectView selectView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        migrateProgress();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(8, 19, 31));
        goImmersive();
        selectView = new SelectView(this);
        setContentView(selectView);
    }

    private void goImmersive() {
        Window w = getWindow();
        View d = w.getDecorView();
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goImmersive();
    }

    @Override protected void onResume() {
        super.onResume();
        if (selectView != null) selectView.invalidate();
    }

    private void migrateProgress() {
        SharedPreferences p = getSharedPreferences("official_progress", MODE_PRIVATE);
        if (p.getInt("levels_version", 1) >= 2) return;
        SharedPreferences.Editor e = p.edit();
        // 2.9 was inserted at position 3: old 3..11 become 4..12.
        for (int i = 12; i >= 4; i--) {
            String from = "done_" + (i - 1);
            if (p.contains(from)) e.putBoolean("done_" + i, p.getBoolean(from, false));
        }
        e.remove("done_3");
        e.putInt("levels_version", 2);
        e.apply();
        TutorialStore.migrateForInsertedLevel(this);
    }

    private int countDone(SharedPreferences p) {
        int n = 0;
        for (int i = 1; i <= OfficialLevels.COUNT; i++) {
            if (p.getBoolean("done_" + i, false)) n++;
        }
        return n;
    }

    private final class SelectView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF[] cards = new RectF[OfficialLevels.COUNT];
        private final RectF btnEditor = new RectF();
        private final float density;
        private int safeTop, safeBottom;

        SelectView(Activity a) {
            super(a);
            density = getResources().getDisplayMetrics().density;
            for (int i = 0; i < cards.length; i++) cards[i] = new RectF();
            setBackgroundColor(Color.rgb(6, 16, 25));
            setOnApplyWindowInsetsListener((v, insets) -> {
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null)
                    safeTop = insets.getDisplayCutout().getSafeInsetTop();
                else safeTop = insets.getSystemWindowInsetTop();
                safeBottom = insets.getSystemWindowInsetBottom();
                layoutCards();
                return insets;
            });
        }

        private float dp(float v) { return v * density; }

        private void layoutCards() {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float left = dp(20), right = w - dp(20);
            float top = safeTop + dp(126);
            float bottom = h - safeBottom - dp(34);
            int n = OfficialLevels.COUNT;
            int cols = 3;
            int rows = (n + cols - 1) / cols;
            float gap = dp(9);
            float cw = (right - left - gap * (cols - 1)) / cols;
            float ch = Math.min(dp(112),
                    (bottom - top - gap * (rows - 1)) / rows);
            float total = ch * rows + gap * (rows - 1);
            top = safeTop + dp(112) + (bottom - safeTop - dp(112) - total) * 0.20f;
            btnEditor.set(w - dp(20) - dp(104), safeTop + dp(22),
                    w - dp(20), safeTop + dp(56));
            for (int i = 0; i < cards.length; i++) {
                int col = i % cols, row = i / cols;
                float l = left + col * (cw + gap);
                float t = top + row * (ch + gap);
                cards[i].set(l, t, l + cw, t + ch);
            }
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            layoutCards();
        }

        private void text(Canvas c, String s, float x, float y, float sp, int color, boolean bold,
                          Paint.Align align) {
            txt.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
            txt.setTextSize(dp(sp));
            txt.setColor(color);
            txt.setTextAlign(align);
            c.drawText(s, x, y, txt);
        }

        @Override protected void onDraw(Canvas c) {
            fill.setShader(new LinearGradient(0, 0, 0, getHeight(),
                    new int[]{0xFF041019, 0xFF0B2634, 0xFF06141E}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
            fill.setShader(null);
            text(c, "流水入杯", dp(24), safeTop + dp(52), 30, 0xFFE8F8FF, true, Paint.Align.LEFT);
            text(c, "正式版 · " + OfficialLevels.COUNT + " 个关卡", dp(24), safeTop + dp(78),
                    12, 0xFF8FBECE, false, Paint.Align.LEFT);
            text(c, "拖动画线，把水引进杯子", dp(24), safeTop + dp(98), 10, 0xFF6E9BAE, false,
                    Paint.Align.LEFT);

            SharedPreferences p = getSharedPreferences("official_progress", MODE_PRIVATE);
            boolean editorUnlocked = countDone(p) >= 10;
            if (editorUnlocked) {
                fill.setColor(0xEE17313B);
                c.drawRoundRect(btnEditor, dp(12), dp(12), fill);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(1.2f));
                stroke.setColor(0xAA6CF3CB);
                c.drawRoundRect(btnEditor, dp(12), dp(12), stroke);
                text(c, "关卡编辑器", btnEditor.centerX(), btnEditor.centerY() + dp(4),
                        10.5f, 0xFFBDFFEA, true, Paint.Align.CENTER);
            }
            for (int i = 0; i < cards.length; i++) {
                RectF r = cards[i];
                boolean done = p.getBoolean("done_" + (i + 1), false);
                boolean unlocked = i == 0 || p.getBoolean("done_" + i, false);
                fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        done ? new int[]{0xFF103A36, 0xFF0A2528}
                             : unlocked ? new int[]{0xFF102A37, 0xFF0A1B25}
                                        : new int[]{0xFF0A151D, 0xFF071017},
                        null, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, dp(16), dp(16), fill);
                fill.setShader(null);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(1.2f));
                stroke.setColor(done ? 0x8858E0C0 : unlocked ? 0x5538B9DA : 0x33344F5C);
                c.drawRoundRect(r, dp(16), dp(16), stroke);
                float numY = r.top + Math.min(dp(36), r.height() * 0.42f);
                float nameY = r.top + Math.min(dp(60), r.height() * 0.70f);
                text(c, String.format(Locale.US, "%02d", i + 1), r.left + dp(12),
                        numY, 19, done ? 0xFF9BFFE4
                                : unlocked ? 0xFFD9F4FF : 0xFF526C78, true,
                        Paint.Align.LEFT);
                text(c, OfficialLevels.NAMES[i], r.left + dp(12), nameY,
                        10.5f, unlocked ? 0xFFE1F8FF : 0xFF627A84, true,
                        Paint.Align.LEFT);
                if (done) {
                    text(c, "已通关", r.left + dp(12), r.bottom - dp(9), 8.5f,
                            0xFF6CF3CB, false, Paint.Align.LEFT);
                } else if (unlocked) text(c, "开始 ›", r.left + dp(12), r.bottom - dp(9),
                        8.5f, 0xFF7FB6C8, false, Paint.Align.LEFT);
                else text(c, "未解锁 · 先过第" + i + "关", r.left + dp(12),
                        r.bottom - dp(9), 7.5f, 0xFF536A74, false, Paint.Align.LEFT);
                if (unlocked && TutorialStore.hasOfficial(OfficialMainActivity.this, i + 1)) {
                    RectF tut = new RectF(r.right - dp(50), r.bottom - dp(25),
                            r.right - dp(8), r.bottom - dp(7));
                    fill.setColor(0xDD17313B);
                    c.drawRoundRect(tut, dp(8), dp(8), fill);
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setStrokeWidth(dp(1));
                    stroke.setColor(0xAA6CF3CB);
                    c.drawRoundRect(tut, dp(8), dp(8), stroke);
                    text(c, "教程 ›", tut.centerX(), tut.centerY() + dp(3.5f), 8.5f,
                            0xFFBDFFEA, true, Paint.Align.CENTER);
                }
            }
            text(c, "完成关卡后自动记录进度", getWidth() * .5f,
                    getHeight() - safeBottom - dp(12), 9, 0x667FA8B8, false, Paint.Align.CENTER);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                SharedPreferences p = getSharedPreferences("official_progress", MODE_PRIVATE);
                if (countDone(p) >= 10 && btnEditor.contains(e.getX(), e.getY())) {
                    startActivity(new Intent(OfficialMainActivity.this, EditorActivity.class));
                    return true;
                }
                for (int i = 0; i < cards.length; i++) {
                    if (cards[i].contains(e.getX(), e.getY())) {
                        boolean unlocked = i == 0 || p.getBoolean("done_" + i, false);
                        if (!unlocked) {
                            Toast.makeText(OfficialMainActivity.this,
                                    "先完成第 " + i + " 关", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        RectF r = cards[i];
                        if (TutorialStore.hasOfficial(OfficialMainActivity.this, i + 1)
                                && e.getX() > r.right - dp(54)
                                && e.getY() > r.bottom - dp(30)) {
                            Intent tut = new Intent(OfficialMainActivity.this, PlayActivity.class);
                            tut.putExtra("level_index", i);
                            tut.putExtra("tutorial", true);
                            startActivity(tut);
                            return true;
                        }
                        Intent it = new Intent(OfficialMainActivity.this, PlayActivity.class);
                        it.putExtra("level_index", i);
                        startActivity(it);
                        return true;
                    }
                }
            }
            return true;
        }
    }
}
