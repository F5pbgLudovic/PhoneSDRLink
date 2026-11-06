package fr.f5pbg.phonesdrlink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * JS8Call text layer (receive only): turns one decoded 12-character JS8 frame (alphabet72, 72 bits) plus the
 * 3-bit transmission type (i3: 1 = first frame, 2 = last frame, 4 = data/fast-data flag) into display text.
 * Port of the unpack* functions of JS8Call Varicode.cpp / JSC.cpp and of DecodedText.cpp.
 * JSC asset format (app/src/main/assets/js8jsc.bin): 262144 Latin-1 words in index order, each ending with a 0x00 byte.
 */
public final class Js8Text {
    private Js8Text() {}

    /** Result of decoding one frame. */
    public static final class Frame {
        /** Display text as JS8Call builds it (data frames keep their significant leading/trailing spaces). */
        public String text;
        public boolean first, last;
        /** 0 heartbeat/CQ, 1 compound, 2 compound directed, 3 directed, 4 data (huffman or JSC). */
        public int kind;
        /** True if the JSC table was needed but missing (text is then the raw frame characters). */
        public boolean degraded;
    }

    public static final int KIND_HEARTBEAT = 0, KIND_COMPOUND = 1, KIND_COMPOUND_DIRECTED = 2, KIND_DIRECTED = 3, KIND_DATA = 4;

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ+-./?";
    private static final String ALPHABET72 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-+/?.";
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ /@";
    private static final int NALPHABET = 41;
    private static final long NBASECALL = 37L * 36 * 10 * 27 * 27 * 27;
    private static final int NBASEGRID = 180 * 180;
    private static final int NUSERGRID = NBASEGRID + 10;
    private static final int NMAXGRID = (1 << 15) - 1;

    private static final String[] BASECALLS = {null, "<....>", "@ALLCALL", "@JS8NET",
        "@DX/NA", "@DX/SA", "@DX/EU", "@DX/AS", "@DX/AF", "@DX/OC", "@DX/AN",
        "@REGION/1", "@REGION/2", "@REGION/3",
        "@GROUP/0", "@GROUP/1", "@GROUP/2", "@GROUP/3", "@GROUP/4", "@GROUP/5", "@GROUP/6", "@GROUP/7", "@GROUP/8", "@GROUP/9",
        "@COMMAND", "@CONTROL", "@NET", "@NTS",
        "@RESERVE/0", "@RESERVE/1", "@RESERVE/2", "@RESERVE/3", "@RESERVE/4",
        "@APRSIS", "@RAGCHEW", "@JS8", "@EMCOMM", "@ARES", "@MARS", "@AMRRON", "@RACES", "@RAYNET", "@RADAR", "@SKYWARN",
        "@CQ", "@HB", "@QSO", "@QSOPARTY", "@CONTEST", "@FIELDDAY", "@SOTA", "@IOTA", "@POTA", "@QRP", "@QRO"};

    private static final String[] CQS = {"CQ CQ CQ", "CQ DX", "CQ QRP", "CQ CONTEST", "CQ FIELD", "CQ FD", "CQ CQ", "CQ"};

    /** QMap::key(value) of directed_cmds for value 0..31 = lexicographically smallest key (as Qt does). */
    private static final String[] CMD = {" SNR?", " DIT DIT", " NACK", " HEARING?", " GRID?", ">", " STATUS?", " STATUS",
        " HEARING", " MSG", " MSG TO:", " QUERY", " QUERY MSGS", " QUERY CALL", " ACK", " GRID", " INFO?", " INFO", " FB",
        " HW CPY?", " SK", " RR", " QSL?", " QSL", " CMD", " SNR", " NO", " YES", " 73", " HEARTBEAT SNR", " AGN?", " "};

    private static final String[] HUFF_CH = {" ", "E", "T", "A", "O", "I", "N", "S", "H", "R", "D", "L", "C", "U", "M", "W", "F", "G", "Y",
        "P", "B", ".", "V", "K", "-", "+", "?", "!", "\"", "X", "0", "J", "1", "Q", "2", "Z", "3", "5", "4", "9", "8", "6", "7", "/"};
    private static final String[] HUFF_CODE = {"01", "100", "1101", "0011", "11111", "11100", "10111", "10100", "00011", "00000",
        "111011", "110011", "110001", "101101", "101011", "001011", "001001", "000101", "000011",
        "1111011", "1111001", "1110100", "1100101", "1100100", "1100001", "1100000", "1011001", "1011000", "1010101", "1010100",
        "0010101", "0010100", "0010001", "0010000", "0001001", "0001000", "0000101", "0000100",
        "11110101", "11110100", "11110001", "11110000", "11101011", "11101010"};

