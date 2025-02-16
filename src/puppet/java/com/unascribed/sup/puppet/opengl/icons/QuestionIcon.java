package com.unascribed.sup.puppet.opengl.icons;

import static com.unascribed.sup.puppet.opengl.util.GL.*;

// custom, but inspired by https://pictogrammers.com/library/mdi/icon/help-circle-outline/
class QuestionIcon {

	public static void draw(int bg, int fg) {
		glColorPacked3i(fg);
		drawCircle(0, 0, 24);
		glColorPacked3i(bg);
		drawCircle(0, 0, 20);
		glColorPacked3i(fg);
		drawCircleArc(0, -2, 8, 0, (Math.PI/2)*3);
		glBegin(GL_QUADS);
			glVertex2f(-1, 0);
			glVertex2f(1, 0);
			glVertex2f(1, 3);
			glVertex2f(-1, 3);
			
			glVertex2f(-1, 4);
			glVertex2f(1, 4);
			glVertex2f(1, 6);
			glVertex2f(-1, 6);
		glEnd();
		glColorPacked3i(bg);
		drawCircle(0, -2, 4);
	}
	
}
