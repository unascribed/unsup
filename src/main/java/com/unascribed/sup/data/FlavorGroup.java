package com.unascribed.sup.data;

import java.util.ArrayList;
import java.util.List;

public class FlavorGroup {
	public String id, name, description;
	public List<FlavorChoice> choices = new ArrayList<>();
	public transient String defChoice, defChoiceName;
	
	public static class FlavorChoice {
		public String id;
		public String name;
		public String description;
		public boolean def;
	}
}