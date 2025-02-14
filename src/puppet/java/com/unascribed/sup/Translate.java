package com.unascribed.sup;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Translate {

	static final Map<String, String> strings = new HashMap<>();

	public static void addTranslation(String key, String value) {
		strings.put(key, value);
	}

	@SuppressWarnings("unlikely-arg-type")
	public static String format(String key, Object... args) {
		if (key.isEmpty()) return "";
		String[] split = key.split("¤");
		if (split.length > 1) {
			int origLen = args.length;
			args = Arrays.copyOf(args, origLen+split.length-1);
			System.arraycopy(split, 1, args, origLen, split.length-1);
			for (int i = 1; i < args.length; i++) {
				if (strings.containsKey(args[i])) {
					args[i] = format((String)args[i], args);
				}
			}
		}
		return String.format(strings.getOrDefault(split[0], key).replace("%n", "\n"), args);
	}

	public static String[] format(String[] keys) {
		String[] out = new String[keys.length];
		for (int i = 0; i < keys.length; i++) {
			out[i] = format(keys[i]);
		}
		return out;
	}

}
