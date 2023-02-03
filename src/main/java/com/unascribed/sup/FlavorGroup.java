package com.unascribed.sup;

import java.util.ArrayList;
import java.util.List;

class FlavorGroup {
	String id, name, description;
	List<FlavorChoice> choices = new ArrayList<>();
	transient String defChoice, defChoiceName;
	
	static class FlavorChoice {
		String id;
		String name;
		String description;
		boolean def;
	}
}