package com.unascribed.sup.json;

public class OrderedVersion implements Comparable<OrderedVersion> {
	
	public String name;
	public int code;
	
	public OrderedVersion() {}
	
	public OrderedVersion(String name, int code) {
		this.name = name;
		this.code = code;
	}
	
	@Override
	public String toString() {
		return name+" ("+code+")";
	}

	@Override
	public int compareTo(OrderedVersion that) {
		return Integer.compare(this.code, that.code);
	}
	
}