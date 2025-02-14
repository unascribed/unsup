package com.unascribed.sup.opengl.icons;

import static com.unascribed.sup.opengl.util.GL.*;

// https://pictogrammers.com/library/mdi/icon/information-outline/
class InfoIcon {

	public static void draw(int bg, int fg) {
		glColorPacked3i(fg);
		drawCircle(0, 0, 24);
		glColorPacked3i(bg);
		drawCircle(0, 0, 20);
		glColorPacked3i(fg);
		glBegin(GL_QUADS);
			glVertex2f(-1, 6);
			glVertex2f(1, 6);
			glVertex2f(1, -2);
			glVertex2f(-1, -2);
			
			glVertex2f(-1, -4);
			glVertex2f(1, -4);
			glVertex2f(1, -6);
			glVertex2f(-1, -6);
		glEnd();
	}
	
}
