package com.example.hourglass;

import android.content.SharedPreferences;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

public class HourglassWallpaperService extends WallpaperService {

    /** Total countdown length in seconds. */
    static final long DEFAULT_SECONDS = 1_258_642_380L;

    @Override
    public Engine onCreateEngine() {
        return new HourglassEngine();
    }

    private class HourglassEngine extends Engine {
        private static final long FRAME_MS = 33;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable drawRunner = this::drawFrame;
        private final Random rnd = new Random();

        private boolean visible;
        private int width, height;
        private long endTimeMs;
        private long totalSeconds = DEFAULT_SECONDS;
        private long fallenStart;
        private long lastSecond = -1;
        private long lastFrameMs;

        // geometry
        private float cx, cy, halfW, halfH, neck;
        private final Path glass = new Path();
        private final Path work = new Path();

        // paints
        private final Paint bgPaint = new Paint();
        private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glassFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glassStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint brownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float[] starX, starY, starR, starPhase;
        private final ArrayList<Particle> grains = new ArrayList<>();
        private final ArrayList<Particle> sparks = new ArrayList<>();
        // brown grains that landed and stay on the pile (oldest merge into the pile shape)
        private final ArrayList<Particle> settled = new ArrayList<>();
        private static final int MAX_SETTLED = 300;

        private class Particle {
            float x, y, vx, vy, life = 1f, r;
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            loadPrefs();

            glassStroke.setStyle(Paint.Style.STROKE);
            highlight.setStyle(Paint.Style.STROKE);
            highlight.setStrokeCap(Paint.Cap.ROUND);
            grainPaint.setColor(0xFF8B5A2B);
            glowPaint.setColor(0xFFFFB347);
            numberPaint.setColor(Color.WHITE);
            numberPaint.setTextAlign(Paint.Align.CENTER);
            numberPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            subPaint.setColor(0xB3E0D8FF);
            subPaint.setTextAlign(Paint.Align.CENTER);
            subPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w;
            height = h;
            setupGeometry();
            drawFrame();
        }

        private void loadPrefs() {
            SharedPreferences prefs = getSharedPreferences("hourglass", MODE_PRIVATE);
            long oldEnd = endTimeMs, oldFallen = fallenStart;
            totalSeconds = prefs.getLong("total", DEFAULT_SECONDS);
            fallenStart = prefs.getLong("fallen", 0);
            endTimeMs = prefs.getLong("end", 0);
            if (endTimeMs == 0) {
                endTimeMs = alignedNow() + totalSeconds * 1000L;
                prefs.edit().putLong("end", endTimeMs).apply();
            }
            lastSecond = -1;
            if (endTimeMs != oldEnd || fallenStart != oldFallen) settled.clear();
        }

        /** Current time rounded down to the whole second, so ticks match the phone clock. */
        private long alignedNow() {
            return (System.currentTimeMillis() / 1000L) * 1000L;
        }

        private void setupGeometry() {
            cx = width / 2f;
            cy = height * 0.47f;
            halfH = Math.min(height * 0.21f, width * 0.5f);
            halfW = halfH * 0.6f;
            neck = halfW * 0.07f;

            glass.reset();
            glass.moveTo(cx - halfW, cy - halfH);
            glass.lineTo(cx + halfW, cy - halfH);
            glass.cubicTo(cx + halfW, cy - halfH * 0.35f, cx + neck, cy - halfH * 0.2f, cx + neck, cy);
            glass.cubicTo(cx + neck, cy + halfH * 0.2f, cx + halfW, cy + halfH * 0.35f, cx + halfW, cy + halfH);
            glass.lineTo(cx - halfW, cy + halfH);
            glass.cubicTo(cx - halfW, cy + halfH * 0.35f, cx - neck, cy + halfH * 0.2f, cx - neck, cy);
            glass.cubicTo(cx - neck, cy - halfH * 0.2f, cx - halfW, cy - halfH * 0.35f, cx - halfW, cy - halfH);
            glass.close();

            bgPaint.setShader(new LinearGradient(0, 0, 0, height,
                    new int[]{0xFF07051A, 0xFF1A1040, 0xFF2B1335}, null, Shader.TileMode.CLAMP));
            glassFill.setShader(new RadialGradient(cx, cy, halfH * 1.2f,
                    0x22B8D8FF, 0x08FFFFFF, Shader.TileMode.CLAMP));
            glassStroke.setStrokeWidth(halfW * 0.025f);
            glassStroke.setShader(new LinearGradient(cx - halfW, 0, cx + halfW, 0,
                    new int[]{0xAAFFFFFF, 0x44AACCFF, 0xAAFFFFFF}, null, Shader.TileMode.CLAMP));
            highlight.setStrokeWidth(halfW * 0.04f);
            highlight.setColor(0x33FFFFFF);
            sandPaint.setShader(new LinearGradient(0, cy - halfH, 0, cy + halfH,
                    new int[]{0xFFFFD98A, 0xFFE8A94F, 0xFFC97B2E}, null, Shader.TileMode.CLAMP));
            brownPaint.setShader(new LinearGradient(0, cy, 0, cy + halfH,
                    new int[]{0xFFA0703F, 0xFF8B5A2B, 0xFF5C3A1A}, null, Shader.TileMode.CLAMP));
            settled.clear();
            glowPaint.setMaskFilter(new BlurMaskFilter(halfW * 0.04f, BlurMaskFilter.Blur.NORMAL));
            framePaint.setShader(new LinearGradient(0, 0, 0, halfW * 0.12f,
                    new int[]{0xFF8A5A2B, 0xFFD9A066, 0xFF6B4220}, null, Shader.TileMode.MIRROR));
            numberPaint.setTextSize(width * 0.1f);
            numberPaint.setShadowLayer(width * 0.02f, 0, 0, 0xAAFFB347);
            subPaint.setTextSize(width * 0.038f);

            int n = 90;
            starX = new float[n];
            starY = new float[n];
            starR = new float[n];
            starPhase = new float[n];
            for (int i = 0; i < n; i++) {
                starX[i] = rnd.nextFloat() * width;
                starY[i] = rnd.nextFloat() * height;
                starR[i] = 0.5f + rnd.nextFloat() * width * 0.003f;
                starPhase[i] = rnd.nextFloat() * 6.28f;
            }
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) {
                loadPrefs();
                lastFrameMs = 0;
                drawFrame();
            } else {
                handler.removeCallbacks(drawRunner);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            visible = false;
            handler.removeCallbacks(drawRunner);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(drawRunner);
        }

