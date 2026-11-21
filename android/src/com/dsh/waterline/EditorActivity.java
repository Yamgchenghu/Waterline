package com.dsh.waterline;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Level editor unlocked from the formal level-select screen. */
public final class EditorActivity extends Activity {
    private EditorView editorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(8, 19, 31));
        goImmersive();
        editorView = new EditorView(this);
        setContentView(editorView);
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
        if (editorView != null) editorView.onResumeEditor();
    }

    @Override protected void onPause() {
        super.onPause();
        if (editorView != null) editorView.onPauseEditor();
    }

    @Override public void onBackPressed() {
        if (editorView == null || !editorView.handleBack()) super.onBackPressed();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (editorView != null) editorView.onActivityResult(requestCode, resultCode, data);
    }
}
