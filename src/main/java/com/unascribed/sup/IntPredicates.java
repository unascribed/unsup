package com.unascribed.sup;

import java.util.Arrays;
import java.util.function.IntPredicate;

class IntPredicates {

	public static IntPredicate none() {
		return v -> false;
	}
	
	public static IntPredicate equals(int i) {
		return v -> v == i;
	}
	
	public static IntPredicate inSet(int... values) {
		Arrays.sort(values);
		return v -> Arrays.binarySearch(values, v) >= 0;
	}
	
	public static IntPredicate inRange(int min, int max) {
		return v -> v >= min && v < max;
	}
	
	public static IntPredicate greaterThan(int i) {
		return v -> v > i;
	}
	
	public static IntPredicate greaterEqual(int i) {
		return v -> v >= i;
	}
	
	public static IntPredicate lessThan(int i) {
		return v -> v < i;
	}
	
	public static IntPredicate lessEqual(int i) {
		return v -> v <= i;
	}
	
	public static IntPredicate any() {
		return v -> true;
	}
	
}
