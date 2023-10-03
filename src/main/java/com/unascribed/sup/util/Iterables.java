package com.unascribed.sup.util;

import java.util.Objects;

public class Iterables {

	public static boolean contains(Iterable<?> arr, Object obj) {
		if (arr != null) {
			boolean anyMatch = false;
			for (Object en : arr) {
				if (Objects.equals(en, obj)) {
					anyMatch = true;
					break;
				}
			}
			if (!anyMatch) {
				return false;
			}
		}
		return true;
	}

	public static boolean intersects(Iterable<?> a, Iterable<?> b) {
		if (a != null) {
			boolean anyMatch = false;
			for (Object en : a) {
				if (contains(b, en)) {
					anyMatch = true;
					break;
				}
			}
			if (!anyMatch) {
				return false;
			}
		}
		return true;
	}

}
