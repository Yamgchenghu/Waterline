package com.dsh.waterline;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.ArrayDeque;

/**
 * Tiny SVG-ish text markup for level labels.
 *
 *   <color 081128255>blue</color>        // rrr ggg bbb, decimal
 *   <color 081128255128>blue</color>     // rrr ggg bbb aaa, decimal
 *   <bold>important</bold>
 *
 * Tags may be nested. Malformed tags are rendered literally.
 */
public final class RichText {
    private RichText() {}

    private static final class Style {
        final String type;
        final int start;
        final int color;
        Style(String type, int start, int color) {
            this.type = type; this.start = start; this.color = color;
        }
    }

    public static void draw(Canvas c, String raw, float cx, float cy,
                            float sizePx, int defaultColor, float shadowRadius) {
        SpannableStringBuilder sb = parse(raw, defaultColor);
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        paint.setTextSize(sizePx);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setColor(defaultColor);
        if (shadowRadius > 0) {
            paint.setShadowLayer(shadowRadius, 0, sizePx * 0.08f, 0xCC000000);
        }
        int width = Math.max((int) (sizePx * 8), c.getWidth() - (int) (sizePx * 2));
        if (width < (int) sizePx * 2) width = (int) sizePx * 2;
        StaticLayout layout;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            layout = StaticLayout.Builder.obtain(sb, 0, sb.length(), paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .setLineSpacing(0, 1.25f)
                    .build();
        } else {
            layout = new StaticLayout(sb, paint, width, Layout.Alignment.ALIGN_CENTER,
                    1.25f, 0, false);
        }
        c.save();
        c.translate(cx - width * 0.5f, cy - layout.getHeight() * 0.5f);
        layout.draw(c);
        c.restore();
    }

    public static SpannableStringBuilder parse(String raw, int defaultColor) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (raw == null) return sb;
        ArrayDeque<Style> stack = new ArrayDeque<>();
        int i = 0;
        while (i < raw.length()) {
            int open = raw.indexOf('<', i);
            if (open < 0) {
                sb.append(raw, i, raw.length());
                break;
            }
            if (open > i) sb.append(raw, i, open);
            if (raw.startsWith("</color>", open)) {
                close(sb, stack, "color");
                i = open + 8;
            } else if (raw.startsWith("</bold>", open)) {
                close(sb, stack, "bold");
                i = open + 7;
            } else if (raw.startsWith("<bold>", open)) {
                stack.push(new Style("bold", sb.length(), defaultColor));
                i = open + 6;
            } else if (raw.regionMatches(true, open, "<color", 0, 6)) {
                int end = raw.indexOf('>', open + 6);
                if (end > 0) {
                    String digits = raw.substring(open + 6, end).trim();
                    int color = parseColor(digits);
                    if (color != Integer.MIN_VALUE) {
                        stack.push(new Style("color", sb.length(), color));
                        i = end + 1;
                        continue;
                    }
                }
                sb.append('<');
                i = open + 1;
            } else {
                sb.append('<');
                i = open + 1;
            }
        }
        // close any unclosed tags at end
        while (!stack.isEmpty()) {
            Style s = stack.pop();
            apply(sb, s, sb.length());
        }
        return sb;
    }

    private static void close(SpannableStringBuilder sb, ArrayDeque<Style> stack, String type) {
        // find the nearest matching open tag; discard nested unmatched wrappers
        ArrayDeque<Style> tmp = new ArrayDeque<>();
        Style found = null;
        while (!stack.isEmpty()) {
            Style s = stack.pop();
            if (s.type.equals(type)) { found = s; break; }
            tmp.push(s);
        }
        if (found != null) apply(sb, found, sb.length());
        while (!tmp.isEmpty()) stack.push(tmp.pop());
    }

    private static void apply(SpannableStringBuilder sb, Style s, int end) {
        if (end <= s.start) return;
        if (s.type.equals("bold")) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), s.start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            sb.setSpan(new ForegroundColorSpan(s.color), s.start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static int parseColor(String digits) {
        if (!digits.matches("[0-9]+")) return Integer.MIN_VALUE;
        int len = digits.length();
        if (len != 9 && len != 12) return Integer.MIN_VALUE;
        try {
            int r = clamp(Integer.parseInt(digits.substring(0, 3)));
            int g = clamp(Integer.parseInt(digits.substring(3, 6)));
            int b = clamp(Integer.parseInt(digits.substring(6, 9)));
            int a = len == 12 ? clamp(Integer.parseInt(digits.substring(9, 12))) : 255;
            return Color.argb(a, r, g, b);
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
}
