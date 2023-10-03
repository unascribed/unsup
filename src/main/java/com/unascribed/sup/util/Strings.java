package com.unascribed.sup.util;

public class Strings {

	public static boolean containsWholeWord(String haystack, String needle) {
		if (haystack == null || needle == null) return false;
		return haystack.equals(needle) || haystack.endsWith(" "+needle) || haystack.startsWith(needle+" ") || haystack.contains(" "+needle+" ");
	}

}
