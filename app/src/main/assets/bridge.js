(function () {
  // Mise en page « ordinateur » et zoom permis : beaucoup de WebSDR (KiwiSDR, OpenWebRX…) s'ajustent à la largeur de l'écran et
  // interdisent le zoom (viewport « device-width » + user-scalable=no) : leurs fenêtres de réglage cachent alors le haut de la page
  // et les boutons + et − n'ont aucun effet. On impose une largeur de 1000 pixels CSS (la page est réduite pour tenir) et le zoom libre.
  if (window.top !== window) return;
  var WANT = 'width=1000, minimum-scale=0.1, maximum-scale=10, user-scalable=yes';
  var apply = function () {
    var head = document.head || document.documentElement;
    if (!head) return;
    var all = document.querySelectorAll('meta[name=viewport]');   // le dernier gagne : on les corrige tous
    if (!all.length) { var n = document.createElement('meta'); n.name = 'viewport'; head.appendChild(n); all = [n]; }
    for (var i = 0; i < all.length; i++) if (all[i].getAttribute('content') !== WANT) all[i].setAttribute('content', WANT);
  };
  apply();
  try { new MutationObserver(apply).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] }); } catch (e) { }
  document.addEventListener('DOMContentLoaded', apply);
})();(function () {
  // La WebView Android n'a pas de reconnaissance vocale : une page qui la demande (UberSDR) faisait tomber le moteur de page.
  try {
    ['SpeechRecognition', 'webkitSpeechRecognition', 'SpeechGrammarList', 'webkitSpeechGrammarList', 'SpeechRecognitionEvent', 'webkitSpeechRecognitionEvent'].forEach(function (n) {
      try { Object.defineProperty(window, n, { value: undefined, configurable: true, writable: true }); } catch (e) { }
    });
  } catch (e) { }
})();
(function () {
  if (window.top !== window || window.__sdrlink) return;
  var L = window.__sdrlink = { last: '', volume: 1, gains: [], ctxs: [] };
  var __post = function (m) { try { PhoneSdr.post(m); } catch (e) { } };

  // Son du WebSDR : tout ce que la page envoie vers la sortie audio passe par une entrée commune, puis
  // par le notch automatique et le réducteur de bruit (s'ils sont actifs), puis par le gain réglable (Mute).
  // Installé avant les scripts de la page, donc valable pour tous les types de WebSDR.
  if (window.AudioNode && window.AudioDestinationNode) {
    var origConnect = AudioNode.prototype.connect, origDisconnect = AudioNode.prototype.disconnect;
    var master = function (ctx) {
      if (!ctx.__sdrlinkIn) {
        var input = ctx.createGain(), g = ctx.createGain();
        g.gain.value = L.volume;
        origConnect.call(input, g);
        origConnect.call(g, ctx.destination);
        ctx.__sdrlinkLinks = [[input, g]];
        ctx.__sdrlinkIn = input;
        ctx.__sdrlinkOut = g;
        L.gains.push(g);
        L.ctxs.push(ctx);
        if (L.notchOn) notch(ctx, true);
        if (L.dnrOn) dnr(ctx, true);
      }
      return ctx.__sdrlinkIn;
    };

    // Notch automatique (comme le DNF des Yaesu) : un analyseur repère les raies stables (porteuses, sifflements)
    // et jusqu'à 4 filtres coupe-bande étroits les suivent. Le chant et la parole bougent trop pour être retenus.
    // Seul le haut-parleur est filtré : le câble SDRLINK reçoit le son brut, pour les logiciels de décodage.
    var NOTCHES = 4, HOLD = 6, RELEASE = 10, ABOVE = 18;
    var notch = function (ctx, on) {
      var n = ctx.__sdrlinkNotch, input = ctx.__sdrlinkIn, out = ctx.__sdrlinkOut;
      if (!input || !ctx.createBiquadFilter || !ctx.createAnalyser) return;
      if (!n) {
        n = ctx.__sdrlinkNotch = { on: false, an: ctx.createAnalyser(), f: [], slots: [] };
        n.an.fftSize = 4096;
        n.an.smoothingTimeConstant = 0;
        origConnect.call(input, n.an);
        for (var i = 0; i < NOTCHES; i++) {
          var b = ctx.createBiquadFilter();
          b.type = 'notch';
          b.frequency.value = ctx.sampleRate * 0.49;   // au repos : tout en haut du spectre, hors de la bande audio utile
          b.Q.value = 30;
          if (i > 0) origConnect.call(n.f[i - 1], b);
          n.f.push(b);
          n.slots.push({ bin: -1, miss: 0 });
        }
        n.chk = ctx.createAnalyser();   // sonde de santé : un filtre numérique qui sort NaN reste muet pour toujours
        n.chk.fftSize = 32;
        origConnect.call(n.f[NOTCHES - 1], n.chk);
      }
      if (on === n.on) return;
      n.on = on;
      if (!on) for (var j = 0; j < NOTCHES; j++) release(ctx, n, j);
      rewire(ctx);
    };
    // Chaîne : entrée → [notch] → [réducteur de bruit] → gain de sortie. Les liens sont refaits à chaque changement.
    var rewire = function (ctx) {
      var input = ctx.__sdrlinkIn, out = ctx.__sdrlinkOut, n = ctx.__sdrlinkNotch, d = ctx.__sdrlinkDnr;
      if (!input) return;
      var links = ctx.__sdrlinkLinks || [];
      for (var i = 0; i < links.length; i++) { try { origDisconnect.call(links[i][0], links[i][1]); } catch (e) { } }
      links = ctx.__sdrlinkLinks = [];
      var prev = input;
      var link = function (a, b) { origConnect.call(a, b); links.push([a, b]); };
      if (n && n.on) { link(prev, n.f[0]); prev = n.f[NOTCHES - 1]; }
      if (d && d.on) { link(prev, d.input); prev = d.output; }
      link(prev, out);
    };

    // Réducteur de bruit (DNR) léger : 16 filtres passe-bande en parallèle, chacun avec son gain. Un analyseur mesure le
    // niveau de chaque bande 20 fois par seconde et suit son plancher de bruit ; une bande qui ne dépasse pas ce plancher
    // de plus de 6 dB est atténuée (jusqu'à 14 dB), une bande qui porte un signal passe intacte. Le travail du signal est fait
    // par les filtres du navigateur ; le script ne règle que 16 gains, ce qui reste très léger pour le téléphone.
    var BANDS_N = 16, DNR_LO = 150, DNR_HI = 4500, DNR_OPEN = 6, DNR_MAX = 14;
    var dnr = function (ctx, on) {
      var d = ctx.__sdrlinkDnr;
      if (!ctx.__sdrlinkIn || !ctx.createBiquadFilter || !ctx.createAnalyser) return;
      if (!d) {
        d = ctx.__sdrlinkDnr = { on: false, input: ctx.createGain(), output: ctx.createGain(), an: ctx.createAnalyser(), g: [], fc: [], floor: [], att: [] };
        d.an.fftSize = 1024;
        d.an.smoothingTimeConstant = 0;
        origConnect.call(d.input, d.an);
        for (var i = 0; i < BANDS_N; i++) {
          var f = DNR_LO * Math.pow(DNR_HI / DNR_LO, i / (BANDS_N - 1));
          var b = ctx.createBiquadFilter(), g = ctx.createGain();
          b.type = 'bandpass';
          b.frequency.value = f;
          b.Q.value = 4;
          origConnect.call(d.input, b);
          origConnect.call(b, g);
          origConnect.call(g, d.output);
          d.g.push(g); d.fc.push(f); d.floor.push(NaN); d.att.push(0);
        }
        d.buf = new Float32Array(d.an.frequencyBinCount);
        d.chk = ctx.createAnalyser();
        d.chk.fftSize = 32;
        origConnect.call(d.output, d.chk);
      }
      if (on === d.on) return;
      d.on = on;
      for (var k = 0; k < BANDS_N; k++) { d.floor[k] = NaN; d.att[k] = 0; d.g[k].gain.value = 1; }
      rewire(ctx);
    };
    var dnrStep = function (ctx, d) {
      var hz = ctx.sampleRate / d.an.fftSize, N = d.an.frequencyBinCount, i, k;
      d.an.getFloatFrequencyData(d.buf);
      for (k = 0; k < BANDS_N; k++) {
        var lo = Math.max(1, Math.floor(d.fc[k] / 1.13 / hz)), hi = Math.min(N - 1, Math.max(lo, Math.ceil(d.fc[k] * 1.13 / hz)));
        var sum = 0;
        for (i = lo; i <= hi; i++) sum += Math.pow(10, Math.max(d.buf[i], -160) / 10);
        var lvl = 10 * Math.log(sum / (hi - lo + 1) + 1e-16) / Math.LN10;
        if (lvl < -130) { d.g[k].gain.setTargetAtTime(1, ctx.currentTime, 0.05); continue; }   // silence : rien à faire
        if (isNaN(d.floor[k]) || lvl < d.floor[k]) d.floor[k] = lvl; else d.floor[k] += 0.025;   // remontée lente : 0,5 dB par seconde
        var excess = lvl - d.floor[k];
        var att = excess >= DNR_OPEN ? 0 : Math.min(DNR_MAX, (DNR_OPEN - excess) * 2.3);
        d.att[k] += (att - d.att[k]) * 0.5;
        d.g[k].gain.setTargetAtTime(Math.pow(10, -d.att[k] / 20), ctx.currentTime, 0.04);
      }
    };
    L.dnrOn = false;
    L.setDnr = function (on) {
      L.dnrOn = !!on;
      for (var i = 0; i < L.ctxs.length; i++) dnr(L.ctxs[i], L.dnrOn);
    };
    setInterval(function () {
      if (!L.dnrOn) return;
      for (var i = 0; i < L.ctxs.length; i++) {
        var d = L.ctxs[i].__sdrlinkDnr;
        if (d && d.on && L.ctxs[i].state === 'running') {
          try {
            if (d.chk && (d.tick = (d.tick || 0) + 1) % 10 === 0 && !healthy(d.chk)) { rebuildDnr(L.ctxs[i]); continue; }
            dnrStep(L.ctxs[i], d);
          } catch (e) { }
        }
      }
    }, 50);
    var release = function (ctx, n, i) {
      n.slots[i].bin = -1;
      n.slots[i].placed = false;   // le prochain placement saute directement à la nouvelle fréquence
      n.f[i].frequency.setTargetAtTime(ctx.sampleRate * 0.49, ctx.currentTime, 0.01);
    };
    var analyse = function (ctx, n) {
      var an = n.an, N = an.frequencyBinCount, hz = ctx.sampleRate / an.fftSize;
      if (!n.cur) { n.cur = new Float32Array(N); n.avg = new Float32Array(N); n.cnt = new Float32Array(N); n.first = true; }
      an.getFloatFrequencyData(n.cur);
      var lo = Math.max(2, Math.ceil(150 / hz)), hi = Math.min(N - 3, Math.floor(4000 / hz)), i, v;
      for (i = lo - 1; i <= hi + 1; i++) {
        v = n.cur[i] > -160 ? n.cur[i] : -160;
        n.avg[i] = n.first ? v : 0.6 * n.avg[i] + 0.4 * v;
      }
      n.first = false;
      var sorted = Array.prototype.slice.call(n.avg, lo, hi + 1).sort(function (a, b) { return a - b; });
      var floor = sorted[sorted.length >> 1];
      if (floor < -140) return;   // silence
      // Raie candidate : pic local nettement au-dessus du bruit ; compteur de présence, toléré à un bin près.
      var cnt = new Float32Array(N), peaks = [];
      for (i = lo; i <= hi; i++) {
        if (n.avg[i] > floor + ABOVE && n.avg[i] >= n.avg[i - 1] && n.avg[i] >= n.avg[i + 1]) {
          cnt[i] = Math.max(n.cnt[i - 1], n.cnt[i], n.cnt[i + 1]) + 1;
          if (cnt[i] >= HOLD) peaks.push(i);
        }
      }
      n.cnt = cnt;
      peaks.sort(function (a, b) { return n.avg[b] - n.avg[a]; });
      var used = [];
      for (var s = 0; s < NOTCHES; s++) {
        var sl = n.slots[s];
        if (sl.bin < 0) continue;
        var best = -1;
        for (var p = 0; p < peaks.length; p++) if (Math.abs(peaks[p] - sl.bin) <= 3 && used.indexOf(peaks[p]) < 0) { best = peaks[p]; break; }
        if (best < 0) { if (++sl.miss > RELEASE) release(ctx, n, s); continue; }
        sl.miss = 0;
        used.push(best);
        place(ctx, n, s, best, hz);
      }
      for (var q = 0; q < peaks.length; q++) {
        if (used.indexOf(peaks[q]) >= 0) continue;
        for (var t = 0; t < NOTCHES; t++) {
          if (n.slots[t].bin >= 0) continue;
          used.push(peaks[q]);
          place(ctx, n, t, peaks[q], hz);
          break;
        }
      }
    };
    // Fréquence affinée par interpolation entre bins voisins ; largeur d'environ 40 Hz.
    var place = function (ctx, n, s, bin, hz) {
      var a = n.avg[bin - 1], b = n.avg[bin], c = n.avg[bin + 1], d = a - 2 * b + c;
      var f = (bin + (d !== 0 ? 0.5 * (a - c) / d : 0)) * hz;
      if (!isFinite(f) || f < 60 || f > ctx.sampleRate * 0.45) return;
      n.slots[s].bin = bin;
      n.slots[s].miss = 0;
      n.f[s].frequency.setTargetAtTime(f, ctx.currentTime, n.slots[s].placed ? 0.05 : 0.005);
      n.f[s].Q.value = Math.max(5, f / 40);
      n.slots[s].placed = true;
    };
    // Contrôle de santé : la sonde reçoit des NaN quand un filtre est devenu fou ; on le reconstruit alors à neuf.
    var healthy = function (an) {
      var b = new Float32Array(an.fftSize);
      an.getFloatTimeDomainData(b);
      for (var i = 0; i < b.length; i++) if (b[i] !== b[i]) return false;
      return true;
    };
    var rebuildNotch = function (ctx) {
      var old = ctx.__sdrlinkNotch;
      if (!old) return;
      try { origDisconnect.call(ctx.__sdrlinkIn, old.an); } catch (e) { }
      ctx.__sdrlinkNotch = null;
      notch(ctx, true);
    };
    var rebuildDnr = function (ctx) {
      ctx.__sdrlinkDnr = null;
      dnr(ctx, true);
    };    L.notchOn = false;
    L.setNotch = function (on) {
      L.notchOn = !!on;
      for (var i = 0; i < L.ctxs.length; i++) notch(L.ctxs[i], L.notchOn);
    };
    setInterval(function () {
      if (!L.notchOn) return;
      for (var i = 0; i < L.ctxs.length; i++) {
        var n = L.ctxs[i].__sdrlinkNotch;
        if (n && n.on && L.ctxs[i].state === 'running') {
          try {
            if (n.chk && (n.tick = (n.tick || 0) + 1) % 5 === 0 && !healthy(n.chk)) { rebuildNotch(L.ctxs[i]); continue; }
            analyse(L.ctxs[i], n);
          } catch (e) { }
        }
      }
    }, 100);
    AudioNode.prototype.connect = function (dest) {
      if (dest instanceof AudioDestinationNode) arguments[0] = master(dest.context);
      return origConnect.apply(this, arguments);
    };
    AudioNode.prototype.disconnect = function (dest) {
      if (dest instanceof AudioDestinationNode && dest.context.__sdrlinkIn) arguments[0] = dest.context.__sdrlinkIn;
      return origDisconnect.apply(this, arguments);
    };
  }
  // v : 0 à 1. Agit aussi sur les éléments <audio>/<video> éventuels.
  L.setVolume = function (v) {
    L.volume = v;
    for (var i = 0; i < L.gains.length; i++) L.gains[i].gain.value = v;
    var media = document.querySelectorAll('audio,video');
    for (var j = 0; j < media.length; j++) media[j].volume = Math.min(1, v);
  };

  // Volume propre à la page, suivant le volume du téléphone : f de 0 à 1 (1 = curseur au maximum).
  L.pageVol = 1;
  L.setPageVolume = function (f) {
    L.pageVol = f;
    var k = detect();
    var setv = function (el, v) {
      try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, v); } catch (e) { el.value = v; }
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    };
    var slide = function (el) { if (el) { var lo = parseFloat(el.min), hi = parseFloat(el.max); setv(el, String(lo + f * (hi - lo))); } };
    try {
      if (k === 'websdr') slide(document.getElementById('volumecontrol2'));
      else if (k === 'owrx' || k === 'owrx+') slide(document.getElementById('openwebrx-panel-volume'));
      else if (k === 'kiwi') slide(document.querySelector('input.id-input-volume'));
      else if (k === 'phantom') slide(L.phantomVolume());
      else if (k === 'ubersdr') UberSDR.command('volume', { volume: f }).catch(function () { });
    } catch (e) { }
    var media = document.querySelectorAll('audio,video');
    for (var j = 0; j < media.length; j++) media[j].volume = 1;
  };  // PhantomSDR : le curseur de volume est le premier « glass-slider » de 0 à 100.
  L.phantomVolume = function () {
    var s = document.querySelectorAll('input.glass-slider');
    for (var i = 0; i < s.length; i++) if (s[i].min === '0' && s[i].max === '100') return s[i];
    return null;
  };
  // Enregistrement MP3 : le son brut de la page (avant volume, muet, notch et réducteur de bruit) est copié par un
  // ScriptProcessor vers un encodeur lamejs (dans un Worker si la page le permet), qui renvoie le MP3 par morceaux en base64.
  L.rec = null;
  L.recStart = function (lameSrc, kbps) {
    var ctx = null, i;
    for (i = 0; i < L.ctxs.length; i++) if (L.ctxs[i].state === 'running') { ctx = L.ctxs[i]; break; }
    if (!ctx || !ctx.createScriptProcessor) { __post('rec|err|noaudio'); return; }
    var r = { ctx: ctx, worker: null, enc: null, sp: null };
    var send = function (bytes) {
      var s = '';
      for (var j = 0; j < bytes.length; j += 8192) s += String.fromCharCode.apply(null, bytes.subarray(j, j + 8192));
      __post('mp3|' + btoa(s));
    };
    r.send = send;
    try {
      var src = lameSrc + ';\nvar enc=null;self.onmessage=function(e){var d=e.data;if(d.init){enc=new lamejs.Mp3Encoder(1,d.rate,d.kbps);return;}' +
        'if(d.stop){self.postMessage({mp3:enc.flush(),end:true});return;}var o=enc.encodeBuffer(new Int16Array(d));if(o.length)self.postMessage({mp3:o});};';
      r.worker = new Worker(URL.createObjectURL(new Blob([src], { type: 'application/javascript' })));
      r.worker.onmessage = function (e) {
        if (e.data.mp3 && e.data.mp3.length) send(new Uint8Array(e.data.mp3.buffer));
        if (e.data.end) { __post('mp3end'); r.worker.terminate(); }
      };
      r.worker.onerror = function () { __post('rec|err|worker'); };
      r.worker.postMessage({ init: true, rate: ctx.sampleRate, kbps: kbps });
    } catch (e) {
      r.worker = null;
      try { (0, eval)(lameSrc); r.enc = new lamejs.Mp3Encoder(1, ctx.sampleRate, kbps); } catch (e2) { __post('rec|err|encoder'); return; }
    }
    var sp = r.sp = ctx.createScriptProcessor(4096, 1, 1), silent = ctx.createGain();
    sp.channelCount = 1;
    sp.channelCountMode = 'explicit';
    silent.gain.value = 0;
    origConnect.call(ctx.__sdrlinkIn, sp);
    origConnect.call(sp, silent);
    origConnect.call(silent, ctx.destination);   // un ScriptProcessor ne tourne que s'il est relié à la sortie
    r.silent = silent;
    sp.onaudioprocess = function (e) {
      var d = e.inputBuffer.getChannelData(0), p = new Int16Array(d.length), j;
      for (j = 0; j < d.length; j++) p[j] = Math.max(-1, Math.min(1, d[j])) * 32767 | 0;
      if (r.worker) r.worker.postMessage(p.buffer, [p.buffer]);
      else { var o = r.enc.encodeBuffer(p); if (o.length) send(new Uint8Array(o.buffer)); }
    };
    L.rec = r;
    __post('rec|ok|' + ctx.sampleRate);
  };
  L.recStop = function () {
    var r = L.rec;
    if (!r) { __post('mp3end'); return; }
    L.rec = null;
    try { r.sp.onaudioprocess = null; origDisconnect.call(r.sp); origDisconnect.call(ctx_of(r), r.sp); } catch (e) { }
    try { origDisconnect.call(r.silent); } catch (e) { }
    if (r.worker) r.worker.postMessage({ stop: true });
    else { var f = r.enc.flush(); if (f.length) r.send(new Uint8Array(f.buffer)); __post('mp3end'); }
  };
  function ctx_of(r) { return r.ctx.__sdrlinkIn; }
  // Zoom du spectre : la largeur de la cascade affichée est ramenée à une échelle fixe sur l'écran (kHzParMm : 1 = 3 kHz de
  // spectre pour 3 mm de téléphone). Les WebSDR zooment par paliers (puissances de 2 ou presque) : on prend le palier le plus
  // proche. webPx = largeur de la WebView en pixels, xdpi = pixels par pouce de l'écran.
  L.zoomTimer = 0;
  L.spectrumZoom = function (webPx, xdpi, kHzPerMm) {
    clearTimeout(L.zoomTimer);
    var k = detect();
    var cssPerPx = (window.visualViewport && visualViewport.width) ? visualViewport.width / webPx : 1;   // pixels CSS par pixel écran
    var mmPerCss = 25.4 / xdpi / cssPerPx;
    var wf = function () {   // la plus large cascade visible, en pixels CSS
      var best = null, c = document.querySelectorAll('canvas'), i, w;
      for (i = 0; i < c.length; i++) {
        w = c[i].getBoundingClientRect().width;
        if (w > 100 && (!best || w > best.w)) best = { el: c[i], w: w };
      }
      return best;
    };
    var log2 = function (x) { return Math.log(x) / Math.LN2; };
    try {
      if (k === 'websdr') {
        // khzperpixel : kHz par pixel de la cascade ; wfset(0) zoome d'un palier, wfset(1) dézoome.
        var cv = document.querySelector('canvas[id^=wf]') || (wf() && wf().el), tries = 0, last = -1;
        var step = function () {
          var w = cv.getBoundingClientRect().width, desired = w * mmPerCss * kHzPerMm, span = khzperpixel * cv.width, r = log2(span / desired);
          if (Math.abs(r) < 0.5 || tries++ > 16 || khzperpixel === last) return;
          last = khzperpixel;
          wfset(r > 0 ? 0 : 1);
          L.zoomTimer = setTimeout(step, 900);
        };
        step();
      } else if (k === 'kiwi') {
        var w1 = wf(); if (!w1) return;
        var desired1 = w1.w * mmPerCss * kHzPerMm, bw = (typeof bandwidth === 'number' ? bandwidth : 30e6) / 1000;
        var z = Math.max(0, Math.min(typeof zoom_levels_max === 'number' ? zoom_levels_max : 14, Math.round(log2(bw / desired1))));
        ext_set_zoom(ext_zoom.ABS, z);
      } else if (k === 'owrx' || k === 'owrx+') {
        if (typeof zoom_levels === 'undefined' || typeof zoom_set !== 'function') return;
        var w2 = typeof waterfallWidth === 'function' ? waterfallWidth() : (wf() ? wf().w : 0);
        var desired2 = w2 * mmPerCss * kHzPerMm, bw2 = bandwidth / 1000, bestL = 0, bestD = 1e9, l;
        for (l = 0; l < zoom_levels.length; l++) {
          var d2 = Math.abs(log2(bw2 / zoom_levels[l] / desired2));
          if (d2 < bestD) { bestD = d2; bestL = l; }
        }
        zoom_set(bestL);
      } else if (k === 'ubersdr') {
        var w3 = wf(), desired3 = (w3 ? w3.w : visualViewport.width) * mmPerCss * kHzPerMm;
        UberSDR.command('spectrum', { span: Math.round(desired3 * 1000) }).catch(function () { });
      } else {
        __post('zoom|unsupported');
      }
    } catch (e) { }
  };
  function detect() {
    if (window.UberSDR && typeof UberSDR.command === 'function' && typeof UberSDR.state === 'function') return 'ubersdr';
    // PhantomSDR (avec module CATsync) : imite en partie Kiwi/websdr.org, à reconnaître avant eux.
    if (window.spectrumAudio && typeof window.__catsync_setfreq_impl === 'function' && typeof window.setfreq === 'function') return 'phantom';
    if (typeof window.ext_tune === 'function' && typeof window.ext_get_freq === 'function') return 'kiwi';
    if (typeof window.nominalfreq === 'function' && typeof window.set_mode === 'function' && typeof window.setfreqb === 'function') return 'websdr';
    if (window.UI && typeof UI.getFrequency === 'function' && typeof UI.setFrequency === 'function' && typeof UI.getModulation === 'function') return 'owrx+';
    if (window.jQuery && jQuery.fn && jQuery.fn.demodulatorPanel && typeof window.center_freq === 'number') return 'owrx';
    return '';
  }
  // OpenWebRX à plusieurs profils (un par bande) : une fréquence hors du profil actif ne peut pas être réglée.
  // On choisit le profil qui la couvre d'après son nom ('HF_7MHz', 'VHF_144-146MHz', '40m'...), on bascule,
  // puis on règle la fréquence une fois le nouveau profil chargé.
  var BANDS = { '160m': [1.8, 2.0], '80m': [3.5, 3.8], '60m': [5.3, 5.45], '40m': [7.0, 7.2], '30m': [10.1, 10.15],
    '20m': [14.0, 14.35], '17m': [18.068, 18.168], '15m': [21.0, 21.45], '12m': [24.89, 24.99], '10m': [28.0, 29.7],
    '6m': [50, 52], '4m': [70, 70.5], '2m': [144, 146], '70cm': [430, 440], '23cm': [1240, 1300] };
  function profileRange(name) {
    var n = (name || '').replace(/,/g, '.'), m = n.match(/(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*MHz/i);
    if (m) return [parseFloat(m[1]), parseFloat(m[2])];
    m = n.match(/(\d+(?:\.\d+)?)\s*MHz/i);
    if (m) { var c = parseFloat(m[1]); return [c - 1, c + 2]; }
    m = n.toLowerCase().match(/(\d+)\s*(cm|m)\b/);
    if (m && BANDS[m[1] + m[2]]) return BANDS[m[1] + m[2]];
    return null;
  }
  function owrxInRange(hz) {
    return typeof window.center_freq === 'number' && typeof window.bandwidth === 'number'
      && Math.abs(hz - center_freq) <= bandwidth / 2 - 1000;
  }
  function findProfile(hz) {
    var mhz = hz / 1e6, best = null, bestWidth = 1e9;
    var opts = document.querySelectorAll('#openwebrx-sdr-profiles-listbox option');
    for (var i = 0; i < opts.length; i++) {
      var r = profileRange(opts[i].textContent);
      if (r && mhz >= r[0] && mhz <= r[1] && r[1] - r[0] < bestWidth) { best = opts[i].value; bestWidth = r[1] - r[0]; }
    }
    return best;
  }
  function owrxTune(k, hz, mode) {
    var m = mode === 'fm' ? 'nfm' : mode;
    if (k === 'owrx+') {
      if (m) UI.setModulation(m);
      if (hz) UI.setFrequency(hz, false);
    } else {
      if (m) jQuery('#openwebrx-panel-receiver').demodulatorPanel().setMode(m);
      var d = owrxDemod();
      if (hz && d) d.set_offset_frequency(hz - center_freq);
    }
  }

  function owrxDemod() {
    var p = jQuery('#openwebrx-panel-receiver').demodulatorPanel();
    return p ? p.getDemodulator() : null;
  }

  // UberSDR v2 : API de page window.UberSDR, asynchrone. On relit 'tuning' à chaque tour et on garde la dernière réponse
  // (un abonnement lancé pendant le chargement de la page peut ne jamais aboutir).
  function uberTuning() {
    // Abonnement : la page prévient à chaque changement (~30 ms) ; la lecture ci-dessous, une réponse en retard
    // (~300 ms), reste en secours.
    if (!L.uberSub) {
      L.uberSub = true;
      try {
        UberSDR.on('tuning', function (v) { if (v && v.frequency) { L.uberTuning = v; L.poll(); } });
        Promise.resolve(UberSDR.subscribe(['tuning'])).catch(function () { });
      } catch (e) { }
    }
    if (!L.uberBusy) {
      L.uberBusy = true;
      UberSDR.get('tuning').then(
        function (t) { L.uberTuning = t; L.uberBusy = false; },
        function () { L.uberBusy = false; });
    }
    return L.uberTuning;
  }
  // Session UberSDR démarrée (« Click to start » passé) depuis plus de 0,7 s. Avant, un réglage ne part pas vers le
  // serveur et la page le remplace au démarrage par sa propre fréquence : l'affichage et le son ne concordaient plus.
  function uberRunning() {
    if (!L.uberRunAt && !L.uberSessBusy) {
      L.uberSessBusy = true;
      UberSDR.get('session').then(
        function (s) { if (s && s.running && !L.uberRunAt) L.uberRunAt = Date.now(); L.uberSessBusy = false; },
        function () { L.uberSessBusy = false; });
    }
    return L.uberRunAt && Date.now() - L.uberRunAt > 700;
  }

  L.get = function () {
    var k = detect();
    try {
      if (k === 'ubersdr') { var t = uberTuning(); if (t && t.frequency) return { k: k, f: t.frequency, m: t.mode }; return null; }
      if (k === 'phantom') {
        // Fréquence réellement écoutée : position de la porteuse dans la bande reçue (audioM, en points). Elle suit tous
        // les réglages, y compris un clic dans le spectre ou la cascade, qui ne met pas à jour __catsync_state.
        // À défaut : l'afficheur (MHz), puis __catsync_state.
        var sa = spectrumAudio, hz = 0;
        if (sa && sa.audioMaxSize > 0 && sa.totalBandwidth > 0 && typeof sa.audioM === 'number' && sa.audioM > 0)
          hz = Math.round(sa.baseFreq + sa.audioM * sa.totalBandwidth / sa.audioMaxSize);
        if (!hz) { var inp = document.querySelector('input.font-mono'); hz = inp ? parseFloat(inp.value) * 1e6 : 0; }
        if (!hz) { var st = window.__catsync_state; hz = st && st.hz || 0; }
        return { k: k, f: hz, m: spectrumAudio.demodulation };
      }
      if (k === 'kiwi') return { k: k, f: ext_get_freq(), m: ext_get_mode() };
      if (k === 'websdr') return { k: k, f: nominalfreq() * 1000, m: window.mode };
      if (k === 'owrx+') return { k: k, f: UI.getFrequency(), m: UI.getModulation() };
      if (k === 'owrx') { var d = owrxDemod(); if (d) return { k: k, f: d.get_offset_frequency() + center_freq, m: d.get_modulation() }; }
    } catch (e) { }
    return null;
  };

  // hz = 0 : ne pas changer la fréquence ; mode = '' : ne pas changer le mode. mode : lsb usb cw am fm
  L.set = function (hz, mode) {
    var k = detect();
    try {
      if (k === 'phantom') {
        // setfreq : valeur en kHz (en dessous de 1e6 la page la prend pour des kHz) ; set_mode : nom en majuscules.
        // La page choisit elle-même le mode de la bande quand la fréquence change (LSB sur 40 m…) : fréquence
        // d'abord, puis le mode voulu, ou celui d'avant (sinon ce changement repartirait vers le poste).
        var keep = String(spectrumAudio.demodulation || '');
        if (hz) setfreq(hz / 1000);
        var want = mode ? mode.toUpperCase() : keep.toUpperCase();
        if (want && want !== String(spectrumAudio.demodulation || '').toUpperCase()) set_mode(want);
      } else if (k === 'ubersdr') {
        var args = { ensureVisible: true };
        if (hz) args.frequency = hz;
        if (mode) args.mode = mode === 'fm' ? 'nfm' : mode === 'cw' ? 'cwu' : mode;
        UberSDR.command('tune', args).catch(function () { });
      } else if (k === 'kiwi') {
        var km = mode === 'fm' ? 'nbfm' : mode;
        ext_tune(hz ? hz / 1000 : null, km || ext_get_mode());
      } else if (k === 'websdr') {
        if (mode && mode.toUpperCase() !== String(window.mode).toUpperCase()) set_mode(mode);
        if (hz) {
          // Fréquence hors de la bande affichée : on change de bande comme le bouton de la page (80, 40, 20…).
          // En vue « une bande » (Bordeaux), setfreqb ne le fait pas : seul le chiffre changeait, la cascade restait
          // sur l'ancienne bande. Le changement de bande inverse LSB/USB : le mode d'avant est remis.
          var b = websdrBand(hz / 1000);
          if (b >= 0 && b !== window.band && typeof window.setband === 'function') {
            var before = String(window.mode);
            setband(b);
            if (typeof window.bandeButton === 'function') bandeButton(b);
            if (String(window.mode).toUpperCase() !== before.toUpperCase()) set_mode(before.toLowerCase());
          }
          setfreqb(hz / 1000);
        }
      } else if (k === 'owrx+' || k === 'owrx') {
        if (hz && !owrxInRange(hz)) {
          var prof = findProfile(hz), sel = document.getElementById('openwebrx-sdr-profiles-listbox');
          if (prof && sel && typeof window.sdr_profile_changed === 'function') {
            // Mode à remettre après la bascule (le nouveau profil a son propre mode par défaut).
            var cur = L.get();
            L.pending = { hz: hz, mode: mode || (cur ? Modes(cur.m) : ''), until: Date.now() + 12000 };
            if (sel.value !== prof) { sel.value = prof; sdr_profile_changed(); }
            return;
          }
        }
        owrxTune(k, hz, mode);
      }
    } catch (e) { }
    L.poll();
  };

  // Bordeaux : rappel du mode à côté du S-mètre, tenu à jour seulement par ses boutons de mode (ni par ses boutons
  // de bande, qui inversent pourtant LSB/USB, ni par SDRLink) : remis d'accord avec le mode réel de la page.
  function modeLabel() {
    var el = document.getElementById('getmode'), m = String(window.mode || '').toLowerCase();
    if (!el || !m || typeof window.ModeID !== 'function') return;
    var want = m.charAt(0).toUpperCase() + m.substring(1);
    if (el.innerHTML !== want) ModeID(want);
  }

  // websdr.org : bande (index dans bi) qui contient la fréquence f en kHz, -1 si aucune.
  function websdrBand(f) {
    if (!window.bi || typeof window.nvbands !== 'number') return -1;
    for (var i = 0; i < nvbands; i++) {
      var e = bi[i];
      if (e && f > e.centerfreq - e.samplerate / 2 - 4 && f < e.centerfreq + e.samplerate / 2 + 4) return i;
    }
    return -1;
  }

  // Mode OpenWebRX ramené à la famille comprise par owrxTune.
  function Modes(m) {
    m = String(m || '').toLowerCase();
    return m === 'nfm' || m === 'wfm' ? 'fm' : m;
  }

  L.poll = function () {
    // Bascule de profil OpenWebRX en cours : rien n'est signalé à SDRLink (la fréquence transitoire du nouveau
    // profil ne doit pas être recopiée sur le poste) ; on règle la fréquence dès que le profil est chargé.
    if (L.pending) {
      var k = detect();
      if (Date.now() > L.pending.until) { L.pending = null; return; }
      var ready = k === 'owrx+' ? !!UI.getDemodulator() : k === 'owrx' && !!owrxDemod();
      if (!ready || !owrxInRange(L.pending.hz)) return;
      try { owrxTune(k, L.pending.hz, L.pending.mode); } catch (e) { }
      // Terminé seulement quand la page affiche bien la fréquence voulue (sinon nouvel essai au tour suivant).
      var now = L.get();
      if (!now || Math.abs(now.f - L.pending.hz) > 50) return;
      L.pending = null;
    }
    // Éléments <audio>/<video> créés après le dernier réglage (sortie vers un autre périphérique, par ex.).
    if (L.volume !== 1) {
      var media = document.querySelectorAll('audio,video'), mv = Math.min(1, L.volume);
      for (var i = 0; i < media.length; i++) if (media[i].volume !== mv) media[i].volume = mv;
    }
    var s = L.get();
    if (!s || !(s.f > 0)) return;
    // websdr.org : la page règle elle-même sa fréquence de départ à la fin du chargement (onload), puis au démarrage
    // du son ; signalée plus tôt, SDRLink la réglerait pour rien et recopierait ensuite ce saut sur le poste.
    if (s.k === 'websdr' && !(window.allloadeddone || (L.loadedAt && Date.now() - L.loadedAt > 4000))) return;
    if (s.k === 'ubersdr' && !uberRunning()) return;
    // PhantomSDR : réglé avant les premiers échantillons audio, il restait muet ; on attend que le son joue.
    if (s.k === 'phantom' && !(window.spectrumAudio && spectrumAudio.playTime)) return;
    squelch(s);
    if (s.k === 'websdr') modeLabel();
    var msg = s.k + '|' + Math.round(s.f) + '|' + String(s.m || '');
    if (msg !== L.last) {
      L.last = msg;
      // « |u » : l'utilisateur vient d'agir sur la page (clic, touche, molette). SDRLink suit alors ce changement même
      // pendant la mise en place de la page, où un changement venu de la page seule est remis sur le poste.
      var user = Date.now() - L.userAt < 1500;
      try { __post(msg + (user ? '|u' : '')); } catch (e) { }
    }
  };
  // Seuls les gestes réels comptent (isTrusted) : les appuis simulés par SDRLink au démarrage ne comptent pas.
  L.userAt = 0;
  ['pointerdown', 'mousedown', 'pointerup', 'keydown', 'wheel', 'touchstart', 'touchend'].forEach(function (t) {
    window.addEventListener(t, function (e) { if (e.isTrusted) L.userAt = Date.now(); }, true);
  });
  window.addEventListener('pointermove', function (e) { if (e.isTrusted && e.buttons) L.userAt = Date.now(); }, true);
  // Appui de la souris dans la page, signalé tout de suite (avant le changement de fréquence qu'il peut faire) :
  // SDRLink arrête un scan en cours et garde la fréquence scannée à ce moment.
  window.addEventListener('pointerdown', function (e) {
    if (e.isTrusted) { try { __post('click'); } catch (x) { } }
  }, true);

  window.addEventListener('load', function () { L.loadedAt = Date.now(); });
  setInterval(L.poll, 125);   // un changement fait sur la page est signalé à SDRLink en 60 ms en moyenne

  // Squelch ouvert (websdr.org : case « squelch », OpenWebRX : curseur au minimum), sauf en FM à partir de 30 MHz
  // (relais, FM VHF/UHF) où il est remis tel que la page l'avait. Appliqué au chargement puis seulement quand on entre
  // ou sort de la FM VHF : un squelch remis à la main par l'utilisateur est laissé tel quel.
  function squelch(s) {
    var want = /fm/i.test(String(s.m || '')) && s.f >= 30e6;
    if (want === L.sqWant) return;
    var el = s.k === 'websdr' ? document.getElementById('squelchcheckbox')
           : s.k === 'owrx+' || s.k === 'owrx' ? document.querySelector('.openwebrx-squelch-slider') : null;
    if (!el) return;
    L.sqWant = want;
    try {
      if (el.type === 'checkbox') {
        if (typeof window.setsquelch !== 'function') return;
        if (!want && el.checked) { L.sqWas = true; el.checked = false; window.setsquelch(false); }
        else if (want && L.sqWas === true && !el.checked) { L.sqWas = null; el.checked = true; window.setsquelch(true); }
      } else {
        var min = parseFloat(el.min), v = parseFloat(el.value);
        if (!want && v > min) { L.sqWas = el.value; slide(el, el.min); }
        else if (want && L.sqWas != null && v <= min) { slide(el, L.sqWas); L.sqWas = null; }
      }
    } catch (e) { }
  }
  function slide(el, v) {
    el.value = v;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  // Démarrage sans clic : la lecture automatique étant permise, seul UberSDR attend encore un clic sur
  // « Click to start ». SDRLink clique une fois à la place de l'utilisateur, dans les 30 s qui suivent l'ouverture.
  var startAt = Date.now(), startTimer = setInterval(function () {
    var b = document.querySelector('button.start__go:not(.start__go--alt)');
    if (b && b.offsetParent !== null) { clearInterval(startTimer); try { b.click(); } catch (e) { } }
    else if (Date.now() - startAt > 30000) clearInterval(startTimer);
  }, 500);

  // PhantomSDR (French Alps…) : voile « Tap to enable audio » ; la page attend un appui de souris n'importe où
  // pour activer le son et retirer le voile. SDRLink le simule, toutes les 0,5 s tant que le voile est là (la page
  // peut l'afficher avant d'écouter l'appui), dans les 30 s qui suivent l'ouverture.
  var tapTimer = setInterval(function () {
    if (Date.now() - startAt > 30000) { clearInterval(tapTimer); return; }
    var v = document.getElementById('startaudio');
    if (!v || v.offsetParent === null) return;
    try { document.documentElement.dispatchEvent(new MouseEvent('mousedown', { bubbles: true })); } catch (e) { }
  }, 500);

  // PhantomSDR : après un changement de fréquence (de SDRLink, du clavier ou d'un clic dans le spectre), le son
  // décodé tombe parfois à zéro 1 à 3 s plus tard et le reste, bien que les trames continuent d'arriver ; changer de
  // mode puis revenir le relance. SDRLink renvoie le mode courant au serveur dès qu'une demi-seconde entière de trames
  // reste muette (le squelch ou le mute de la page arrêtent les trames avant : ils ne la déclenchent pas). Trois
  // essais au plus, rendus quand le son revient.
  var ph = { fr: 0, pk: 0, at: 0, tries: 0 };
  setInterval(function () {
    var a = window.spectrumAudio;
    if (!a || typeof a.playPCM !== 'function' || typeof a.setAudioDemodulation !== 'function') return;
    if (!a.__sdrlinkPcm) {
      var play = a.playPCM;
      a.playPCM = function (e) {
        ph.fr++;
        try { for (var i = 0; i < e.length; i += 4) { var v = e[i] < 0 ? -e[i] : e[i]; if (v > ph.pk) ph.pk = v; } } catch (x) { }
        return play.apply(this, arguments);
      };
      a.__sdrlinkPcm = true;
      ph.at = Date.now();
      return;
    }
    if (a.audioM !== ph.m) { ph.m = a.audioM; ph.tries = 0; }   // nouvelle fréquence : nouveaux essais
    if (Date.now() - ph.at < 500) return;
    if (ph.fr >= 10 && ph.pk < 0.0005) {
      if (ph.tries < 3 && a.playTime) { ph.tries++; try { a.setAudioDemodulation(a.demodulation); } catch (e) { } }
    } else if (ph.pk >= 0.0005) ph.tries = 0;
    ph.fr = 0; ph.pk = 0; ph.at = Date.now();
  }, 250);

  // KiwiSDR complet : SDRLink appuie sur « Enter queue » (file d'attente) ; KiwiSDR recharge ensuite la page de
  // lui-même dès qu'un canal se libère. Avec des canaux à mot de passe, « Queue or Camp » mène d'abord à cet écran.
  // Une fois par ouverture, dans les 30 s : une sortie de file voulue par l'utilisateur n'est pas annulée.
  var queueTimer = setInterval(function () {
    if (Date.now() - startAt > 30000) { clearInterval(queueTimer); return; }
    if (!window.kiwi) return;
    var btns = document.querySelectorAll('button');
    for (var i = 0; i < btns.length; i++) {
      var b = btns[i], t = (b.textContent || '').trim();
      if (b.offsetParent === null) continue;
      if ((t === 'Enter queue' && !window.kiwi.queued) || t === 'Queue or Camp') { clearInterval(queueTimer); try { b.click(); } catch (e) { } return; }
    }
  }, 500);
})();
