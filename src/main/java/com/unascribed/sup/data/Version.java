package com.unascribed.sup.data;

import com.grack.nanojson.JsonObject;

public class Version {
	public final String name;
	public final int code;
	
	public Version(String name, int code) {
		this.name = name;
		this.code = code;
	}
	
	public JsonObject toJson() {
		JsonObject obj = new JsonObject();
		obj.put("name", name);
		obj.put("code", code);
		return obj;
	}
	
	public static Version fromJson(JsonObject obj) {
		if (obj == null) return null;
		return new Version(obj.getString("name"), obj.getInt("code"));
	}
	
	@Override
	public String toString() {
		return name+" ("+code+")";
	}
}