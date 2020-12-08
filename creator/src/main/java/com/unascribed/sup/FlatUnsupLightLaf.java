package com.unascribed.sup;

import com.formdev.flatlaf.FlatLightLaf;

public class FlatUnsupLightLaf extends FlatLightLaf {

	public static boolean install( ) {
		return install(new FlatUnsupLightLaf());
	}

	@Override
	public String getName() {
		return "Unsup Light";
	}

	@Override
	public String getDescription() {
		return "Unsup Light Look and Feel";
	}
	
}
