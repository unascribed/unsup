package com.unascribed.sup.json;

import blue.endless.jankson.JsonElement;
import blue.endless.jankson.api.Marshaller;

public interface Marshallable {

	JsonElement serialize(Marshaller m);
	
}
