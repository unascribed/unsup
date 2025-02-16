package com.unascribed.sup.puppet.opengl.icons;

import static com.unascribed.sup.puppet.opengl.util.GL.*;

// https://pictogrammers.com/library/mdi/icon/alert-outline/
class AlertIcon {

	public static void draw(int bg, int fg) {
		glColorPacked3i(fg);
		glBegin(GL_TRIANGLES);
			glVertex2f(0, -10);
			glVertex2f(11, 9);
			glVertex2f(-11, 9);
			
			glColorPacked3i(bg);
			glVertex2f(0, -6.01f);
			glVertex2f(7.531f, 7f);
			glVertex2f(-7.531f, 7f);
		glEnd();
		glColorPacked3i(fg);
		glBegin(GL_QUADS);
			glVertex2f(-1, -2);
			glVertex2f(1, -2);
			glVertex2f(1, 2);
			glVertex2f(-1, 2);
			
			glVertex2f(-1, 4);
			glVertex2f(1, 4);
			glVertex2f(1, 6);
			glVertex2f(-1, 6);
		glEnd();
	}
	
}