        private void drawFrame() {
            handler.removeCallbacks(drawRunner);
            if (width == 0) return;

            long now = SystemClock.uptimeMillis();
            float dt = lastFrameMs == 0 ? 0 : Math.min((now - lastFrameMs) / 1000f, 0.1f);
            lastFrameMs = now;

            long remaining = Math.max(0, (endTimeMs - System.currentTimeMillis()) / 1000);
            if (lastSecond != -1 && remaining != lastSecond && remaining > 0) {
                spawnGrain();
            }
            lastSecond = remaining;

            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockHardwareCanvas();
                if (c != null) render(c, remaining, dt, now);
            } catch (Exception ignored) {
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) { }
                }
            }

            long toSecond = 1000L - (System.currentTimeMillis() % 1000L);
            if (visible) handler.postDelayed(drawRunner, Math.min(FRAME_MS, toSecond));
        }

        private void spawnGrain() {
            Particle p = new Particle();
            p.x = cx + (rnd.nextFloat() - 0.5f) * neck;
            p.y = cy - halfH * 0.05f;
            p.vy = halfH * 0.2f;
            p.r = halfW * (0.012f + rnd.nextFloat() * 0.006f);
            grains.add(p);
        }

        private void render(Canvas c, long remaining, float dt, long now) {
            float t = now / 1000f;
            c.drawRect(0, 0, width, height, bgPaint);

            // twinkling stars
            for (int i = 0; i < starX.length; i++) {
                float a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 1.5f + starPhase[i]));
                starPaint.setColor(Color.argb((int) (a * 200), 255, 255, 255));
                c.drawCircle(starX[i], starY[i], starR[i], starPaint);
            }

            // sand levels in true proportion: gold = remaining, brown = fallen
            double exactLeft = Math.max(0.0, (endTimeMs - System.currentTimeMillis()) / 1000.0);
            double brownExact = fallenStart + Math.max(0.0, totalSeconds - exactLeft);
            double all = exactLeft + brownExact;
            float topFrac = all <= 0 ? 0f : (float) (exactLeft / all);
            float botFrac = all <= 0 ? 0f : (float) (brownExact / all);
            long brownCount = (long) brownExact;

            c.drawPath(glass, glassFill);

            c.save();
            c.clipPath(glass);

            // upper sand with a dimple that wobbles slightly
            float level = cy - topFrac * halfH * 0.85f;
            float dip = halfH * 0.06f * (1f + 0.15f * (float) Math.sin(t * 3f));
            work.reset();
            work.moveTo(cx - halfW, level);
            work.quadTo(cx, level + dip * 2f, cx + halfW, level);
            work.lineTo(cx + halfW, cy);
            work.lineTo(cx - halfW, cy);
            work.close();
            c.drawPath(work, sandPaint);

            // lower pile
            float base = cy + halfH;
            float ph = botFrac * halfH * 0.85f;
            if (ph > 0.5f) {
                work.reset();
                work.moveTo(cx - halfW, base);
                work.lineTo(cx - halfW, base - ph * 0.55f);
                work.quadTo(cx, base - ph * 1.35f, cx + halfW, base - ph * 0.55f);
                work.lineTo(cx + halfW, base);
                work.close();
                c.drawPath(work, brownPaint);
            }
            float pileTop = base - ph * 0.95f;

            // seed visible brown grains for those already fallen
            if (settled.isEmpty() && brownCount > 0) {
                for (long i = 0; i < Math.min(brownCount, MAX_SETTLED); i++) {
                    settle(cx + (float) rnd.nextGaussian() * halfW * 0.25f, base, ph,
                            halfW * (0.012f + rnd.nextFloat() * 0.006f));
                }
            }
            for (Particle s : settled) {
                c.drawCircle(s.x, s.y, s.r, grainPaint);
            }

            // falling grains
            float g = halfH * 2.5f;
            Iterator<Particle> it = grains.iterator();
            while (it.hasNext()) {
                Particle p = it.next();
                p.vy += g * dt;
                p.y += p.vy * dt;
                if (p.y >= pileTop) {
                    splash(p.x, pileTop);
                    settle(p.x + (float) rnd.nextGaussian() * halfW * 0.2f, base, ph, p.r);
                    it.remove();
                    continue;
                }
                c.drawCircle(p.x, p.y, p.r, grainPaint);
            }

            // splash sparks
            Iterator<Particle> si = sparks.iterator();
            while (si.hasNext()) {
                Particle s = si.next();
                s.vy += g * dt;
                s.x += s.vx * dt;
                s.y += s.vy * dt;
                s.life -= dt * 1.6f;
                if (s.life <= 0) { si.remove(); continue; }
                grainPaint.setAlpha((int) (255 * s.life));
                c.drawCircle(s.x, Math.min(s.y, pileTop + s.r), s.r, grainPaint);
            }
            grainPaint.setAlpha(255);

            // pulsing glow at the neck
            glowPaint.setAlpha((int) (90 + 60 * Math.sin(t * 4f)));
            c.drawCircle(cx, cy, neck * 2.5f, glowPaint);
            c.restore();

            // glass outline and reflections
            c.drawPath(glass, glassStroke);
            c.drawLine(cx - halfW * 0.75f, cy - halfH * 0.9f, cx - halfW * 0.7f, cy - halfH * 0.55f, highlight);
            c.drawLine(cx - halfW * 0.75f, cy + halfH * 0.55f, cx - halfW * 0.7f, cy + halfH * 0.9f, highlight);

            // wooden frame
            float capH = halfW * 0.12f;
            float capW = halfW * 1.25f;
            c.drawRoundRect(new RectF(cx - capW, cy - halfH - capH, cx + capW, cy - halfH + capH * 0.2f), capH / 2, capH / 2, framePaint);
            c.drawRoundRect(new RectF(cx - capW, cy + halfH - capH * 0.2f, cx + capW, cy + halfH + capH), capH / 2, capH / 2, framePaint);
            float postW = capH * 0.35f;
            c.drawRoundRect(new RectF(cx - capW + capH * 0.3f, cy - halfH, cx - capW + capH * 0.3f + postW, cy + halfH), postW, postW, framePaint);
            c.drawRoundRect(new RectF(cx + capW - capH * 0.3f - postW, cy - halfH, cx + capW - capH * 0.3f, cy + halfH), postW, postW, framePaint);

            // countdown text
            float ty = height * 0.1f;
            c.drawText(String.format(Locale.US, "%,d", remaining), cx, ty, numberPaint);
            long days = remaining / 86400;
            long years = days / 365;
            long restDays = days % 365;
            String line1 = String.format(Locale.US, "%,d years   %,d days", years, restDays);
            String line2 = new java.text.SimpleDateFormat("HH : mm : ss", Locale.US).format(new java.util.Date());
            String line3 = String.format(Locale.US, "%,d fallen grains", brownCount);
            String line4 = String.format(Locale.US, "%,d days total", days);
            c.drawText(line1, cx, ty + subPaint.getTextSize() * 1.6f, subPaint);
            c.drawText(line2, cx, ty + subPaint.getTextSize() * 3.0f, subPaint);
            c.drawText(line4, cx, ty + subPaint.getTextSize() * 4.4f, subPaint);

            // fallen-grain count under the bottom of the hourglass
            c.drawText(line3, cx, cy + halfH + capH + subPaint.getTextSize() * 1.8f, subPaint);
        }

        /** Puts a brown grain on the pile surface at x; it stays there. */
        private void settle(float x, float base, float ph, float r) {
            x = Math.max(cx - halfW * 0.9f, Math.min(cx + halfW * 0.9f, x));
            float u = (x - (cx - halfW)) / (2f * halfW);
            float edge = base - ph * 0.55f;
            float ctrl = base - ph * 1.35f;
            float surface = (1 - u) * (1 - u) * edge + 2 * u * (1 - u) * ctrl + u * u * edge;
            Particle s = new Particle();
            s.x = x;
            s.y = surface - r * (0.5f + rnd.nextFloat());
            s.r = r;
            settled.add(s);
            if (settled.size() > MAX_SETTLED) settled.remove(0);
        }

        private void splash(float x, float y) {
            int n = 4 + rnd.nextInt(4);
            for (int i = 0; i < n; i++) {
                Particle s = new Particle();
                s.x = x;
                s.y = y;
                s.vx = (rnd.nextFloat() - 0.5f) * halfW * 1.2f;
                s.vy = -rnd.nextFloat() * halfH * 0.6f;
                s.r = halfW * 0.012f;
                sparks.add(s);
            }
        }
    }
}