    // ---------------------------------------------------------------- JSC table
    private static final int JSC_SIZE = 262144;
    private static volatile byte[] jscData;
    private static volatile int[] jscOff;

    /** Loads the JSC word table (asset js8jsc.bin). Idempotent; the stream is read fully but not closed. */
    public static synchronized void loadJsc(InputStream in) throws IOException {
        if (jscData != null) return;
        ByteArrayOutputStream bo = new ByteArrayOutputStream(2000000);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        byte[] d = bo.toByteArray();
        int[] off = new int[JSC_SIZE + 1];
        int w = 0;
        for (int i = 0; i < d.length && w < JSC_SIZE; i++) if (d[i] == 0) off[++w] = i + 1;
        if (w != JSC_SIZE) throw new IOException("bad JSC table: " + w + " words");
        jscOff = off;
        jscData = d;
    }

    public static boolean jscLoaded() { return jscData != null; }

    private static String jscWord(int j) {
        byte[] d = jscData; int[] off = jscOff;
        int s = off[j], e = off[j + 1] - 1;
        char[] c = new char[e - s];
        for (int i = 0; i < c.length; i++) c[i] = (char) (d[s + i] & 0xFF);
        return new String(c);
    }

    // ---------------------------------------------------------------- bit helpers
    private static long bitsToInt(boolean[] b, int from, int n) {
        long v = 0;
        for (int i = 0; i < n; i++) v = (v << 1) | (b[from + i] ? 1 : 0);
        return v;
    }

    /** 12 alphabet72 chars -> 72 bits (the 64-bit value of unpack72bits followed by the 8-bit remainder). Null if invalid. */
    private static boolean[] frameBits(String t) {
        if (t == null || t.length() < 12) return null;
        boolean[] b = new boolean[72];
        for (int i = 0; i < 12; i++) {
            int v = ALPHABET72.indexOf(t.charAt(i));
            if (v < 0) return null;
            for (int j = 0; j < 6; j++) b[i * 6 + j] = ((v >> (5 - j)) & 1) != 0;
        }
        return b;
    }

    // ---------------------------------------------------------------- unpackNbits (kept for completeness)
    static int unpack5bits(String v) { return ALPHABET.indexOf(v.charAt(0)); }
    static int unpack6bits(String v) { return ALPHABET.indexOf(v.charAt(0)); }
    static int unpack16bits(String v) {
        int u = NALPHABET * NALPHABET * ALPHABET.indexOf(v.charAt(0)) + NALPHABET * ALPHABET.indexOf(v.charAt(1)) + ALPHABET.indexOf(v.charAt(2));
        return u > 0xFFFF ? 0 : u;
    }
    static long unpack32bits(String v) { return ((long) unpack16bits(v.substring(0, 3)) << 16) | unpack16bits(v.substring(v.length() - 3)); }
    static long unpack64bits(String v) { return (unpack32bits(v.substring(0, 6)) << 32) | unpack32bits(v.substring(v.length() - 6)); }

    // ---------------------------------------------------------------- alphanumerics, callsign, grid, cmd
    static String unpackAlphaNumeric22(long packed, boolean[] isFlag) {
        if (isFlag != null) isFlag[0] = (packed & 1) != 0;
        packed >>= 1;
        char[] w = new char[4];
        for (int i = 3; i >= 0; i--) { w[i] = ALPHANUMERIC.charAt((int) (packed % 38)); packed /= 38; }
        return new String(w);
    }

