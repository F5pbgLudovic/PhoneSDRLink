import fr.f5pbg.phonesdrlink.Js8Text;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** PC test bench for Js8Text. Usage: java -cp out TestText <path to js8jsc.bin>
 *  Only the pack functions needed for round trips are re-implemented here (ported from Varicode.cpp). */
public class TestText {
    static final String A72 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-+/?.";
    static final String AN = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ /@";
    static int pass, fail;

    static void check(String name, Object got, Object exp) {
        boolean ok = Objects.equals(got, exp);
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  got=[" + got + "] expected=[" + exp + "]"));
    }

    // ---- bit helpers (bits as a String of 0/1)
    static String bits(long v, int n) { StringBuilder s = new StringBuilder(); for (int i = n - 1; i >= 0; i--) s.append(((v >>> i) & 1) != 0 ? '1' : '0'); return s.toString(); }

    static String pack72(String b72) {
        StringBuilder o = new StringBuilder();
        for (int i = 0; i < 12; i++) o.append(A72.charAt(Integer.parseInt(b72.substring(i * 6, i * 6 + 6), 2)));
        return o.toString();
    }

    static long packAN50(String v) {
        StringBuilder w = new StringBuilder(v.replaceAll("[^A-Z0-9 /@]", ""));
        if (w.length() > 3 && w.charAt(3) != '/') w.insert(3, ' ');
        if (w.length() > 7 && w.charAt(7) != '/') w.insert(7, ' ');
        while (w.length() < 11) w.append(' ');
        long a = 38L * 38 * 38 * 2 * 38 * 38 * 38 * 2 * 38 * 38 * AN.indexOf(w.charAt(0));
        long b = 38L * 38 * 38 * 2 * 38 * 38 * 38 * 2 * 38 * AN.indexOf(w.charAt(1));
        long c = 38L * 38 * 38 * 2 * 38 * 38 * 38 * 2 * AN.indexOf(w.charAt(2));
        long d = 38L * 38 * 38 * 2 * 38 * 38 * 38 * (w.charAt(3) == '/' ? 1 : 0);
        long e = 38L * 38 * 38 * 2 * 38 * 38 * AN.indexOf(w.charAt(4));
        long f = 38L * 38 * 38 * 2 * 38 * AN.indexOf(w.charAt(5));
        long g = 38L * 38 * 38 * 2 * AN.indexOf(w.charAt(6));
        long h = 38L * 38 * 38 * (w.charAt(7) == '/' ? 1 : 0);
        long i = 38L * 38 * AN.indexOf(w.charAt(8));
        long j = 38L * AN.indexOf(w.charAt(9));
        long k = AN.indexOf(w.charAt(10));
        return a + b + c + d + e + f + g + h + i + j + k;
    }

    static final long NBASECALL = 37L * 36 * 10 * 27 * 27 * 27;

    static long packCall(String s, boolean[] portable) {
        if (s.equals("@ALLCALL")) return NBASECALL + 2;
        if (s.equals("@HB")) return NBASECALL + 45;
        if (s.equals("<....>")) return NBASECALL + 1;
        if (s.endsWith("/P")) { s = s.substring(0, s.length() - 2); portable[0] = true; }
        if (s.startsWith("3DA0")) s = "3D0" + s.substring(4);
        String m = (s.length() >= 3 && Character.isDigit(s.charAt(2))) ? s : " " + s;
        while (m.length() < 6) m += " ";
        long p = AN.indexOf(m.charAt(0));
        p = 36 * p + AN.indexOf(m.charAt(1));
        p = 10 * p + AN.indexOf(m.charAt(2));
        p = 27 * p + AN.indexOf(m.charAt(3)) - 10;
        p = 27 * p + AN.indexOf(m.charAt(4)) - 10;
        p = 27 * p + AN.indexOf(m.charAt(5)) - 10;
        return p;
    }

    static int packGrid(String g) {
        g = g.substring(0, 4).toUpperCase() + "mm";
        int nlong = 180 - 20 * (g.charAt(0) - 'A');
        int n20d = 2 * (g.charAt(2) - '0');
        float xminlong = (float) (5 * (g.charAt(4) - 'a' + 0.5));
        float dlong = (float) (nlong - n20d - xminlong / 60.0);
        int nlat = -90 + 10 * (g.charAt(1) - 'A') + g.charAt(3) - '0';
        float xminlat = (float) (2.5 * (g.charAt(5) - 'a' + 0.5));
        float dlat = (float) (nlat + xminlat / 60.0);
        int ilong = (int) dlong, ilat = (int) (dlat + 90);
        return ((ilong + 180) / 2) * 180 + ilat;
    }

