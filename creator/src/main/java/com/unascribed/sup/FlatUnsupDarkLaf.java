package com.unascribed.sup;

import com.formdev.flatlaf.FlatDarkLaf;

public class FlatUnsupDarkLaf extends FlatDarkLaf {

	public static boolean install( ) {
		return install(new FlatUnsupDarkLaf());
	}

	@Override
	public String getName() {
		return "Unsup Dark";
	}

	@Override
	public String getDescription() {
		return "Unsup Dark Look and Feel";
	}
	
}