    /** Throws IllegalArgumentException when a digit is out of the alphabet (cannot happen for 50 bits < 39*38^... ranges). */
    static String unpackAlphaNumeric50(long packed) {
        char[] w = new char[11];
        w[10] = ALPHANUMERIC.charAt((int) Long.remainderUnsigned(packed, 38)); packed = Long.divideUnsigned(packed, 38);
        w[9] = ALPHANUMERIC.charAt((int) (packed % 38)); packed /= 38;
        w[8] = ALPHANUMERIC.charAt((int) (packed % 38)); packed /= 38;
        w[7] = (packed % 2) != 0 ? '/' : ' '; packed /= 2;
        w[6] = ALPHANUMERIC.charAt((int) (packed % 38)); packed /= 38;
        w[5] = ALPHANUMERIC.charAt((int) (packed % 38)); packed /= 38;
        w[4] = ALPHANUMERIC.charAt((int) (packed % 38)); packed /= 38;
        w[3] = (packed % 2) != 0 ? '/' : ' '; packed /= 2;
        w[2] = ALPHANUMERIC.charAt((int) (packed % 38)); packed /= 38;
        w[1] = ALPHANUMERIC.charAt((int) (packed % 38)); packed /= 38;
        w[0] = ALPHANUMERIC.charAt((int) (packed % 39));
        return new String(w).replace(" ", "");
    }

    /** Returns null when the value is outside the callsign range (JS8Call would index out of bounds). */
    static String unpackCallsign(long value, boolean portable) {
        if (value > NBASECALL && value - NBASECALL < BASECALLS.length) return BASECALLS[(int) (value - NBASECALL)];
        if (value / 7085880L > 38) return null;  // 36*10*27^3 = 7085880; word[0] index must be <= 38
        char[] w = new char[6];
        w[5] = ALPHANUMERIC.charAt((int) (value % 27 + 10)); value /= 27;
        w[4] = ALPHANUMERIC.charAt((int) (value % 27 + 10)); value /= 27;
        w[3] = ALPHANUMERIC.charAt((int) (value % 27 + 10)); value /= 27;
        w[2] = ALPHANUMERIC.charAt((int) (value % 10)); value /= 10;
        w[1] = ALPHANUMERIC.charAt((int) (value % 36)); value /= 36;
        w[0] = ALPHANUMERIC.charAt((int) value);
        String cs = new String(w);
        if (cs.startsWith("3D0")) cs = "3DA0" + cs.substring(3);
        if (cs.charAt(0) == 'Q' && cs.charAt(1) >= 'A' && cs.charAt(1) <= 'Z') cs = "3X" + cs.substring(1);
        if (portable) cs = cs.trim() + "/P";
        return cs.trim();
    }

    /** Maidenhead 4-char locator; "" for values above nbasegrid (as JS8Call). */
    static String unpackGrid(int value) {
        if (value > NBASEGRID) return "";
        float dlat = value % 180 - 90;
        float dlong = value / 180 * 2 - 180 + 2;
        if (dlong < -180) dlong += 360;
        if (dlong > 180) dlong -= 360;
        int nlong = (int) (60.0 * (180.0 - dlong) / 5);
        int n1 = nlong / 240, n2 = (nlong - 240 * n1) / 24;
        int nlat = (int) (60.0 * (dlat + 90) / 2.5);
        int m1 = nlat / 240, m2 = (nlat - 240 * m1) / 24;
        return "" + (char) ('A' + n1) + (char) ('A' + m1) + (char) ('0' + n2) + (char) ('0' + m2);
    }

    private static boolean isSnrCmd(String cmd) { return " SNR".equals(cmd) || " HEARTBEAT SNR".equals(cmd); }

    /** Returns command index 0..31 (25 or 29 for SNR forms); num[0] receives the 6-bit number. */
    static int unpackCmd(int value, int[] num) {
        value &= 0xFF;
        if ((value & 0x80) != 0) {
            num[0] = value & 63;
            return (value & 0x40) != 0 ? 29 : 25;
        }
        num[0] = 0;
        return value & 127;
    }

    static String formatSNR(int snr) {
        if (snr < -60 || snr > 60) return "";
        int a = Math.abs(snr);
        String d = (a < 10 ? "0" : "") + a;
        if (snr < 0) return "-" + d;
        return "+" + d;
    }

