    // Notch automatique (DNF) de PhoneSDRLink 1.30, deux types en plus du notch d'origine (filtres du navigateur, dans bridge.js).
    // Chaque fonction est autonome : elles sont recopiées telles quelles dans un AudioWorklet (toString), sans rien d'extérieur.
    //
    // SdrlinkAnf : annulateur de raies adaptatif (« line enhancer » NLMS). Un filtre prédit le son à partir du son d'il y a ~8 ms ;
    // ce qui est prévisible (sifflements, porteuses, battements stables) est retranché, ce qui ne l'est pas (la parole, le bruit)
    // passe. Il suit les raies qui dérivent et en traite autant qu'il y en a. Niveau 1 à 10 : vitesse d adaptation (0,0015 à 0,06).
    function SdrlinkAnf(sr) {
      var L = Math.max(32, Math.min(256, Math.round(sr * 0.008))), D = Math.max(16, Math.round(sr * 0.008));
      var M = 1; while (M < D + L + 2) M <<= 1;
      var mask = M - 1, buf = new Float32Array(M), w = new Float32Array(L), pos = 0, mu = 0.01;
      var reset = this.reset = function () { buf.fill(0); w.fill(0); pos = 0; };
      this.setLevel = function (l) { l = Math.max(1, Math.min(10, l | 0 || 1)); mu = 0.0015 * Math.pow(1.5, l - 1); };
      this.frameLen = 128;
      this.process = function (x, y) {
        var n = x.length, i, j, v, p, yy, en, e, g, s, bad = false;
        for (i = 0; i < n; i++) {
          s = x[i]; p = (pos - D) & mask; yy = 0; en = 1e-6;
          for (j = 0; j < L; j++) { v = buf[(p - j) & mask]; yy += w[j] * v; en += v * v; }
          e = s - yy;
          if (e !== e || e > 4 || e < -4) { bad = true; e = 0; }
          g = mu * e / en;
          for (j = 0; j < L; j++) w[j] = w[j] * 0.99995 + g * buf[(p - j) & mask];
          buf[pos] = s; pos = (pos + 1) & mask;
          if (y) y[i] = e;
        }
        if (bad) reset();
      };
      this.setLevel(5);
      reset();
    }

    // SdrlinkSpf : notch spectral. Tranches de 43 ms (fenêtre racine de Hann, recouvrement de moitié) ; pour chaque fréquence, le
    // niveau lissé est comparé au niveau médian des fréquences voisines. Une fréquence qui dépasse ses voisines d'un seuil pendant
    // plusieurs tranches de suite est une raie stable : elle est ramenée au niveau de ses voisines (jamais en dessous), avec ses
    // deux ou quatre voisines immédiates. La parole bouge trop pour être retenue. Niveau 1 à 10 : seuil de 15 à 7 dB, durée de
    // présence de 0,35 à 0,08 s, atténuation maximale de 20 à 40 dB, largeur traitée de 3 à 5 fréquences. Retard : une tranche.
    function SdrlinkSpf(sr) {
      var N = sr <= 12500 ? 512 : sr <= 24000 ? 1024 : 2048, H = N >> 1, K = H + 1, i, b;
      var win = new Float32Array(N), inb = new Float32Array(N), ola = new Float32Array(H), outq = new Float32Array(H);
      var re = new Float32Array(N), im = new Float32Array(N), cs = new Float32Array(H), sn = new Float32Array(H), rev = new Uint16Array(N);
      var sdb = new Float32Array(K + 16), cnt = new Uint8Array(K), ex = new Float32Array(K), gn = new Float32Array(K), tg = new Float32Array(K), tmp = new Float32Array(12);
      var fill = H, opos = 0, first = true, thr = 11, hold = 8, floorG = 0.03, wid = 1, frames = sr / H;
      for (i = 0; i < N; i++) win[i] = Math.sqrt(0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
      for (i = 0; i < H; i++) { cs[i] = Math.cos(2 * Math.PI * i / N); sn[i] = -Math.sin(2 * Math.PI * i / N); }
      var bits = Math.round(Math.log(N) / Math.LN2);
      for (i = 0; i < N; i++) { var r = 0; for (b = 0; b < bits; b++) r |= ((i >> b) & 1) << (bits - 1 - b); rev[i] = r; }
      var fft = function (inv) {
        var j, k, t, len, half, step;
        for (j = 0; j < N; j++) { k = rev[j]; if (k > j) { t = re[j]; re[j] = re[k]; re[k] = t; t = im[j]; im[j] = im[k]; im[k] = t; } }
        for (len = 2; len <= N; len <<= 1) {
          half = len >> 1; step = N / len;
          for (j = 0; j < N; j += len) for (k = 0; k < half; k++) {
            var wr = cs[k * step], wi = inv ? -sn[k * step] : sn[k * step], p = j + k, q = p + half;
            var tr = re[q] * wr - im[q] * wi, ti = re[q] * wi + im[q] * wr;
            re[q] = re[p] - tr; im[q] = im[p] - ti; re[p] += tr; im[p] += ti;
          }
        }
      };
      var reset = this.reset = function () {
        inb.fill(0); ola.fill(0); outq.fill(0); cnt.fill(0); ex.fill(0); gn.fill(1);
        fill = H; opos = 0; first = true;
      };
      this.setLevel = function (l) {
        l = Math.max(1, Math.min(10, l | 0 || 1)) - 1;
        thr = 15 - 0.9 * l;
        hold = Math.max(3, Math.round((0.35 - 0.03 * l) * frames));
        floorG = Math.pow(10, -(20 + 2.2 * l) / 20);
        wid = l < 4 ? 1 : 2;
      };
      this.frameLen = N;
      var frame = function () {
        var j, k, d, m, t, c, base, a, v;
        for (j = 0; j < N; j++) { re[j] = inb[j] * win[j]; im[j] = 0; }
        fft(false);
        for (k = 0; k < K; k++) {
          v = 10 * Math.log(re[k] * re[k] + im[k] * im[k] + 1e-10) / Math.LN10;
          sdb[k] = first ? v : sdb[k] + (v - sdb[k]) * 0.35;
        }
        first = false;
        for (k = 0; k < K; k++) tg[k] = 1;
        for (k = 2; k < K - 1; k++) {
          // niveau de fond : médiane des voisines à 3 à 8 fréquences de distance de chaque côté
          m = 0;
          for (d = 3; d <= 8; d++) {
            if (k - d >= 1) { v = sdb[k - d]; for (t = m; t > 0 && tmp[t - 1] > v; t--) tmp[t] = tmp[t - 1]; tmp[t] = v; m++; }
            if (k + d < K) { v = sdb[k + d]; for (t = m; t > 0 && tmp[t - 1] > v; t--) tmp[t] = tmp[t - 1]; tmp[t] = v; m++; }
          }
          if (m < 4) continue;
          base = (m & 1) ? tmp[m >> 1] : 0.5 * (tmp[(m >> 1) - 1] + tmp[m >> 1]);
          ex[k] = sdb[k] - base;
          if (ex[k] > thr) { if (cnt[k] < 250) cnt[k]++; } else cnt[k] = cnt[k] > 2 ? cnt[k] - 2 : 0;
        }
        for (k = 2; k < K - 1; k++) {
          if (cnt[k] < hold) continue;
          a = Math.max(floorG, Math.min(1, Math.pow(10, -ex[k] / 20)));
          if (a < tg[k]) tg[k] = a;
          for (d = 1; d <= wid; d++) {
            c = Math.pow(a, d === 1 ? 0.7 : 0.4);
            if (k - d >= 1 && c < tg[k - d]) tg[k - d] = c;
            if (k + d < K && c < tg[k + d]) tg[k + d] = c;
          }
        }
        for (k = 0; k < K; k++) {
          gn[k] += (tg[k] - gn[k]) * (tg[k] < gn[k] ? 0.7 : 0.1);
          re[k] *= gn[k]; im[k] *= gn[k];
          if (k > 0 && k < H) { re[N - k] *= gn[k]; im[N - k] *= gn[k]; }
        }
        fft(true);
        for (j = 0; j < H; j++) outq[j] = ola[j] + re[j] / N * win[j];
        for (j = 0; j < H; j++) ola[j] = re[H + j] / N * win[H + j];
      };
      this.process = function (x, y) {
        var n = x.length, i2;
        for (i2 = 0; i2 < n; i2++) {
          if (y) y[i2] = outq[opos];
          inb[fill++] = x[i2];
          opos++;
          if (fill === N) {
            frame();
            for (var q = 0; q < H; q++) inb[q] = inb[H + q];
            fill = H; opos = 0;
          }
        }
        if (!(outq[0] === outq[0])) reset();
      };
      this.setLevel(5);
      reset();
    }

    // Les deux types derrière un seul objet : 0 = adaptatif, 1 = spectral. Chaque type garde son niveau ; créé à la première utilisation.
    function SdrlinkNf(sr) {
      var cores = [null, null], algo = 0, lv = [5, 5];
      var get = function (a) {
        var c = cores[a];
        if (!c) { c = cores[a] = a === 1 ? new SdrlinkSpf(sr) : new SdrlinkAnf(sr); c.setLevel(lv[a]); }
        return c;
      };
      this.set = function (a, l) {
        a = a | 0;
        if (a < 0 || a > 1) a = 0;
        lv[a] = Math.max(1, Math.min(10, l | 0 || 1));
        var c = get(a);
        c.setLevel(lv[a]);
        if (a !== algo) { algo = a; c.reset(); }
      };
      this.reset = function () { get(algo).reset(); };
      this.process = function (x, y) { get(algo).process(x, y); };
    }
