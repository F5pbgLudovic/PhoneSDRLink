package fr.f5pbg.phonesdrlink;
public class Dt4 { public static void main(String[] a) {
  System.out.println("bruit seul  : [" + Dt.run("cw", 0.04, "", 18, 700, 0, 0, 0) + "]");
  System.out.println("CW SNR bas  : [" + Dt.run("cw", 0.25, "CQ CQ DE F5PBG K", 18, 700, 0, 0, 0) + "]");
  System.out.println("CW 12 wpm   : [" + Dt.run("cw", 0.1, "CQ DE F4ABC", 12, 800, 0, 0, 0) + "]");
  System.out.println("CW 35 wpm   : [" + Dt.run("cw", 0.05, "CQ DE F4ABC K", 35, 650, 0, 0, 0) + "]");
} }