    // ---------------------------------------------------------------- message unpackers
    /** unpackCompoundFrame: out[0]=type, out[1]=num(16 bit), out[2]=bits3; returns callsign or null. */
    private static String compoundFrame(boolean[] b, int[] out) {
        int flag = (int) bitsToInt(b, 0, 3);
        if (flag == 4 || flag == 3) return null;
        long call = bitsToInt(b, 3, 50);
        int p11 = (int) bitsToInt(b, 53, 11);
        int p5 = (int) bitsToInt(b, 64, 5);
        out[0] = flag;
        out[1] = (p11 << 5) | p5;
        out[2] = (int) bitsToInt(b, 69, 3);
        try { return unpackAlphaNumeric50(call); } catch (RuntimeException e) { return null; }
    }

    private static Frame heartbeat(boolean[] b) {
        int[] o = new int[3];
        String call = compoundFrame(b, o);
        if (call == null || o[0] != KIND_HEARTBEAT) return null;
        boolean alt = (o[1] & (1 << 15)) != 0;
        String grid = unpackGrid(o[1] & ((1 << 15) - 1));
        Frame f = new Frame();
        f.kind = KIND_HEARTBEAT;
        f.text = call + ": " + (alt ? "@ALLCALL " + CQS[o[2] & 7] : "@HB HEARTBEAT") + " " + grid + " ";
        return f;
    }

    private static Frame compound(boolean[] b) {
        int[] o = new int[3];
        String call = compoundFrame(b, o);
        if (call == null || (o[0] != KIND_COMPOUND && o[0] != KIND_COMPOUND_DIRECTED)) return null;
        int extra = o[1];
        String ex = null;
        if (extra <= NBASEGRID) {
            ex = " " + unpackGrid(extra);
        } else if (NUSERGRID <= extra && extra < NMAXGRID) {
            int[] num = new int[1];
            int cmd = unpackCmd(extra - NUSERGRID, num);
            ex = CMD[cmd];
            if (isSnrCmd(ex)) ex = ex + " " + formatSNR(num[0] - 31);
        }
        Frame f = new Frame();
        f.kind = o[0];
        f.text = o[0] == KIND_COMPOUND ? call + ": " : call + (ex == null ? "" : ex) + " ";
        return f;
    }

    private static Frame directed(boolean[] b) {
        if ((int) bitsToInt(b, 0, 3) != KIND_DIRECTED) return null;
        long pf = bitsToInt(b, 3, 28), pt = bitsToInt(b, 31, 28);
        int cmdI = (int) bitsToInt(b, 59, 5);
        int extra = (int) bitsToInt(b, 64, 8);
        boolean portFrom = ((extra >> 7) & 1) == 1, portTo = ((extra >> 6) & 1) == 1;
        extra %= 64;
        String from = unpackCallsign(pf, portFrom), to = unpackCallsign(pt, portTo);
        if (from == null || to == null) return null;
        String cmd = CMD[cmdI % 32];
        StringBuilder sb = new StringBuilder(from).append(": ").append(to).append(cmd);
        if (extra != 0) sb.append(' ').append(isSnrCmd(cmd) ? formatSNR(extra - 31) : Integer.toString(extra - 31));
        Frame f = new Frame();
        f.kind = KIND_DIRECTED;
        f.text = sb.append(' ').toString();
        return f;
    }

    private static String huffDecode(boolean[] b, int from, int to) {
        StringBuilder bits = new StringBuilder();
        for (int i = from; i < to; i++) bits.append(b[i] ? '1' : '0');
        String s = bits.toString();
        StringBuilder out = new StringBuilder();
        int p = 0;
        while (p < s.length()) {
            int found = -1;
            for (int k = 0; k < HUFF_CODE.length; k++) if (s.startsWith(HUFF_CODE[k], p)) { found = k; break; }
            if (found < 0) break;
            out.append(HUFF_CH[found]);
            p += HUFF_CODE[found].length();
        }
        return out.toString();
    }