    /** [3 flag][50 callsign][11 num high],[5 num low][3 bits3] */
    static String packCompoundFrame(String call, int type, int num, int bits3) {
        return pack72(bits(type, 3) + bits(packAN50(call), 50) + bits((num >> 5) & 0x7FF, 11) + bits(num & 31, 5) + bits(bits3, 3));
    }

    static String packHeartbeat(String call, String grid, boolean alt, int n) {
        int extra = grid == null ? 32767 : packGrid(grid);
        if (alt) extra |= 1 << 15;
        return packCompoundFrame(call, 0, extra, n);
    }

    static String packDirected(String from, String to, int cmd, int num) {
        boolean[] pf = new boolean[1], pt = new boolean[1];
        long f = packCall(from, pf), t = packCall(to, pt);
        int extra = (pf[0] ? 128 : 0) + (pt[0] ? 64 : 0) + num;
        return pack72(bits(3, 3) + bits(f, 28) + bits(t, 28) + bits(cmd % 32, 5) + bits(extra, 8));
    }

    // ---- data frames
    static final String[] HC = {" ", "E", "T", "A", "O", "I", "N", "S", "H", "R", "D", "L", "C", "U", "M", "W", "F", "G", "Y", "P", "B", ".", "V", "K", "-", "+", "?", "!", "\"", "X", "0", "J", "1", "Q", "2", "Z", "3", "5", "4", "9", "8", "6", "7", "/"};
    static final String[] HB = {"01", "100", "1101", "0011", "11111", "11100", "10111", "10100", "00011", "00000", "111011", "110011", "110001", "101101", "101011", "001011", "001001", "000101", "000011", "1111011", "1111001", "1110100", "1100101", "1100100", "1100001", "1100000", "1011001", "1011000", "1010101", "1010100", "0010101", "0010100", "0010001", "0010000", "0001001", "0001000", "0000101", "0000100", "11110101", "11110100", "11110001", "11110000", "11101011", "11101010"};

    static String pad72(String b) {
        StringBuilder s = new StringBuilder(b);
        for (int i = 0; s.length() < 72; i++) s.append(i == 0 ? '0' : '1');
        return s.toString();
    }

    /** returns {frame, consumed chars} */
    static Object[] packHuff(String text) {
        StringBuilder b = new StringBuilder("10");
        int i = 0;
        while (i < text.length()) {
            int k = Arrays.asList(HC).indexOf(text.substring(i, i + 1));
            if (b.length() + HB[k].length() < 72) { b.append(HB[k]); i++; } else break;
        }
        return new Object[]{pack72(pad72(b.toString())), i};
    }

    static List<String> words = new ArrayList<>();
    static Map<String, Integer> index = new HashMap<>();

    static String codeword(int idx, boolean sep) {
        final int s = 7, c = 9;
        StringBuilder out = new StringBuilder(bits(((idx % s) << 1) + (sep ? 1 : 0), 5));
        int x = idx / s;
        while (x > 0) { x--; out.insert(0, bits((x % c) + s, 4)); x /= c; }
        return out.toString();
    }

    /** JSC compress of the whole text into one frame's worth; returns {frame, consumed chars}. */
    static Object[] packJsc(String text, String prefix) {
        StringBuilder b = new StringBuilder(prefix);
        int consumed = 0;
        String[] ws = text.split(" ", -1);
        outer:
        for (int i = 0; i < ws.length; i++) {
            String w = ws[i];
            boolean last = i == ws.length - 1, spaceCh = false;
            if (w.isEmpty() && !last) { w = " "; spaceCh = true; }
            while (!w.isEmpty()) {
                int l = w.length();
                while (l > 0 && !index.containsKey(w.substring(0, l))) l--;
                if (l == 0) throw new RuntimeException("no JSC entry for " + w);
                String piece = w.substring(0, l);
                String rest = w.substring(l);
                boolean sep = rest.isEmpty() && !spaceCh && !last;
                String cw = codeword(index.get(piece), sep);
                if (b.length() + cw.length() >= 72) break outer;
                b.append(cw);
                consumed += piece.length() + (sep ? 1 : 0);
                w = rest;
            }
        }
        return new Object[]{pack72(pad72(b.toString())), consumed};
    }

    static String snr(int v) { return Js8Text.frameText(null, 0, false); }

