package com.unascribed.sup.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Bases {

	private static final String hex = "0123456789abcdef";

	public static String bytesToHex(byte[] bys) {
		return bytesToHex(bys, 0, bys.length);
	}
	
	public static String bytesToHex(byte[] bys, int ofs, int len) {
		StringBuilder sb = new StringBuilder(bys.length*2);
		for (int i = ofs; i < ofs+len; i++) {
			int hi = ((bys[i]&0xF0)>>4);
			int lo = bys[i]&0xF;
			sb.append(hex.charAt(hi));
			sb.append(hex.charAt(lo));
		}
		return sb.toString();
	}

	public static String b64ToString(String b64) {
		return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
	}

	public static String longToHex(long l) {
		return intToHex((l>>32L)&0xFFFFFFFF)+intToHex(l&0xFFFFFFFF);
	}

	private static String intToHex(long i) {
		// bad
		return Long.toHexString((i&0xFFFFFFFFL)|0xF00000000L).substring(1);
	}

}
