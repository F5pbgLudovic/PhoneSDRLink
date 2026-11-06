import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Reads JSC_map.cpp, writes js8jsc.bin: 262144 Latin-1 words in index order, each terminated by a 0x00 byte
 *  (word #69 is a newline, so '\n' cannot be the terminator).
 *  Usage: java GenJsc.java <JSC_map.cpp> <out js8jsc.bin> */
public class GenJsc {
    public static void main(String[] a) throws Exception {
        String s = new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.ISO_8859_1);
        int p = s.indexOf("map[262144]");
        p = s.indexOf('{', p) + 1;
        String[] words = new String[262144];
        int n = 0, bad = 0;
        final char BS = (char) 92;
        while (true) {
            p = skip(s, p);
            char c = s.charAt(p);
            if (c == '}') break;
            if (c == ',') { p++; continue; }
            if (c != '{') throw new RuntimeException("expected { at " + p);
            p = skip(s, p + 1);
            StringBuilder w = new StringBuilder();
            if (s.charAt(p) != '"') throw new RuntimeException("expected string at " + p);
            p++;
            while (s.charAt(p) != '"') {
                char ch = s.charAt(p++);
                if (ch != BS) { w.append(ch); continue; }
                ch = s.charAt(p++);
                switch (ch) {
                    case 'n': w.append('\n'); break;
                    case 't': w.append('\t'); break;
                    case 'r': w.append('\r'); break;
                    case '"': w.append('"'); break;
                    case '\'': w.append('\''); break;
                    case '0': w.append('\0'); break;
                    case 'x': {
                        int v = 0, k = 0;
                        while (Character.digit(s.charAt(p), 16) >= 0) { v = v * 16 + Character.digit(s.charAt(p++), 16); k++; }
                        if (k == 0 || v > 255) throw new RuntimeException("bad hex escape at " + p);
                        w.append((char) v);
                        break;
                    }
                    default:
                        if (ch == BS) w.append(BS);
                        else throw new RuntimeException("unknown escape at " + p);
                }
            }
            p = skip(s, p + 1);
            if (s.charAt(p++) != ',') throw new RuntimeException("expected , after string at " + p);
            p = skip(s, p);
            int q = p; while (Character.isDigit(s.charAt(q))) q++;
            int len = Integer.parseInt(s.substring(p, q)); p = skip(s, q);
            if (s.charAt(p++) != ',') throw new RuntimeException("expected , after len at " + p);
            p = skip(s, p);
            q = p; while (Character.isDigit(s.charAt(q))) q++;
            int idx = Integer.parseInt(s.substring(p, q)); p = skip(s, q);
            if (s.charAt(p++) != '}') throw new RuntimeException("expected } at " + p);
            if (idx != n) throw new RuntimeException("index mismatch: entry " + n + " has index " + idx);
            if (n >= 262144) throw new RuntimeException("too many entries");
            words[n] = w.toString();
            if (w.length() != len) { bad++; if (bad <= 10) System.out.println("length column differs: #" + n + " '" + w + "' declared " + len); }
            for (int i = 0; i < w.length(); i++) if (w.charAt(i) == 0 || w.charAt(i) > 255) throw new RuntimeException("bad char in #" + n);
            n++;
        }
        if (n != 262144) throw new RuntimeException("count " + n);
        ByteArrayOutputStream o = new ByteArrayOutputStream(3000000);
        for (String w : words) { o.write(w.getBytes(StandardCharsets.ISO_8859_1)); o.write(0); }
        Files.write(Paths.get(a[1]), o.toByteArray());
        System.out.println("words=" + n + " bytes=" + o.size() + " length-column mismatches=" + bad);
    }

    static int skip(String s, int p) {
        while (true) {
            char c = s.charAt(p);
            if (Character.isWhitespace(c)) p++;
            else if (s.startsWith("//", p)) { while (s.charAt(p) != '\n') p++; }
            else if (s.startsWith("/*", p)) p = s.indexOf("*/", p) + 2;
            else return p;
        }
    }
}
