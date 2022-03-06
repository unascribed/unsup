package com.unascribed.sup.json;

import com.unascribed.sup.Creator;

import blue.endless.jankson.JsonElement;
import blue.endless.jankson.api.Marshaller;
import blue.endless.jankson.impl.MarshallerImpl;

public class OrderedVersion implements Comparable<OrderedVersion>, Marshallable {
	
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

	@Override
	public JsonElement serialize(Marshaller m) {
		return Creator.copyInto(new OneLineJsonObject(), MarshallerImpl.getFallback().serialize(this));
	}
	
}