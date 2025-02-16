package com.unascribed.sup.puppet.opengl.icons;

import static com.unascribed.sup.puppet.opengl.util.GL.*;

// https://pictogrammers.com/library/mdi/icon/update/
class UpdateIcon {

	public static void draw(int bg, int fg) {
		glColorPacked3i(fg);
		drawCircle(0, 0, 18);
		glColorPacked3i(bg);
		drawCircle(0, 0, 14);
		glBegin(GL_QUADS);
			glVertex2f(6.5f, -1.88f);
			glVertex2f(9.5f, -1.88f);
			glVertex2f(9.5f, 0.1f);
			glVertex2f(6.5f, 0.1f);
		
			glColorPacked3i(fg);
			glVertex2f(0.5f, -4);
			glVertex2f(-1, -4);
			glVertex2f(-1, 1);
			glVertex2f(0.5f, 0.25f);
			
			glVertex2f(-1, 1);
			glVertex2f(0.5f, 0.25f);
			glVertex2f(4, 2.33f);
			glVertex2f(3.28f, 3.54f);
		glEnd();
		glBegin(GL_TRIANGLES);
			glVertex2f(2.22f, -1.88f);
			glVertex2f(9, -9);
			glVertex2f(9, -1.88f);
		glEnd();
	}
	
}
