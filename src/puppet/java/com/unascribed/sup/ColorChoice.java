package com.unascribed.sup;

public enum ColorChoice {
	BACKGROUND(0x000000),
	TITLE(0xFFFFFF),
	SUBTITLE(0xAAAAAA),
	PROGRESS(0xFF0000),
	PROGRESSTRACK(0xAAAAAA),
	DIALOG(0xFFFFFF),
	BUTTON(0xFFFF00),
	BUTTONTEXT(0x000000),
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
		0xFFFFFF
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
	
}
