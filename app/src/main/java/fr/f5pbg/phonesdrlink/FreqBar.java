package fr.f5pbg.phonesdrlink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

// Barre de réglage : on glisse le doigt vers la droite pour monter la fréquence, vers la gauche pour la baisser
// (le rôle de la molette de la souris dans SDRLink). Chaque PIXELS_PER_STEP pixels = un pas ; plus on glisse vite, plus le pas est
// multiplié, comme l'accélération de la molette.
final class FreqBar extends View {
    interface Listener { void onSteps(int steps, double msPerStep); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float pxPerStep;
    private Listener listener;
    private float lastX, carry, shift;
    private long lastMove;
    private boolean down;
    String hint = "";
    boolean locked;
    Runnable onLong;            // appui de 2 secondes sans bouger
    private boolean longDone;
    private float downX, downY;
    private final Runnable longRun = new Runnable() { public void run() { longDone = true; down = false; carry = 0; invalidate(); if (onLong != null) onLong.run(); } };

    FreqBar(Context c) {
        super(c);
        pxPerStep = Ui.dp(c, 14);
    }

    void setListener(Listener l) { listener = l; }

    // Effet de roue : après un geste rapide, la barre continue sur sa lancée en ralentissant ; un nouvel appui l'arrête.
    private VelocityTracker vt;
    private float flingV;          // vitesse restante, pixels par seconde (signée)
    private long flingT;
    private static final float FLING_MIN = 900 * android.content.res.Resources.getSystem().getDisplayMetrics().density;       // vitesse de lâcher minimale pour lancer la roue
    private static final float FLING_STOP = 40 * android.content.res.Resources.getSystem().getDisplayMetrics().density;       // en dessous, la roue s'arrête
    private static final float FRICTION = 1.6f;               // décroissance exponentielle par seconde

    private final Runnable flingStep = new Runnable() {
        public void run() {
            if (flingV == 0 || locked) { flingV = 0; return; }
            long now = android.os.SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - flingT) / 1000f);
            flingT = now;
            float dx = flingV * dt;
            flingV *= (float) Math.exp(-FRICTION * dt);
            if (Math.abs(flingV) < FLING_STOP) flingV = 0;
            move(dx, now, Math.abs(flingV) > 0 ? flingV : dx / Math.max(dt, 0.001f));
            invalidate();
            if (flingV != 0) postOnAnimation(this);
        }
    };

    void stopFling() { flingV = 0; removeCallbacks(flingStep); }

    // Bague / télécommande à balayages (JX-11…) : chaque balayage donne une lancée à la roue, comme un geste rapide du doigt ; les balayages
    // successifs dans le même sens s'additionnent (la roue accélère), un balayage en sens inverse freine. dir = +1 monte la fréquence.
    boolean flinging() { return flingV != 0; }
    int flingDir() { return flingV > 0 ? 1 : flingV < 0 ? -1 : 0; }

    void kick(int dir) {
        if (locked) return;
        boolean idle = flingV == 0;
        float add = dir * 500 * getResources().getDisplayMetrics().density;
        flingV = Math.max(-4500f, Math.min(4500f, flingV + add));
        if (Math.abs(flingV) < FLING_STOP) flingV = 0;
        flingT = android.os.SystemClock.uptimeMillis();
        if (flingV != 0 && idle) postOnAnimation(flingStep);
    }

    @Override
    protected void onDetachedFromWindow() { stopFling(); super.onDetachedFromWindow(); }

    // Déplacement de dx pixels : convertit en pas ; v = vitesse (pixels par seconde) qui règle l'accélération.
    private void move(float dx, long now, float v) {
        shift = (shift + dx) % (pxPerStep * 4);
        carry += dx;
        int n = (int) (carry / pxPerStep);
        if (n != 0) {
            carry -= n * pxPerStep;
            double ms = Math.abs(v) > 1 ? 1000.0 * pxPerStep / Math.abs(v) : 1000;
            if (listener != null) listener.onSteps(n, ms);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (locked) { if (e.getActionMasked() == MotionEvent.ACTION_DOWN) android.widget.Toast.makeText(getContext(), Lang.T("🔒 Verrouillé : appuyez sur Verrou pour modifier", "🔒 Locked: press Lock to change"), android.widget.Toast.LENGTH_SHORT).show(); return true; }
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopFling();
                longDone = false; downX = e.getX(); downY = e.getY(); removeCallbacks(longRun); postDelayed(longRun, 2000);
                vt.clear();
                vt.addMovement(e);
                getParent().requestDisallowInterceptTouchEvent(true);
                down = true;
                lastX = e.getX();
                lastMove = e.getEventTime();
                carry = 0;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (longDone) return true;
                if (Math.abs(e.getX() - downX) > Ui.dp(getContext(), 12) || Math.abs(e.getY() - downY) > Ui.dp(getContext(), 12)) removeCallbacks(longRun);
                float dx = e.getX() - lastX;
                lastX = e.getX();
                shift = (shift + dx) % (pxPerStep * 4);
                carry += dx;
                int n = (int) (carry / pxPerStep);
                if (n != 0) {
                    carry -= n * pxPerStep;
                    long now = e.getEventTime();
                    double ms = (now - lastMove) / (double) Math.abs(n);
                    lastMove = now;
                    if (listener != null) listener.onSteps(n, ms);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(longRun);
                if (longDone) { longDone = false; if (vt != null) { vt.recycle(); vt = null; } return true; }
                down = false;
                float v = 0;
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    vt.computeCurrentVelocity(1000);
                    v = vt.getXVelocity();
                    // Doigt resté immobile avant de lâcher : pas de lancée.
                    if (e.getEventTime() - lastMove > 120) v = 0;
                }
                vt.recycle();
                vt = null;
                if (Math.abs(v) >= FLING_MIN) {
                    flingV = Math.max(-9000f, Math.min(9000f, v));
                    flingT = android.os.SystemClock.uptimeMillis();
                    postOnAnimation(flingStep);
                }
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = Ui.dp(getContext(), 10);
        p.setStyle(Paint.Style.FILL);
        p.setColor(down ? Ui.accent() : Ui.barBg());
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, p);
        // Graduations qui suivent le doigt
        p.setColor(down ? 0xFF0B2A3A : Ui.barFgTicks());
        p.setStrokeWidth(Ui.dp(getContext(), 1.5f));
        float step = pxPerStep * 4, top = h * 0.2f, bottom = h * 0.45f;
        for (float x = (shift % step) - step; x < w + step; x += step) {
            c.drawLine(x, top, x, bottom, p);
            for (int k = 1; k < 4; k++) c.drawLine(x + k * pxPerStep, top, x + k * pxPerStep, top + (bottom - top) * 0.5f, p);
        }
        p.setColor(down ? 0xFF05202D : Ui.barFgText());
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Ui.dp(getContext(), 13));
        c.drawText(hint, w / 2, h * 0.82f, p);
    }
}
