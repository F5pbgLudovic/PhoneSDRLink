    function SdrlinkDnr(sr) {
      var N = sr <= 16000 ? 256 : sr <= 32000 ? 512 : 1024, H = N >> 1, K = H + 1, U = 8, BIAS = 2.0, i, b;
      var V = Math.max(4, Math.round(1.6 * sr / H / U));
      var win = new Float32Array(N), inb = new Float32Array(N), ola = new Float32Array(N), outq = new Float32Array(H);
      var re = new Float32Array(N), im = new Float32Array(N), cs = new Float32Array(H), sn = new Float32Array(H), rev = new Uint16Array(N);
      var psm = new Float32Array(K), cmin = new Float32Array(K), mins = new Float32Array(K * U), pmin = new Float32Array(K), spow = new Float32Array(K);
      var fill, opos, frames, sub, slot, floorG = 0.5, beta = 1;
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
        inb.fill(0); ola.fill(0); outq.fill(0); spow.fill(0);
        fill = N - H; opos = 0; frames = 0; sub = 0; slot = 0;
      };
      this.setLevel = function (l) {
        l = Math.max(1, Math.min(10, l | 0 || 1)) - 1;
        floorG = Math.pow(10, -(6 + 3 * l) / 20);
        beta = 1 + 0.22 * l;
      };
      this.frameLen = N;
      var frame = function () {
        var j, k, m;
        for (j = 0; j < N; j++) { re[j] = inb[j] * win[j]; im[j] = 0; }
        fft(false);
        var turn = ++sub >= V;
        for (k = 0; k < K; k++) {
          var P = re[k] * re[k] + im[k] * im[k];
          if (frames === 0) {
            psm[k] = P; cmin[k] = P; pmin[k] = P;
            for (j = 0; j < U; j++) mins[k * U + j] = P;
          } else {
            psm[k] = 0.8 * psm[k] + 0.2 * P;
            if (psm[k] < cmin[k]) cmin[k] = psm[k];
            if (psm[k] < pmin[k]) pmin[k] = psm[k];
            if (turn) {
              mins[k * U + slot] = cmin[k];
              m = cmin[k];
              for (j = 0; j < U; j++) if (mins[k * U + j] < m) m = mins[k * U + j];
              pmin[k] = m;
              cmin[k] = psm[k];
            }
          }
          var nz = pmin[k] * BIAS * beta + 1e-20, post = P / nz;
          var xi = 0.98 * spow[k] / nz + 0.02 * (post > 1 ? post - 1 : 0);
          var g = xi / (1 + xi);
          if (!(g >= floorG)) g = floorG;
          spow[k] = g * g * P;
          re[k] *= g; im[k] *= g;
          if (k > 0 && k < H) { re[N - k] *= g; im[N - k] *= g; }
        }
        if (turn) { sub = 0; slot = (slot + 1) % U; }
        frames++;
        fft(true);
        for (j = 0; j < N; j++) ola[j] += re[j] * win[j] / N;
        for (j = 0; j < H; j++) outq[j] = ola[j];
        if (!(outq[0] === outq[0])) reset();   // jamais de NaN qui resterait pour toujours
        ola.copyWithin(0, H); ola.fill(0, N - H);
        inb.copyWithin(0, H);
      };
      this.process = function (x, y) {
        for (var s = 0; s < x.length; s++) {
          inb[fill++] = x[s];
          if (y) y[s] = outq[opos];
          opos++;
          if (fill === N) { frame(); fill = N - H; opos = 0; }
        }
      };
      reset();
    }
    // DNR amélioré (1.48) : même découpage que le DNR classique, mais estimation du bruit par probabilité de présence de la parole
    // (suit vite un bruit qui change, sans manger la voix), gain « log-spectral » (MMSE-LSA, Ephraim et Malah) qui déforme beaucoup
    // moins la voix que le filtre de Wiener, mélangé au plancher selon la probabilité que la fréquence contienne de la voix (OM-LSA),
    // puis relâchement doux du gain d'une tranche à l'autre. Niveau 1 à 10 : plancher de -8 à -30 dB, sans sur-soustraction.
    function SdrlinkLsa(sr) {
      var N = sr <= 16000 ? 256 : sr <= 32000 ? 512 : 1024, H = N >> 1, K = H + 1, i, b;
      var win = new Float32Array(N), inb = new Float32Array(N), ola = new Float32Array(N), outq = new Float32Array(H);
      var re = new Float32Array(N), im = new Float32Array(N), cs = new Float32Array(H), sn = new Float32Array(H), rev = new Uint16Array(N);
      var lam = new Float32Array(K), pbar = new Float32Array(K), gprev = new Float32Array(K), gam = new Float32Array(K), gs = new Float32Array(K);
      var hop = H / sr, aN = Math.exp(-hop / 0.072), rel = Math.pow(0.5, hop / 0.01), gmin = 0.3, fill, opos, frames;
      var XI1 = 31.62, G1 = XI1 / (1 + XI1), LX = Math.log(1 + XI1), XIMIN = 0.003;
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
      // Intégrale exponentielle E1 (approximations d'Abramowitz et Stegun).
      var e1 = function (v) {
        if (v < 1e-8) v = 1e-8;
        if (v < 1) return -0.57721566 - Math.log(v) + v - v * v / 4 + v * v * v / 18 - v * v * v * v / 96;
        return Math.exp(-v) / v * (v * v + 2.334733 * v + 0.250621) / (v * v + 3.330657 * v + 1.681534);
      };
      var reset = this.reset = function () {
        inb.fill(0); ola.fill(0); outq.fill(0); lam.fill(0); pbar.fill(0); gprev.fill(1); gam.fill(1); gs.fill(0);
        fill = N - H; opos = 0; frames = 0;
      };
      this.setLevel = function (l) {
        l = Math.max(1, Math.min(10, l | 0 || 1)) - 1;
        gmin = Math.pow(10, -(8 + 2.45 * l) / 20);
      };
      this.frameLen = N;
      var frame = function () {
        var j, k;
        for (j = 0; j < N; j++) { re[j] = inb[j] * win[j]; im[j] = 0; }
        fft(false);
        var lg = Math.log(gmin);
        for (k = 0; k < K; k++) {
          var P = re[k] * re[k] + im[k] * im[k];
          if (frames < 8) lam[k] = (lam[k] * frames + P) / (frames + 1);   // départ : moyenne des premières tranches
          else {
            var ph = 1 / (1 + Math.exp(LX - P / (lam[k] + 1e-20) * G1));     // probabilité de parole (Gerkmann et Hendriks)
            pbar[k] = 0.9 * pbar[k] + 0.1 * ph;
            if (pbar[k] > 0.99 && ph > 0.99) ph = 0.99;                    // évite que l'estimation reste bloquée
            lam[k] = aN * lam[k] + (1 - aN) * ((1 - ph) * P + ph * lam[k]);
          }
          var n = lam[k] + 1e-20, g = P / n;
          if (g > 1e4) g = 1e4;
          var xi = 0.98 * gprev[k] * gprev[k] * gam[k] + 0.02 * (g > 1 ? g - 1 : 0);
          if (xi < XIMIN) xi = XIMIN;
          var v = xi * g / (1 + xi), G = xi / (1 + xi) * Math.exp(0.5 * e1(v));
          if (!(G <= 1)) G = 1;
          var p = 1 / (1 + (1 + xi) * Math.exp(-v));                        // présence de voix dans cette fréquence
          var Gf = Math.exp(p * Math.log(G + 1e-20) + (1 - p) * lg);
          if (Gf < gmin) Gf = gmin;
          gprev[k] = G; gam[k] = g;
          if (Gf < rel * gs[k]) Gf = rel * gs[k];                           // relâchement doux : pas de coupure sèche en fin de mot
          gs[k] = Gf;
          re[k] *= Gf; im[k] *= Gf;
          if (k > 0 && k < H) { re[N - k] *= Gf; im[N - k] *= Gf; }
        }
        frames++;
        fft(true);
        for (j = 0; j < N; j++) ola[j] += re[j] * win[j] / N;
        for (j = 0; j < H; j++) outq[j] = ola[j];
        if (!(outq[0] === outq[0])) reset();
        ola.copyWithin(0, H); ola.fill(0, N - H);
        inb.copyWithin(0, H);
      };
      this.process = function (x, y) {
        for (var s = 0; s < x.length; s++) {
          inb[fill++] = x[s];
          if (y) y[s] = outq[opos];
          opos++;
          if (fill === N) { frame(); fill = N - H; opos = 0; }
        }
      };
      reset();
    }

    // DNR IA (1.48) : RNNoise (Jean-Marc Valin, Xiph.Org / Mozilla, licence BSD), réseau de neurones entraîné à séparer la voix du
    // bruit, porté du C (version 0.1.1, poids d'origine) en JavaScript. Il travaille à 48 kHz par tranches de 10 ms : le son du WebSDR
    // est rééchantillonné à l'aller et au retour. Niveau 1 à 10 : plancher de -6 à -34 dB, 10 = sans plancher (RNNoise pur).
    // W = poids du réseau (Int8Array de 87503 octets). Retard ajouté : 20 à 25 ms environ.
    function SdrlinkRnn(sr, W) {
      var FS = 480, WS = 960, FQ = 481, NB = 22, PBS = 1728, CM = 8, i, j;
      var eb = [0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24, 28, 34, 40, 48, 60, 78, 100];
      // --- Poids : couches dans l'ordre du fichier rnn_data.c.
      // Chaque matrice est transposée au chargement (une ligne de poids contiguë par neurone : calcul plus rapide).
      var off = 0, take = function (M, Nn) {
        var a = new Float32Array(M * Nn);
        for (var c = 0; c < M; c++) for (var k = 0; k < Nn; k++) a[k * M + c] = W[off + c * Nn + k];
        off += M * Nn; return a;
      };
      var idW = take(42, 24), idB = take(1, 24), vgW = take(24, 72), vgR = take(24, 72), vgB = take(1, 72), ngW = take(90, 144), ngR = take(48, 144), ngB = take(1, 144);
      var dgW = take(114, 288), dgR = take(96, 288), dgB = take(1, 288), doW = take(96, 22), doB = take(1, 22), voW = take(24, 1), voB = take(1, 1);
      // --- Transformée de Fourier de 960 points (bases 4, 4, 4, 3, 5), tables de rotation précalculées par étage.
      var fac = [4, 4, 4, 3, 5], perm = new Int32Array(WS), stg = [];
      var fr = new Float64Array(WS), fi = new Float64Array(WS), tr = new Float64Array(WS), ti = new Float64Array(WS), ar = new Float64Array(5), ai = new Float64Array(5);
      var src = function (pos, n, s) { if (s === 0) return 0; var p = fac[s - 1], m = n / p; return Math.floor(pos / m) + p * src(pos % m, m, s - 1); };
      for (i = 0; i < WS; i++) perm[i] = src(i, WS, fac.length);
      (function () {
        var Lp = 1;
        for (var s = 0; s < fac.length; s++) {
          var P = fac[s], L = Lp * P, tc = new Float64Array(Lp * P), ts = new Float64Array(Lp * P), dc = new Float64Array(P * P), ds = new Float64Array(P * P), k, q;
          for (k = 0; k < Lp; k++) for (q = 0; q < P; q++) { tc[k * P + q] = Math.cos(2 * Math.PI * q * k / L); ts[k * P + q] = -Math.sin(2 * Math.PI * q * k / L); }
          for (k = 0; k < P; k++) for (q = 0; q < P; q++) { dc[k * P + q] = Math.cos(2 * Math.PI * k * q / P); ds[k * P + q] = -Math.sin(2 * Math.PI * k * q / P); }
          stg.push({ P: P, Lp: Lp, L: L, tc: tc, ts: ts, dc: dc, ds: ds });
          Lp = L;
        }
      })();
      var fft = function () {
        var p, k, b, s, q, C3 = 0.8660254037844386, c1 = 0.30901699437494745, c2 = -0.8090169943352474, s1 = 0.9510565162951535, s2 = 0.5877852522924731;
        for (p = 0; p < WS; p++) { tr[p] = fr[perm[p]]; ti[p] = fi[perm[p]]; }
        for (s = 0; s < stg.length; s++) {
          var S = stg[s], P = S.P, Lp = S.Lp, L = S.L, tc = S.tc, ts = S.ts;
          for (b = 0; b < WS; b += L) for (k = 0; k < Lp; k++) {
            var o = b + k, w = k * P;
            for (q = 0; q < P; q++) {
              var x = o + q * Lp, cq = tc[w + q], sq = ts[w + q];
              ar[q] = tr[x] * cq - ti[x] * sq; ai[q] = tr[x] * sq + ti[x] * cq;
            }
            if (P === 4) {
              var t0r = ar[0] + ar[2], t0i = ai[0] + ai[2], t1r = ar[0] - ar[2], t1i = ai[0] - ai[2], t2r = ar[1] + ar[3], t2i = ai[1] + ai[3], t3r = ar[1] - ar[3], t3i = ai[1] - ai[3];
              tr[o] = t0r + t2r; ti[o] = t0i + t2i;
              tr[o + Lp] = t1r + t3i; ti[o + Lp] = t1i - t3r;
              tr[o + 2 * Lp] = t0r - t2r; ti[o + 2 * Lp] = t0i - t2i;
              tr[o + 3 * Lp] = t1r - t3i; ti[o + 3 * Lp] = t1i + t3r;
            } else if (P === 3) {
              var sr3 = ar[1] + ar[2], si3 = ai[1] + ai[2], dr3 = ar[1] - ar[2], di3 = ai[1] - ai[2], mr = ar[0] - 0.5 * sr3, mi = ai[0] - 0.5 * si3;
              tr[o] = ar[0] + sr3; ti[o] = ai[0] + si3;
              tr[o + Lp] = mr + C3 * di3; ti[o + Lp] = mi - C3 * dr3;
              tr[o + 2 * Lp] = mr - C3 * di3; ti[o + 2 * Lp] = mi + C3 * dr3;
            } else {
              var b1r = ar[1] + ar[4], b1i = ai[1] + ai[4], b2r = ar[2] + ar[3], b2i = ai[2] + ai[3], d1r = ar[1] - ar[4], d1i = ai[1] - ai[4], d2r = ar[2] - ar[3], d2i = ai[2] - ai[3];
              var m1r = ar[0] + c1 * b1r + c2 * b2r, m1i = ai[0] + c1 * b1i + c2 * b2i, m2r = ar[0] + c2 * b1r + c1 * b2r, m2i = ai[0] + c2 * b1i + c1 * b2i;
              var n1r = s1 * d1r + s2 * d2r, n1i = s1 * d1i + s2 * d2i, n2r = s2 * d1r - s1 * d2r, n2i = s2 * d1i - s1 * d2i;
              tr[o] = ar[0] + b1r + b2r; ti[o] = ai[0] + b1i + b2i;
              tr[o + Lp] = m1r + n1i; ti[o + Lp] = m1i - n1r;
              tr[o + 4 * Lp] = m1r - n1i; ti[o + 4 * Lp] = m1i + n1r;
              tr[o + 2 * Lp] = m2r + n2i; ti[o + 2 * Lp] = m2i - n2r;
              tr[o + 3 * Lp] = m2r - n2i; ti[o + 3 * Lp] = m2i + n2r;
            }
          }
        }
        for (p = 0; p < WS; p++) { fr[p] = tr[p]; fi[p] = ti[p]; }
      };
      var hw = new Float64Array(FS), dctT = new Float64Array(NB * NB);
      for (i = 0; i < FS; i++) { var sv = Math.sin(0.5 * Math.PI * (i + 0.5) / FS); hw[i] = Math.sin(0.5 * Math.PI * sv * sv); }
      for (i = 0; i < NB; i++) for (j = 0; j < NB; j++) dctT[i * NB + j] = Math.cos((i + 0.5) * j * Math.PI / NB) * (j === 0 ? Math.sqrt(0.5) : 1);
      var dct = function (out, o, inp) { for (var a = 0; a < NB; a++) { var s = 0; for (var c = 0; c < NB; c++) s += inp[c] * dctT[c * NB + a]; out[o + a] = s * Math.sqrt(2 / 22); } };
      var win = function (x) { for (var a = 0; a < FS; a++) { x[a] *= hw[a]; x[WS - 1 - a] *= hw[a]; } };
      // Les deux transformées directes (signal et signal décalé d'une période) en une seule FFT complexe.
      var fwd2 = function (xa, xb) {
        var a, b2;
        for (a = 0; a < WS; a++) { fr[a] = xa[a]; fi[a] = xb[a]; }
        fft();
        for (a = 0; a < FQ; a++) {
          b2 = a === 0 ? 0 : WS - a;
          var zr = fr[a], zi = fi[a], wr = fr[b2], wi = fi[b2];
          Xr[a] = (zr + wr) / (2 * WS); Xi[a] = (zi - wi) / (2 * WS);
          Pr[a] = (zi + wi) / (2 * WS); Pi[a] = (wr - zr) / (2 * WS);
        }
      };
      var inv = function (out, Xr, Xi) {
        var a;
        for (a = 0; a < FQ; a++) { fr[a] = Xr[a]; fi[a] = -Xi[a]; }
        for (; a < WS; a++) { fr[a] = Xr[WS - a]; fi[a] = Xi[WS - a]; }
        fft();
        for (a = 0; a < WS; a++) out[a] = fr[a];
      };
      var bandE = function (out, Ar, Ai, Br, Bi) {
        var a, c; for (a = 0; a < NB; a++) out[a] = 0;
        for (a = 0; a < NB - 1; a++) {
          var bs = (eb[a + 1] - eb[a]) << 2, b0 = eb[a] << 2;
          for (c = 0; c < bs; c++) { var fr2 = c / bs, t = Ar[b0 + c] * Br[b0 + c] + Ai[b0 + c] * Bi[b0 + c]; out[a] += (1 - fr2) * t; out[a + 1] += fr2 * t; }
        }
        out[0] *= 2; out[NB - 1] *= 2;
      };
      var interp = function (g, bE) {
        var a, c; for (a = 0; a < FQ; a++) g[a] = 0;
        for (a = 0; a < NB - 1; a++) { var bs = (eb[a + 1] - eb[a]) << 2, b0 = eb[a] << 2; for (c = 0; c < bs; c++) { var fr2 = c / bs; g[b0 + c] = (1 - fr2) * bE[a] + fr2 * bE[a + 1]; } }
      };
      // --- Hauteur de la voix (pitch.c et celt_lpc.c, version flottante).
      var xlp = new Float64Array(PBS >> 1), ac = new Float64Array(5), lpc = new Float64Array(4), yyl = new Float64Array(385), xc4 = new Float64Array(147), xc = new Float64Array(294);
      var x4 = new Float64Array(240), y4 = new Float64Array(387), best = [0, 1];
      var down = function (x) {
        var h = PBS >> 1, a, k;
        for (a = 1; a < h; a++) xlp[a] = 0.5 * (0.5 * (x[2 * a - 1] + x[2 * a + 1]) + x[2 * a]);
        xlp[0] = 0.5 * (0.5 * x[1] + x[0]);
        for (k = 0; k <= 4; k++) { var s = 0; for (a = k; a < h; a++) s += xlp[a] * xlp[a - k]; ac[k] = s; }
        ac[0] *= 1.0001;
        for (a = 1; a <= 4; a++) ac[a] -= ac[a] * (0.008 * a) * (0.008 * a);
        var err = ac[0]; lpc.fill(0);
        if (ac[0] !== 0) for (a = 0; a < 4; a++) {
          var rr = 0; for (k = 0; k < a; k++) rr += lpc[k] * ac[a - k];
          rr += ac[a + 1];
          var r = -rr / err; lpc[a] = r;
          for (k = 0; k < (a + 1) >> 1; k++) { var t1 = lpc[k], t2 = lpc[a - 1 - k]; lpc[k] = t1 + r * t2; lpc[a - 1 - k] = t2 + r * t1; }
          err = err - r * r * err;
          if (err < 0.001 * ac[0]) break;
        }
        var tmp = 1; for (a = 0; a < 4; a++) { tmp *= 0.9; lpc[a] *= tmp; }
        var n0 = lpc[0] + 0.8, n1 = lpc[1] + 0.8 * lpc[0], n2 = lpc[2] + 0.8 * lpc[1], n3 = lpc[3] + 0.8 * lpc[2], n4 = 0.8 * lpc[3];
        var m0 = 0, m1 = 0, m2 = 0, m3 = 0, m4 = 0;
        for (a = 0; a < h; a++) {
          var xi0 = xlp[a], s2 = xi0 + n0 * m0 + n1 * m1 + n2 * m2 + n3 * m3 + n4 * m4;
          m4 = m3; m3 = m2; m2 = m1; m1 = m0; m0 = xi0; xlp[a] = s2;
        }
      };
      var findBest = function (xcr, y, yo, len, maxp) {
        var Syy = 1, bn0 = -1, bn1 = -1, bd0 = 0, bd1 = 0, a;
        best[0] = 0; best[1] = 1;
        for (a = 0; a < len; a++) Syy += y[yo + a] * y[yo + a];
        for (a = 0; a < maxp; a++) {
          if (xcr[a] > 0) {
            var x16 = xcr[a] * 1e-12, num = x16 * x16;
            if (num * bd1 > bn1 * Syy) {
              if (num * bd0 > bn0 * Syy) { bn1 = bn0; bd1 = bd0; best[1] = best[0]; bn0 = num; bd0 = Syy; best[0] = a; }
              else { bn1 = num; bd1 = Syy; best[1] = a; }
            }
          }
          Syy += y[yo + a + len] * y[yo + a + len] - y[yo + a] * y[yo + a];
          if (Syy < 1) Syy = 1;
        }
      };
      var search = function () {   // x = xlp + 384, y = xlp, len 960, max_pitch 588
        var a, k, X0 = 384;
        for (a = 0; a < 240; a++) x4[a] = xlp[X0 + 2 * a];
        for (a = 0; a < 387; a++) y4[a] = xlp[2 * a];
        for (k = 0; k < 147; k++) { var s = 0; for (a = 0; a < 240; a++) s += x4[a] * y4[k + a]; xc4[k] = s; }
        findBest(xc4, y4, 0, 240, 147);
        var b0 = best[0], b1 = best[1];
        for (k = 0; k < 294; k++) {
          xc[k] = 0;
          if (Math.abs(k - 2 * b0) > 2 && Math.abs(k - 2 * b1) > 2) continue;
          var s3 = 0; for (a = 0; a < 480; a++) s3 += xlp[X0 + a] * xlp[k + a];
          xc[k] = Math.max(-1, s3);
        }
        findBest(xc, xlp, 0, 480, 294);
        var o = 0;
        if (best[0] > 0 && best[0] < 293) {
          var pa = xc[best[0] - 1], pb = xc[best[0]], pc = xc[best[0] + 1];
          if (pc - pa > 0.7 * (pb - pa)) o = 1; else if (pa - pc > 0.7 * (pb - pc)) o = -1;
        }
        return 2 * best[0] - o;
      };
      var SC = [0, 0, 3, 2, 3, 2, 5, 2, 3, 2, 3, 2, 5, 2, 3, 2];
      var dot = function (o1, o2, n) { var s = 0; for (var a = 0; a < n; a++) s += xlp[o1 + a] * xlp[o2 + a]; return s; };
      var doubling = function (T0in, prevP, prevG) {   // x = xlp, maxperiod 768, minperiod 60, N 960
        var maxp = 384, minp = 30, Nn = 480, xo = 384, k, a;
        var T0 = Math.floor(T0in / 2), pp = Math.floor(prevP / 2);
        if (T0 >= maxp) T0 = maxp - 1;
        var T = T0, xx = dot(xo, xo, Nn), xy = dot(xo, xo - T0, Nn), yy = xx;
        yyl[0] = xx;
        for (a = 1; a <= maxp; a++) { yy = yy + xlp[xo - a] * xlp[xo - a] - xlp[xo + Nn - a] * xlp[xo + Nn - a]; yyl[a] = Math.max(0, yy); }
        yy = yyl[T0];
        var bxy = xy, byy = yy, g0 = xy / Math.sqrt(1 + xx * yy), g = g0;
        for (k = 2; k <= 15; k++) {
          var T1 = Math.floor((2 * T0 + k) / (2 * k)), T1b;
          if (T1 < minp) break;
          if (k === 2) T1b = T1 + T0 > maxp ? T0 : T0 + T1;
          else T1b = Math.floor((2 * SC[k] * T0 + k) / (2 * k));
          var xy1 = 0.5 * (dot(xo, xo - T1, Nn) + dot(xo, xo - T1b, Nn)), yy1 = 0.5 * (yyl[T1] + yyl[T1b]);
          var g1 = xy1 / Math.sqrt(1 + xx * yy1), cont = 0;
          if (Math.abs(T1 - pp) <= 1) cont = prevG;
          else if (Math.abs(T1 - pp) <= 2 && 5 * k * k < T0) cont = 0.5 * prevG;
          var th = Math.max(0.3, 0.7 * g0 - cont);
          if (T1 < 3 * minp) th = Math.max(0.4, 0.85 * g0 - cont);
          else if (T1 < 2 * minp) th = Math.max(0.5, 0.9 * g0 - cont);
          if (g1 > th) { bxy = xy1; byy = yy1; T = T1; g = g1; }
        }
        bxy = Math.max(0, bxy);
        var pg = byy <= bxy ? 1 : bxy / (byy + 1), c0 = dot(xo, xo - (T - 1), Nn), c1 = dot(xo, xo - T, Nn), c2 = dot(xo, xo - (T + 1), Nn), o = 0;
        if (c2 - c0 > 0.7 * (c1 - c0)) o = 1; else if (c0 - c2 > 0.7 * (c1 - c2)) o = -1;
        if (pg > g) pg = g;
        var T0o = 2 * T + o;
        if (T0o < 60) T0o = 60;
        return [T0o, pg];
      };
      // --- Réseau (rnn.c) : couches denses et GRU, poids entiers / 256.
      var sig = function (v) { return 1 / (1 + Math.exp(-v)); };
      var vdot = function (Wt, o, v, n) { var s = 0; for (var c = 0; c < n; c++) s += Wt[o + c] * v[c]; return s; };
      var dense = function (Wt, B, M, Nn, inp, out, act) {
        for (var a = 0; a < Nn; a++) { var s = (B[a] + vdot(Wt, a * M, inp, M)) / 256; out[a] = act === 0 ? Math.tanh(s) : sig(s); }
      };
      var gz = new Float64Array(96), gr = new Float64Array(96), gh = new Float64Array(96), gs = new Float64Array(96);
      var gru = function (Wi, Wr, B, M, Nn, st, inp) {
        var a, s;
        for (a = 0; a < Nn; a++) gz[a] = sig((B[a] + vdot(Wi, a * M, inp, M) + vdot(Wr, a * Nn, st, Nn)) / 256);
        for (a = 0; a < Nn; a++) { gr[a] = sig((B[Nn + a] + vdot(Wi, (Nn + a) * M, inp, M) + vdot(Wr, (Nn + a) * Nn, st, Nn)) / 256); gs[a] = st[a] * gr[a]; }
        for (a = 0; a < Nn; a++) {
          s = (B[2 * Nn + a] + vdot(Wi, (2 * Nn + a) * M, inp, M) + vdot(Wr, (2 * Nn + a) * Nn, gs, Nn)) / 256;
          if (s < 0) s = 0;
          gh[a] = gz[a] * st[a] + (1 - gz[a]) * s;
        }
        for (a = 0; a < Nn; a++) st[a] = gh[a];
      };
      // --- État.
      var amem, smem, pbuf, cmem, memid, lastG, lastP, hp0, hp1, lastg, vst, nst, dst;
      var Xr = new Float64Array(FQ), Xi = new Float64Array(FQ), Pr = new Float64Array(FQ), Pi = new Float64Array(FQ), xw = new Float64Array(WS), pw = new Float64Array(WS);
      var Ex = new Float64Array(NB), Ep = new Float64Array(NB), Exp = new Float64Array(NB), Ly = new Float64Array(NB), tmp = new Float64Array(NB), feat = new Float64Array(42);
      var g = new Float64Array(NB), gf = new Float64Array(FQ), rf = new Float64Array(FQ), dOut = new Float64Array(24), vad = new Float64Array(1);
      var nIn = new Float64Array(90), dIn = new Float64Array(114), rr2 = new Float64Array(NB), xin = new Float64Array(FS), floorG = 0;
      var features = function () {   // compute_frame_features ; renvoie true si silence
        var a, E = 0;
        for (a = 0; a < FS; a++) { xw[a] = amem[a]; xw[FS + a] = xin[a]; amem[a] = xin[a]; }
        win(xw);
        pbuf.copyWithin(0, FS); for (a = 0; a < FS; a++) pbuf[PBS - FS + a] = xin[a];
        down(pbuf);
        var pi = 768 - search(), rd = doubling(pi, lastP, lastG);
        pi = rd[0]; lastP = pi; lastG = rd[1];
        for (a = 0; a < WS; a++) pw[a] = pbuf[PBS - WS - pi + a];
        win(pw); fwd2(xw, pw);
        bandE(Ex, Xr, Xi, Xr, Xi); bandE(Ep, Pr, Pi, Pr, Pi); bandE(Exp, Xr, Xi, Pr, Pi);
        for (a = 0; a < NB; a++) Exp[a] = Exp[a] / Math.sqrt(0.001 + Ex[a] * Ep[a]);
        dct(tmp, 0, Exp);
        for (a = 0; a < 6; a++) feat[34 + a] = tmp[a];
        feat[34] -= 1.3; feat[35] -= 0.9; feat[40] = 0.01 * (pi - 300);
        var logMax = -2, follow = -2;
        for (a = 0; a < NB; a++) {
          Ly[a] = Math.log(1e-2 + Ex[a]) / Math.LN10;
          Ly[a] = Math.max(logMax - 7, Math.max(follow - 1.5, Ly[a]));
          logMax = Math.max(logMax, Ly[a]); follow = Math.max(follow - 1.5, Ly[a]); E += Ex[a];
        }
        if (E < 0.04) { feat.fill(0); return true; }
        dct(feat, 0, Ly);
        feat[0] -= 12; feat[1] -= 4;
        var c0 = memid * NB, c1 = ((memid + CM - 1) % CM) * NB, c2 = ((memid + CM - 2) % CM) * NB;
        for (a = 0; a < NB; a++) cmem[c0 + a] = feat[a];
        memid++;
        for (a = 0; a < 6; a++) {
          feat[a] = cmem[c0 + a] + cmem[c1 + a] + cmem[c2 + a];
          feat[NB + a] = cmem[c0 + a] - cmem[c2 + a];
          feat[NB + 6 + a] = cmem[c0 + a] - 2 * cmem[c1 + a] + cmem[c2 + a];
        }
        if (memid === CM) memid = 0;
        var sv2 = 0;
        for (a = 0; a < CM; a++) {
          var md = 1e15;
          for (var b2 = 0; b2 < CM; b2++) {
            if (b2 === a) continue;
            var d = 0; for (var k = 0; k < NB; k++) { var t = cmem[a * NB + k] - cmem[b2 * NB + k]; d += t * t; }
            if (d < md) md = d;
          }
          sv2 += md;
        }
        feat[41] = sv2 / CM - 2.1;
        return false;
      };
      var pfilter = function () {
        var a;
        for (a = 0; a < NB; a++) {
          var r;
          if (Exp[a] > g[a]) r = 1;
          else r = Exp[a] * Exp[a] * (1 - g[a] * g[a]) / (0.001 + g[a] * g[a] * (1 - Exp[a] * Exp[a]));
          r = Math.sqrt(Math.min(1, Math.max(0, r)));
          rr2[a] = r * Math.sqrt(Ex[a] / (1e-8 + Ep[a]));
        }
        interp(rf, rr2);
        for (a = 0; a < FQ; a++) { Xr[a] += rf[a] * Pr[a]; Xi[a] += rf[a] * Pi[a]; }
        bandE(tmp, Xr, Xi, Xr, Xi);
        for (a = 0; a < NB; a++) rr2[a] = Math.sqrt(Ex[a] / (1e-8 + tmp[a]));
        interp(rf, rr2);
        for (a = 0; a < FQ; a++) { Xr[a] *= rf[a]; Xi[a] *= rf[a]; }
      };
      var y480 = new Float64Array(FS);
      var frame = function () {   // rnnoise_process_frame : xin (échelle 16 bits) → y480
        var a;
        for (a = 0; a < FS; a++) { var xi0 = xin[a], yi = xi0 + hp0; hp0 = hp1 + (-2 * xi0 + 1.99599 * yi); hp1 = xi0 - 0.996 * yi; xin[a] = yi; }
        if (!features()) {
          dense(idW, idB, 42, 24, feat, dOut, 0);
          gru(vgW, vgR, vgB, 24, 24, vst, dOut);
          dense(voW, voB, 24, 1, vst, vad, 1);
          for (a = 0; a < 24; a++) { nIn[a] = dOut[a]; nIn[24 + a] = vst[a]; }
          for (a = 0; a < 42; a++) nIn[48 + a] = feat[a];
          gru(ngW, ngR, ngB, 90, 48, nst, nIn);
          for (a = 0; a < 24; a++) dIn[a] = vst[a];
          for (a = 0; a < 48; a++) dIn[24 + a] = nst[a];
          for (a = 0; a < 42; a++) dIn[72 + a] = feat[a];
          gru(dgW, dgR, dgB, 114, 96, dst, dIn);
          dense(doW, doB, 96, 22, dst, g, 1);
          pfilter();
          for (a = 0; a < NB; a++) { g[a] = Math.max(g[a], 0.6 * lastg[a]); lastg[a] = g[a]; if (g[a] < floorG) g[a] = floorG; }
          interp(gf, g);
          for (a = 0; a < FQ; a++) { Xr[a] *= gf[a]; Xi[a] *= gf[a]; }
        }
        inv(xw, Xr, Xi); win(xw);
        for (a = 0; a < FS; a++) { y480[a] = xw[a] + smem[a]; smem[a] = xw[FS + a]; }
      };
      this.vad = function () { return vad[0]; };
      // --- Rééchantillonneur polyphase (sinus cardinal fenêtré, coefficients précalculés pour chaque décalage).
      var Rs = function (fi0, fo) {
        if (fi0 === fo) return { lat: 0, push: function (x, n, out) { for (var a = 0; a < n; a++) out[a] = x[a]; return n; }, reset: function () { } };
        var gcd = function (u, v) { while (v) { var t = u % v; u = v; v = t; } return u; };
        var fc = 0.9 * Math.min(1, fo / fi0), half = Math.ceil(12 / fc), T = 2 * half, a, j;
        var gg = gcd(Math.round(fi0), Math.round(fo)), P = Math.round(fo) / gg, exact = P <= 1024 && gg > 0;
        if (!exact) P = 1024;
        var stepN = exact ? Math.round(fi0) / gg : 0, stepF = fi0 / fo, co = new Float32Array(P * T);
        for (var ph = 0; ph < P; ph++) {
          var sum = 0;
          for (j = 0; j < T; j++) {
            var u = Math.abs(ph / P + half - 1 - j), h = 0;
            if (u < half) h = fc * (u === 0 ? 1 : Math.sin(Math.PI * fc * u) / (Math.PI * fc * u)) * (0.42 + 0.5 * Math.cos(Math.PI * u / half) + 0.08 * Math.cos(2 * Math.PI * u / half));
            co[ph * T + j] = h; sum += h;
          }
          for (j = 0; j < T; j++) co[ph * T + j] /= sum;
        }
        var buf = new Float32Array(1 << 15), len, ip, fr0;   // position de la prochaine sortie : ip + fr0 / P (exact) ou ip + fr0 (sinon)
        var o = {
          lat: half,
          reset: function () { buf.fill(0); len = half; ip = half; fr0 = 0; },
          push: function (x, n, out) {
            var c = 0, a2;
            if (len + n > buf.length) { var nb = new Float32Array((len + n) * 2); nb.set(buf.subarray(0, len)); buf = nb; }
            for (a2 = 0; a2 < n; a2++) buf[len++] = x[a2];
            while (ip + half < len) {
              var phs = exact ? fr0 : Math.round(fr0 * P), i0 = ip - half + 1, s = 0, base;
              if (phs >= P) { phs -= P; i0++; }
              base = phs * T;
              for (a2 = 0; a2 < T; a2++) s += buf[i0 + a2] * co[base + a2];
              out[c++] = s;
              if (exact) { fr0 += stepN; while (fr0 >= P) { fr0 -= P; ip++; } }
              else { fr0 += stepF; var w = Math.floor(fr0); ip += w; fr0 -= w; }
            }
            var d = ip - half - 1;
            if (d > 0) { buf.copyWithin(0, d, len); len -= d; ip -= d; }
            return c;
          }
        };
        o.reset();
        return o;
      };
      var up = Rs(sr, 48000), dn = Rs(48000, sr), u48 = new Float32Array(1 << 15), dnb = new Float32Array(4096), f48 = new Float32Array(FS);
      var oq = new Float32Array(1 << 15), oqr, oqw, fpos, D = Math.ceil(FS * sr / 48000) + up.lat + Math.ceil(dn.lat * sr / 48000) + 4;
      this.delay = D;
      var reset = this.reset = function () {
        amem = new Float64Array(FS); smem = new Float64Array(FS); pbuf = new Float64Array(PBS); cmem = new Float64Array(CM * NB);
        memid = 0; lastG = 0; lastP = 0; hp0 = 0; hp1 = 0; lastg = new Float64Array(NB); vst = new Float64Array(24); nst = new Float64Array(48); dst = new Float64Array(96);
        up.reset(); dn.reset(); oq.fill(0); oqr = 0; oqw = D; fpos = 0;
      };
      this.setLevel = function (l) {
        l = Math.max(1, Math.min(10, l | 0 || 1));
        floorG = l >= 10 ? 0 : Math.pow(10, -(6 + 3.5 * (l - 1)) / 20);
      };
      this.frameLen = FS;
      this.frame480 = function (x, y) { for (var a = 0; a < FS; a++) xin[a] = x[a]; frame(); for (a = 0; a < FS; a++) y[a] = y480[a]; };   // pour les essais (48 kHz, échelle 16 bits)
      var M = oq.length - 1;
      this.process = function (x, y) {
        var n = x.length, p = 0;
        while (p < n) {
          var m = Math.min(n - p, 1024), c = up.push(x.subarray ? x.subarray(p, p + m) : x, m, u48), a;
          for (a = 0; a < c; a++) {
            f48[fpos++] = u48[a];
            if (fpos === FS) {
              for (var b2 = 0; b2 < FS; b2++) xin[b2] = f48[b2] * 32768;
              frame();
              for (b2 = 0; b2 < FS; b2++) f48[b2] = y480[b2] / 32768;
              var k = dn.push(f48, FS, dnb);
              for (b2 = 0; b2 < k; b2++) { oq[oqw & M] = dnb[b2]; oqw++; }
              fpos = 0;
            }
          }
          for (a = 0; a < m; a++) { if (y) y[p + a] = oqr < oqw ? oq[oqr & M] : 0; if (oqr < oqw) oqr++; }
          p += m;
        }
        if (!(oq[(oqw - 1) & M] === oq[(oqw - 1) & M])) reset();
      };
      this.setLevel(5);
      reset();
    }

    // Les trois DNR derrière un seul objet (1.48) : 0 = classique, 1 = amélioré, 2 = IA. Chaque type garde son niveau ; un type
    // n'est créé qu'à sa première utilisation (le réseau de RNNoise ne se charge donc que si on le choisit).
    function SdrlinkNr(sr, W) {
      var cores = [null, null, null], algo = 0, lv = [3, 5, 5];
      var get = function (a) {
        var c = cores[a];
        if (!c) { c = cores[a] = a === 2 ? new SdrlinkRnn(sr, W) : a === 1 ? new SdrlinkLsa(sr) : new SdrlinkDnr(sr); c.setLevel(lv[a]); }
        return c;
      };
      this.set = function (a, l) {
        a = a | 0;
        if (a < 0 || a > 2) a = 0;
        if (a === 2 && !(W && W.length === 87503)) a = 1;
        lv[a] = Math.max(1, Math.min(10, l | 0 || 1));
        var c = get(a);
        c.setLevel(lv[a]);
        if (a !== algo) { algo = a; c.reset(); }
      };
      this.algo = function () { return algo; };
      this.reset = function () { get(algo).reset(); };
      this.process = function (x, y) { get(algo).process(x, y); };
    }
