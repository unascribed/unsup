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
	public final int defaultValue;

	ColorChoice(int defaultValue) {
		this.defaultValue = defaultValue;
	}
	
}
