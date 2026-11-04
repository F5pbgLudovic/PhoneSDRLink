package fr.f5pbg.phonesdrlink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
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

    FreqBar(Context c) {
        super(c);
        pxPerStep = Ui.dp(c, 14);
    }

    void setListener(Listener l) { listener = l; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (locked) { if (e.getActionMasked() == MotionEvent.ACTION_DOWN) android.widget.Toast.makeText(getContext(), Lang.T("🔒 Verrouillé : appuyez sur Verrou pour modifier", "🔒 Locked: press Lock to change"), android.widget.Toast.LENGTH_SHORT).show(); return true; }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                down = true;
                lastX = e.getX();
                lastMove = e.getEventTime();
                carry = 0;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
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
            case MotionEvent.ACTION_CANCEL:
                down = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = Ui.dp(getContext(), 10);
        p.setStyle(Paint.Style.FILL);
        p.setColor(down ? Ui.accent() : Ui.btn());
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, p);
        // Graduations qui suivent le doigt
        p.setColor(down ? 0xFF0B2A3A : Ui.sub());
        p.setStrokeWidth(Ui.dp(getContext(), 1.5f));
        float step = pxPerStep * 4, top = h * 0.2f, bottom = h * 0.45f;
        for (float x = (shift % step) - step; x < w + step; x += step) {
            c.drawLine(x, top, x, bottom, p);
            for (int k = 1; k < 4; k++) c.drawLine(x + k * pxPerStep, top, x + k * pxPerStep, top + (bottom - top) * 0.5f, p);
        }
        p.setColor(down ? 0xFF05202D : Ui.fg());
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Ui.dp(getContext(), 13));
        c.drawText(hint, w / 2, h * 0.82f, p);
    }
}
