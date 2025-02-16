package com.unascribed.sup.puppet;

public enum ColorChoice {
	BACKGROUND(0x000000),
	TITLE(0xFFFFFF),
	SUBTITLE(0xAAAAAA),
	PROGRESS(0xFF0000),
	PROGRESSTRACK(0xAAAAAA),
	DIALOG(0xFFFFFF),
	BUTTON(0xFFFF00),
	BUTTONTEXT(0x000000),
	
	QUESTION(0xFF00FF),
	INFO(0x00FFFF),
	WARNING(0xFFFF00),
	ERROR(0xFF0000),
	;
	
	public static boolean usePrettyDefaults = false;
	
	private static int[] prettyDefaults = {
		0x263238,
		0xFFFFFF,
		0x90A4AE,
		0x00EB76,
		0x455A64,
		0xFFFFFF,
		0x00A653,
		0xFFFFFF,
		0xD500F9,
		0x2979FF,
		0xFF9100,
		0xFF1744,
	};
	
	public final int defaultValue;

	ColorChoice(int defaultValue) {
		this.defaultValue = defaultValue;
	}

	public static int[] createLookup() {
		if (usePrettyDefaults) return prettyDefaults.clone();
		int[] rtrn = new int[values().length];
		for (ColorChoice choice : ColorChoice.values()) {
			rtrn[choice.ordinal()] = choice.defaultValue;
		}
		return rtrn;
	}
	
	public int get() {
		return Puppet.getColor(this);
	}
	
}