    public static void main(String[] a) throws Exception {
        // 0. table not loaded -> graceful
        String dummyJsc = (String) packJscNoTable();
        Js8Text.Frame deg = Js8Text.decode(dummyJsc, 0, false);
        check("JSC frame without table degrades to raw chars", deg != null && deg.degraded && deg.text.equals(dummyJsc), true);
        check("jscLoaded false before load", Js8Text.jscLoaded(), false);

        byte[] d = Files.readAllBytes(Paths.get(a[0]));
        int st = 0;
        for (int i = 0; i < d.length; i++) if (d[i] == 0) {
            String w = new String(d, st, i - st, StandardCharsets.ISO_8859_1);
            if (!index.containsKey(w)) index.put(w, words.size());
            words.add(w);
            st = i + 1;
        }
        check("table has 262144 words", words.size(), 262144);
        long t0 = System.nanoTime();
        try (InputStream in = new FileInputStream(a[0])) { Js8Text.loadJsc(in); }
        try (InputStream in = new FileInputStream(a[0])) { Js8Text.loadJsc(in); } // idempotent
        System.out.println("loadJsc ms: " + (System.nanoTime() - t0) / 1000000);
        check("jscLoaded", Js8Text.jscLoaded(), true);
        check("word 0", words.get(0), "E");

        // 1. heartbeat / CQ
        check("HB F5PBG JN18", Js8Text.frameText(packHeartbeat("F5PBG", "JN18", false, 0), 1, false), "F5PBG: @HB HEARTBEAT JN18");
        check("HB no grid", Js8Text.frameText(packHeartbeat("KN4CRD", null, false, 0), 0, false), "KN4CRD: @HB HEARTBEAT");
        check("CQ F5PBG JN18", Js8Text.frameText(packHeartbeat("F5PBG", "JN18", true, 7), 0, false), "F5PBG: @ALLCALL CQ JN18");
        check("CQ DX FN31", Js8Text.frameText(packHeartbeat("K1ABC", "FN31", true, 1), 0, false), "K1ABC: @ALLCALL CQ DX FN31");
        check("HB grid RE78", Js8Text.frameText(packHeartbeat("ZL1AA", "RE78", false, 3), 0, false), "ZL1AA: @HB HEARTBEAT RE78");
        check("HB grid IO91", Js8Text.frameText(packHeartbeat("G4XYZ", "IO91", false, 0), 0, false), "G4XYZ: @HB HEARTBEAT IO91");
        check("HB grid AA00", Js8Text.frameText(packHeartbeat("VK3AB", "AA00", false, 0), 0, false), "VK3AB: @HB HEARTBEAT AA00");
        check("HB grid RR99", Js8Text.frameText(packHeartbeat("JA1AB", "RR99", false, 0), 0, false), "JA1AB: @HB HEARTBEAT RR99");

        // 2. directed
        check("directed SNR -10", Js8Text.frameText(packDirected("F5PBG", "K1ABC", 25, 21), 0, false), "F5PBG: K1ABC SNR -10");
        check("directed SNR +05", Js8Text.frameText(packDirected("F5PBG", "K1ABC", 25, 36), 0, false), "F5PBG: K1ABC SNR +05");
        check("directed GRID?", Js8Text.frameText(packDirected("F5PBG", "K1ABC", 4, 0), 0, false), "F5PBG: K1ABC GRID?");
        check("directed HEARING?", Js8Text.frameText(packDirected("DL1ABC", "F5PBG", 3, 0), 0, false), "DL1ABC: F5PBG HEARING?");
        check("directed to @ALLCALL 73", Js8Text.frameText(packDirected("F5PBG", "@ALLCALL", 28, 0), 0, false), "F5PBG: @ALLCALL 73");
        check("directed /P", Js8Text.frameText(packDirected("F5PBG/P", "K1ABC", 21, 0), 0, false), "F5PBG/P: K1ABC RR");
        check("directed freetext cmd", Js8Text.frameText(packDirected("F5PBG", "K1ABC", 31, 0), 0, false), "F5PBG: K1ABC");
        check("directed 2-letter-digit call", Js8Text.frameText(packDirected("3A2MD", "K1ABC", 22, 0), 0, false), "3A2MD: K1ABC QSL?");
        check("directed <....>", Js8Text.frameText(packDirected("<....>", "K1ABC", 22, 0), 0, false), "<....>: K1ABC QSL?");

        // 3. compound
        check("compound", Js8Text.frameText(packCompoundFrame("KN4CRD/P", 1, 32767, 0), 0, false), "KN4CRD/P:");
        check("compound directed grid", Js8Text.frameText(packCompoundFrame("KN4CRD/P", 2, packGrid("EM73"), 0), 0, false), "KN4CRD/P EM73");
        check("compound directed SNR -10", Js8Text.frameText(packCompoundFrame("F5PBG/QRP", 2, 32410 + 128 + 21, 0), 0, false), "F5PBG/QRP SNR -10");
        check("compound directed cmd", Js8Text.frameText(packCompoundFrame("F5PBG/MM", 2, 32410 + 28, 0), 0, false), "F5PBG/MM 73");

        // 4. first/last flags
        Js8Text.Frame fl = Js8Text.decode(packHeartbeat("F5PBG", "JN18", false, 0), 3, false);
        check("flags first+last", fl.first && fl.last && fl.kind == 0, true);
        fl = Js8Text.decode(packDirected("F5PBG", "K1ABC", 25, 21), 1, false);
        check("flag first only", fl.first && !fl.last && fl.kind == 3, true);

        // 5. huffman data
        Object[] h = packHuff("HELLO WORLD");
        check("huff consumed", h[1], 11);
        check("huffman HELLO WORLD", Js8Text.frameText((String) h[0], 0, false), "HELLO WORLD");
        h = packHuff("CQ CQ DE F5PBG F5PBG K");
        check("huffman long truncated roundtrip", ((String) "CQ CQ DE F5PBG F5PBG K").startsWith(Js8Text.decode((String) h[0], 1, false).text), true);
        check("huffman leading/trailing space kept", Js8Text.decode((String) packHuff(" 73 ")[0], 0, false).text, " 73 ");

        // 6. JSC data
        Object[] j = packJsc("HELLO WORLD", "11");
        check("jsc consumed all", j[1], 11);
        check("JSC HELLO WORLD", Js8Text.frameText((String) j[0], 0, false), "HELLO WORLD");
        String txt = "THIS IS A TEST OF THE JS8CALL DECODER FROM F5PBG IN FRANCE 73";
        StringBuilder acc = new StringBuilder();
        String rest = txt;
        int frames = 0;
        while (!rest.isEmpty() && frames < 20) {
            Object[] r = packJsc(rest, "11");
            int n = (Integer) r[1];
            if (n == 0) break;
            acc.append(Js8Text.frameText((String) r[0], frames == 0 ? 1 : 0, false));
            rest = rest.substring(n);
            frames++;
        }
        check("JSC multi-frame message (" + frames + " frames)", acc.toString(), txt);
        Object[] fast = packJsc("HELLO WORLD THIS IS FAST", "");
        String ft = Js8Text.frameText((String) fast[0], 4, true);
        check("fast data (JSC, no prefix)", ft, "HELLO WORLD THIS IS FAST".substring(0, (Integer) fast[1]).trim() .isEmpty() ? ft : "HELLO WORLD THIS IS FAST".substring(0, (Integer) fast[1]));
        System.out.println("   fast consumed " + fast[1] + " chars -> [" + ft + "]");
        Js8Text.Frame ff = Js8Text.decode((String) fast[0], 4 | 1, true);
        check("fast kind data + first flag", ff != null && ff.kind == 4 && ff.first && !ff.last, true);

        // 7. garbage never throws / returns null
        check("garbage short", Js8Text.frameText("ABC", 0, false), null);
        check("garbage invalid char", Js8Text.frameText("AAAAAAAAAAA~", 0, false), null);
        check("garbage null", Js8Text.frameText(null, 0, false), null);
        check("garbage with space", Js8Text.frameText("AAAAAA AAAAA", 0, false), null);
        Random rnd = new Random(1);
        int nulls = 0, nonnull = 0;
        for (int i = 0; i < 20000; i++) {
            StringBuilder s = new StringBuilder();
            for (int k = 0; k < 12; k++) s.append(A72.charAt(rnd.nextInt(64)));
            Js8Text.Frame f = Js8Text.decode(s.toString(), rnd.nextInt(8), rnd.nextBoolean());
            if (f == null) nulls++; else nonnull++;
        }
        System.out.println("   random frames: decoded " + nonnull + ", null " + nulls + " (no exception)");
        check("random frames no exception", true, true);

        System.out.println("TOTAL pass=" + pass + " fail=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    /** a JSC frame built before the table is loaded: bits 11 + arbitrary then padding. */
    static Object packJscNoTable() { return pack72(pad72("11" + "0000" + "0")); }
}
