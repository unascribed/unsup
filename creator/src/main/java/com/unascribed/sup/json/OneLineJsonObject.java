package com.unascribed.sup.json;

import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.JsonObject;

public class OneLineJsonObject extends JsonObject {
	
	@Override
	public String toJson(JsonGrammar grammar, int depth) {
		return super.toJson(JsonGrammar.COMPACT, depth);
	}
	
}