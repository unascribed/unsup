package com.unascribed.sup.json;

import blue.endless.jankson.JsonArray;
import blue.endless.jankson.JsonGrammar;

public class OneLineJsonArray extends JsonArray {
	
	@Override
	public String toJson(JsonGrammar grammar, int depth) {
		return super.toJson(JsonGrammar.COMPACT, depth);
	}
	
}