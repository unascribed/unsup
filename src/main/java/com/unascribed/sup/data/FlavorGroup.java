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
	
	public boolean isBoolean() {
		if (choices.size() == 2) {
			String a = choices.get(0).id;
			String b = choices.get(1).id;
			String on = id+"_on";
			String off = id+"_off";
			return (a.equals(on) && b.equals(off))
					|| (a.equals(off) && b.equals(on));
		}
		return false;
	}
}