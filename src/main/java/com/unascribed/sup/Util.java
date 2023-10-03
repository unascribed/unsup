package com.unascribed.sup;

public class Util {

	public static final String VERSION = noinline("${version}");
	
	private static String noinline(String s) {
		return s;
	}

}
