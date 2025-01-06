package com.unascribed.sup;

import java.net.URI;
import java.net.URISyntaxException;

public class Util {

	public static final String VERSION = noinline("${version}");
	
	private static String noinline(String s) {
		return s;
	}

	public static URI uriOfPath(String path) throws URISyntaxException {
		return new URI(null, null, path, null);
	}

}
