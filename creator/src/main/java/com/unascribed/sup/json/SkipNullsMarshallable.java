package com.unascribed.sup.json;

import java.util.Iterator;
import java.util.Map;

import blue.endless.jankson.JsonArray;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonNull;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.Marshaller;
import blue.endless.jankson.impl.MarshallerImpl;

public interface SkipNullsMarshallable extends Marshallable {

	@Override
	default JsonElement serialize(Marshaller m) {
		JsonElement base = MarshallerImpl.getFallback().serialize(this);
		if (base instanceof JsonArray) {
			Iterator<JsonElement> iter = ((JsonArray)base).iterator();
			while (iter.hasNext()) {
				if (iter.next() == JsonNull.INSTANCE) {
					iter.remove();
				}
			}
		} else if (base instanceof JsonObject) {
			 for (Map.Entry<String, JsonElement> en : ((JsonObject)base).entrySet()) {
				 if (en.getValue() == JsonNull.INSTANCE) {
					 ((JsonObject)base).remove(en.getKey());
				 }
			 }
		} else {
			return base;
		}
		return base;
	}
	
}
