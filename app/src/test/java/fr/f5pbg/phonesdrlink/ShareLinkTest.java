package fr.f5pbg.phonesdrlink;

import static org.junit.Assert.*;
import org.junit.Test;

public class ShareLinkTest {
    static final String[] MODES = { "USB", "LSB", "AM", "FM", "DATAU", "CW" };
    static final String TW = "http://websdr.ewi.utwente.nl:8901/";

    @Test public void buildMinimalIsExact() {
        assertEquals("https://f5pbgludovic.github.io/PhoneSDRLink/s/?u=http%3A%2F%2Fwebsdr.ewi.utwente.nl%3A8901%2F&f=7100000&m=LSB",
            ShareLink.build(TW, 7100000L, "LSB", 0, ""));
    }

    @Test public void roundTripAllFields() {
        ShareLink s = ShareLink.parse(ShareLink.build(TW, 7074000L, "USB", 2900, "FT8"), MODES);
        assertNotNull(s);
        assertEquals(TW, s.url); assertEquals(7074000L, s.hz); assertEquals("USB", s.mode);
        assertEquals(2900, s.bwHz); assertEquals("FT8", s.decoder);
    }

    @Test public void roundTripMinimalHasDefaults() {
        ShareLink s = ShareLink.parse(ShareLink.build(TW, 3650000L, "LSB", 0, null), MODES);
        assertNotNull(s);
        assertEquals(0, s.bwHz); assertEquals("", s.decoder);
    }

    @Test public void urlWithSpecialCharacters() {
        String u = "http://kiwi.example.org:8073/?f=7074usb&z=10#top";
        assertEquals(u, ShareLink.parse(ShareLink.build(u, 7074000L, "USB", 0, ""), MODES).url);
        String v = "https://sdr.exemple.fr/réception a+b/";
        assertEquals(v, ShareLink.parse(ShareLink.build(v, 7074000L, "USB", 0, ""), MODES).url);
    }

    @Test public void decoderWithSpace() {
        assertEquals("RADE V1", ShareLink.parse(ShareLink.build(TW, 14236000L, "USB", 0, "RADE V1"), MODES).decoder);
    }

    @Test public void lowercaseModeAndUppercaseSchemeAccepted() {
        ShareLink s = ShareLink.parse("PhoneSDRLink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=usb", MODES);
        assertNotNull(s);
        assertEquals("USB", s.mode);
    }

    @Test public void customSchemeStillAccepted() {
        ShareLink s = ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB", MODES);
        assertNotNull(s);
        assertEquals("http://a.b/", s.url);
    }

    @Test public void webLinkWithoutTrailingSlashAccepted() {
        assertNotNull(ShareLink.parse("https://f5pbgludovic.github.io/PhoneSDRLink/s?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB", MODES));
    }

    @Test public void recognisesOnlyOwnLinks() {
        assertTrue(ShareLink.isShareLink("https://f5pbgludovic.github.io/PhoneSDRLink/s/?u=x"));
        assertTrue(ShareLink.isShareLink("phonesdrlink://tune?u=x"));
        assertFalse(ShareLink.isShareLink("https://f5pbgludovic.github.io/PhoneSDRLink/"));
        assertFalse(ShareLink.isShareLink("https://evil.example/PhoneSDRLink/s/?u=x"));
        assertFalse(ShareLink.isShareLink("https://bt0r.github.io/PhoneSDRLink/s/?u=x"));   // ancienne page de test du fork
        assertFalse(ShareLink.isShareLink(null));
    }

    @Test public void unknownParametersIgnored() {
        assertNotNull(ShareLink.parse("phonesdrlink://tune?v=2&u=http%3A%2F%2Fa.b%2F&zz=1&f=7074000&m=USB", MODES));
    }

    @Test public void rejectsWrongSchemeOrHost() {
        assertNull(ShareLink.parse(null, MODES));
        assertNull(ShareLink.parse("https://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://other?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB", MODES));
        assertNull(ShareLink.parse("https://evil.example/PhoneSDRLink/s/?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB", MODES));
    }

    @Test public void rejectsMissingRequired() {
        assertNull(ShareLink.parse("phonesdrlink://tune?f=7074000&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000", MODES));
    }

    @Test public void rejectsFrequencyOutOfRange() {
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=1000&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=500000000&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=abc&m=USB", MODES));
    }

    @Test public void rejectsUnknownMode() {
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=XYZ", MODES));
    }

    @Test public void rejectsNonWebUrl() {
        assertNull(ShareLink.parse(ShareLink.build("javascript:alert(1)", 7074000L, "USB", 0, ""), MODES));
        assertNull(ShareLink.parse(ShareLink.build("file:///sdcard/x.html", 7074000L, "USB", 0, ""), MODES));
        assertNull(ShareLink.parse(ShareLink.build("http://", 7074000L, "USB", 0, ""), MODES));
    }

    @Test public void rejectsMalformedEscape() {
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2&f=7074000&m=USB", MODES));
    }

    @Test public void ignoresBandwidthOutOfRange() {
        assertEquals(0, ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB&bw=50", MODES).bwHz);
        assertEquals(0, ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB&bw=99999", MODES).bwHz);
        assertEquals(0, ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB&bw=x", MODES).bwHz);
    }
}
