package fr.f5pbg.phonesdrlink;

// Recherche d'un signal dans le spectre audio (son à 8000 Hz) pour les modes à large bande (Olivia, Contestia, Hell…) :
// FFT de 2048 points (3,9 Hz par case) toutes les 512 échantillons, puissance lissée sur environ 1,3 s, puis une fenêtre de largeur donnée glisse
// sur le spectre : on garde l'endroit où la puissance dans la fenêtre dépasse le plus celle des deux flancs (le bruit voisin).
final class SpecFind {
    static final int N = 2048, HOP = 512, BINS = N / 2;
    static final double HZ = 8000.0 / N;
    static final double LOW = 250, HIGH = 2850;       // zone du spectre où l'on cherche (et où se prennent les flancs)

    final float[] ring = new float[N];
    final double[] win = new double[N], re = new double[N], im = new double[N];
    final double[] cosT = new double[N / 2], sinT = new double[N / 2];
    final double[] pw = new double[BINS];
    final double[] cum = new double[BINS + 1];
    int rp, sinceHop;
    long total;
    boolean dirty;

    SpecFind() {
        for (int i = 0; i < N; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / N);
        for (int i = 0; i < N / 2; i++) { cosT[i] = Math.cos(2 * Math.PI * i / N); sinT[i] = -Math.sin(2 * Math.PI * i / N); }
    }

    void add(short[] s, int n) {
        for (int i = 0; i < n; i++) {
            ring[rp] = s[i] / 32768f;
            rp = (rp + 1) & (N - 1);
            total++;
            if (++sinceHop >= HOP && total >= N) { sinceHop = 0; hop(); }
        }
    }

    void hop() {
        for (int i = 0; i < N; i++) { re[i] = ring[(rp + i) & (N - 1)] * win[i]; im[i] = 0; }
        fft();
        for (int b = 0; b < BINS; b++) pw[b] = pw[b] * 0.95 + re[b] * re[b] + im[b] * im[b];
        dirty = true;
    }

    void fft() {
        int j = 0;
        for (int i = 0; i < N - 1; i++) {
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
            int m = N >> 1;
            while (m >= 1 && j >= m) { j -= m; m >>= 1; }
            j += m;
        }
        for (int len = 2; len <= N; len <<= 1) {
            int half = len >> 1, step = N / len;
            for (int i = 0; i < N; i += len) {
                for (int k = 0; k < half; k++) {
                    double wr = cosT[k * step], wi = sinT[k * step];
                    int a = i + k, b = a + half;
                    double tr = re[b] * wr - im[b] * wi, ti = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - tr; im[b] = im[a] - ti;
                    re[a] += tr; im[a] += ti;
                }
            }
        }
    }

    void prep() {
        if (!dirty) return;
        cum[0] = 0;
        for (int b = 0; b < BINS; b++) cum[b + 1] = cum[b] + pw[b];
        dirty = false;
    }

    /** Puissance moyenne entre f0 et f1 (Hz), -1 si la plage est vide. */
    double mean(double f0, double f1) {
        int b0 = Math.max(0, Math.min(BINS, (int) Math.round(f0 / HZ))), b1 = Math.max(0, Math.min(BINS, (int) Math.round(f1 / HZ)));
        if (b1 <= b0) return -1;
        return (cum[b1] - cum[b0]) / (b1 - b0);
    }

    /** Rapport entre la puissance de la fenêtre [c-w/2, c+w/2] et celle des flancs (largeur fl de chaque côté, limités à LOW..HIGH) ; 0 si les flancs sont trop courts. */
    double ratio(double c, double w, double fl) {
        prep();
        double in = mean(c - w / 2, c + w / 2);
        if (in <= 0) return 0;
        double l0 = Math.max(LOW, c - w / 2 - fl), l1 = c - w / 2, r0 = c + w / 2, r1 = Math.min(HIGH, c + w / 2 + fl);
        double sum = 0, len = 0;
        if (l1 - l0 > 0) { double m = mean(l0, l1); if (m >= 0) { sum += m * (l1 - l0); len += l1 - l0; } }
        if (r1 - r0 > 0) { double m = mean(r0, r1); if (m >= 0) { sum += m * (r1 - r0); len += r1 - r0; } }
        if (len < Math.max(30, 0.5 * fl)) return 0;
        double fm = sum / len;
        return fm <= 0 ? 0 : in / fm;
    }

    /** Meilleur centre de fenêtre de largeur w (flancs de 0,75 w) : { centre en Hz, rapport } ; centre 0 si rien d'exploitable. */
    double[] best(double w) {
        prep();
        double lo = Math.max(LOW + w / 4, w / 2 + 100), hi = Math.min(HIGH - w / 4, 3300 - w / 2);
        double bc = 0, br = 0;
        for (double c = lo; c <= hi; c += HZ) {
            double r = ratio(c, w, 0.75 * w);
            if (r > br) { br = r; bc = c; }
        }
        return new double[] { bc, br };
    }

    /** Case la plus forte (en Hz) entre f0 et f1 sur le spectre lissé. */
    double peak(double f0, double f1) {
        prep();
        int b0 = Math.max(1, (int) Math.round(f0 / HZ)), b1 = Math.min(BINS - 2, (int) Math.round(f1 / HZ));
        int bb = b0;
        double bp = -1;
        for (int b = b0; b <= b1; b++) {
            double p = pw[b - 1] + pw[b] + pw[b + 1];
            if (p > bp) { bp = p; bb = b; }
        }
        return bb * HZ;
    }
}
