package com.dsh.waterline;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.dsh.waterline.game.Level;

public final class PlayActivity extends Activity {
    private PlayView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(8, 19, 31));
        goImmersive();
        int index = getIntent().getIntExtra("level_index", 0);
        boolean custom = getIntent().getBooleanExtra("custom_level", false);
        boolean editor = getIntent().getBooleanExtra("editor_mode", false);
        boolean tutorial = getIntent().getBooleanExtra("tutorial", false);
        if (tutorial) {
            TutorialStore.Tutorial t = custom
                    ? TutorialStore.getCustom(this, getIntent().getStringExtra("tutorial_name"))
                    : TutorialStore.getOfficial(this, index + 1);
            if (t == null) { finish(); return; }
            Level replay = null;
            try { replay = LevelStore.fromJson(t.levelJson); } catch (Exception ignored) {}
            if (replay == null) { finish(); return; }
            gameView = new PlayView(this, replay, index, editor, t);
        } else {
            Level level = custom ? LevelStore.load(this) : OfficialLevels.load(this, index);
            gameView = new PlayView(this, level, index, editor);
        }
        setContentView(gameView);
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

    @Override protected void onResume() { super.onResume(); if (gameView != null) gameView.onResumeGame(); }
    @Override protected void onPause() { super.onPause(); if (gameView != null) gameView.onPauseGame(); }
    @Override public void onBackPressed() { finish(); }
}
