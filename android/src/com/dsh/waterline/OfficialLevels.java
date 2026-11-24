package com.dsh.waterline;

import android.content.Context;

import com.dsh.waterline.game.Level;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** The bundled official levels. */
public final class OfficialLevels {
    public static final int COUNT = 19;
    public static final String[] NAMES = {
            "第一关 · 初见", "第二关 · 三角", "第三关 · 推杯",
            "第四关 · 多线", "第五关 · 清线", "第六关 · 岩浆",
            "第七关", "第八关", "第九关",
            "第十关", "第十一关", "第十二关", "第十三关", "第十四关",
            "第十五关", "第十六关", "第十七关", "第十八关", "第十九关"
    };

    private OfficialLevels() {}

    public static Level load(Context ctx, int index) {
        index = Math.max(0, Math.min(COUNT - 1, index));
        try {
            InputStream in = ctx.getAssets().open("levels/level" + (index + 1) + ".json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            Level L = LevelStore.fromJson(new String(bos.toByteArray(), "UTF-8"));
            L.name = NAMES[index];
            return L;
        } catch (Exception e) {
            Level L = Level.createDefault();
            L.name = NAMES[index];
            return L;
        }
    }
}
