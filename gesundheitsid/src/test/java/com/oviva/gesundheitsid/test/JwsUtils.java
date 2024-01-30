package com.oviva.gesundheitsid.test;

public class JwsUtils {

  private JwsUtils() {}

  public static String tamperSignature(String jws) {
    var raw = jws.toCharArray();
    raw[raw.length - 3] = flipSecondBit(raw[raw.length - 3]);
    return new String(raw);
  }

  public static String garbageSignature(String jws) {
    var splits = jws.split("\\.");
    return splits[0] + "." + splits[1] + ".garbage";
  }

  private static char flipSecondBit(char a) {
    return (char) ((int) a ^ 0x1);
  }
}
