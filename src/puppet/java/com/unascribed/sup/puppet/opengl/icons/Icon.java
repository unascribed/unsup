package com.unascribed.sup.puppet.opengl.icons;

import com.unascribed.sup.puppet.ColorChoice;

/*
 * "Mom can we have SVG"
 * "We have SVG at home"
 */
public interface Icon {
	
	Icon FRAGILE = FragileIcon::draw;
	Icon ALERT = AlertIcon::draw;
	Icon QUESTION = QuestionIcon::draw;
	Icon INFO = InfoIcon::draw;
	Icon ERROR = ErrorIcon::draw;
	Icon UPDATE = UpdateIcon::draw;
	
	void draw(int bg, int fg);
	default void draw(ColorChoice bg, ColorChoice fg) {
		draw(bg.get(), fg.get());
	}
	
}