    /** JSC::decompress over bits [from,to). Returns null if the table is not loaded. */
    private static String jscDecompress(boolean[] bv, int from, int to) {
        if (jscData == null) return null;
        final int s = 7, c = 9;
        long[] base = new long[8];
        base[1] = s;
        long p = s;
        for (int k = 2; k < 8; k++) { p *= c; base[k] = base[k - 1] + p; }
        int count = to - from;
        int[] bytes = new int[count / 4 + 1];
        int[] seps = new int[count / 4 + 1];
        int nb = 0, ns = 0, i = 0;
        while (i < count) {
            if (count - i < 4) break;
            int by = (int) bitsToInt(bv, from + i, 4);
            bytes[nb++] = by;
            i += 4;
            if (by < s) {
                if (count - i > 0 && bv[from + i]) seps[ns++] = nb - 1;
                i++;
            }
        }
        StringBuilder out = new StringBuilder();
        int sp = 0, start = 0;
        while (start < nb) {
            int k = 0;
            long j = 0;
            while (start + k < nb && bytes[start + k] >= s) { j = j * c + (bytes[start + k] - s); k++; if (k > 7) return out.toString(); }
            if (j >= JSC_SIZE) break;
            if (start + k >= nb) break;
            j = j * s + bytes[start + k] + base[k];
            if (j >= JSC_SIZE) break;
            out.append(jscWord((int) j));
            if (sp < ns && seps[sp] == start + k) { out.append(' '); sp++; }
            start += k + 1;
        }
        return out.toString();
    }

    /** unpackDataMessage (normal modes) or unpackFastDataMessage; null = nothing decodable / table missing. */
    private static String data(boolean[] b, boolean fast, boolean[] needJsc) {
        if (!fast && !b[0]) return null;
        int n = -1;
        for (int i = 71; i >= 0; i--) if (!b[i]) { n = i; break; }
        if (fast) {
            // bits.mid(0, n); n == -1 means "everything"
            int end = n < 0 ? 72 : n;
            String r = jscDecompress(b, 0, end);
            if (r == null) needJsc[0] = true;
            return r;
        }
        // bits = bits.mid(1) ; n is relative to that (index in 71-bit vector); compressed = bits[0]
        int nn = n - 1; // index in the 71-bit vector (the first removed bit is b[0])
        boolean compressed = b[1];
        // bits.mid(1, nn-1) on the 71-bit vector, i.e. b[2 .. 2+len); len<0 means "to end"
        int len = nn - 1;
        int from = 2, to = len < 0 ? 72 : 2 + len;
        if (compressed) {
            String r = jscDecompress(b, from, to);
            if (r == null) needJsc[0] = true;
            return r;
        }
        return huffDecode(b, from, to);
    }

    // ---------------------------------------------------------------- public API
    /**
     * Decodes one frame. chars12: the 12 JS8 alphabet characters, i3: transmission type bits (1 first, 2 last, 4 data),
     * fastSubmode: unused (JS8Call selects the fast-data layout from the i3 data flag, as DecodedText does).
     * Returns null if the frame cannot be interpreted. Never throws.
     */
    public static Frame decode(String chars12, int i3, boolean fastSubmode) {
        try {
            if (chars12 == null) return null;
            String m = chars12.trim();
            if (m.length() < 12 || m.indexOf(' ') >= 0) return null;
            boolean[] b = frameBits(m);
            if (b == null) return null;
            Frame f = null;
            boolean[] needJsc = new boolean[1];
            if ((i3 & 4) != 0) {
                String d = data(b, true, needJsc);
                if (needJsc[0]) { f = new Frame(); f.text = m.substring(0, 12); f.degraded = true; f.kind = KIND_DATA; }
                else if (d != null && d.length() > 0) { f = new Frame(); f.text = d; f.kind = KIND_DATA; }
            } else {
                String d = data(b, false, needJsc);
                if (needJsc[0]) { f = new Frame(); f.text = m.substring(0, 12); f.degraded = true; f.kind = KIND_DATA; }
                else if (d != null && d.length() > 0) { f = new Frame(); f.text = d; f.kind = KIND_DATA; }
                if (f == null) f = heartbeat(b);
                if (f == null) f = compound(b);
                if (f == null) f = directed(b);
            }
            if (f == null) return null;
            f.first = (i3 & 1) != 0;
            f.last = (i3 & 2) != 0;
            return f;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Single-line display text of a frame (trailing blanks trimmed except for data frames; newlines become spaces). */
    public static String frameText(String chars12, int i3, boolean fastSubmode) {
        Frame f = decode(chars12, i3, fastSubmode);
        if (f == null) return null;
        String t = f.text.replace('\n', ' ').replace('\r', ' ');
        if (f.kind != KIND_DATA) {
            int e = t.length();
            while (e > 0 && t.charAt(e - 1) == ' ') e--;
            t = t.substring(0, e);
        }
        return t;
    }
}
