package com.unascribed.sup.puppet.opengl.util;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import com.unascribed.sup.puppet.ColorChoice;

public class GL extends GL13 {
	
	public static class State {
		private int circleList;
		public double glTranslationX, glTranslationY, glTranslationZ;
		private List<double[]> matrixStack = new ArrayList<>();
	}
	
	private static ThreadLocal<State> state = ThreadLocal.withInitial(State::new);
	
	public static void glColor(ColorChoice choice) {
		glColorPacked3i(choice.get());
	}

	public static void glColor(ColorChoice choice, float alpha) {
		glColorPacked3i1f(choice.get(), alpha);
	}
	
	public static void glColorPacked3i(int color) {
		glColorPacked3i1f(color, 1);
	}
	
	public static void glColorPacked4i(int color) {
		glColorPacked3i1f(color, ((color >> 24)&0xFF)/255f);
	}
	
	public static void glColorPacked3i1f(int color, float alpha) {
		glColor4f(((color >> 16)&0xFF)/255f, ((color >> 8)&0xFF)/255f, ((color >> 0)&0xFF)/255f, alpha);
	}
	
	public static void glPushMatrix() {
		GL12.glPushMatrix();
		State s = state.get();
		s.matrixStack.add(0, new double[] {s.glTranslationX, s.glTranslationY, s.glTranslationZ});
	}
	
	public static void glPopMatrix() {
		GL12.glPopMatrix();
		State s = state.get();
		double[] t = s.matrixStack.remove(0);
		s.glTranslationX = t[0];
		s.glTranslationY = t[1];
		s.glTranslationZ = t[2];
	}
	
	public static void glLoadIdentity() {
		GL12.glLoadIdentity();
		State s = state.get();
		s.glTranslationX = 0;
		s.glTranslationY = 0;
		s.glTranslationZ = 0;
	}
	
	public static void glTranslatef(float x, float y, float z) {
		GL12.glTranslatef(x, y, z);
		State s = state.get();
		s.glTranslationX += x;
		s.glTranslationY += y;
		s.glTranslationZ += z;
	}
	
	public static void glTranslated(double x, double y, double z) {
		GL12.glTranslated(x, y, z);
		State s = state.get();
		s.glTranslationX += x;
		s.glTranslationY += y;
		s.glTranslationZ += z;
	}
	
	public static State getState() {
		return state.get();
	}
	
	
	public static void drawRectWH(float x, float y, float w, float h) {
		drawRectXY(x, y, x+w, y+h);
	}
	
	public static void drawRectXY(float x1, float y1, float x2, float y2) {
		glBegin(GL_QUADS);
			buildRectXY(x1, y1, x2, y2);
		glEnd();
	}
	
	public static void buildRectWH(float x, float y, float w, float h) {
		buildRectXY(x, y, x+w, y+h);
	}
	
	public static void buildRectXY(float x1, float y1, float x2, float y2) {
		glVertex2f(x1, y1);
		glVertex2f(x2, y1);
		glVertex2f(x2, y2);
		glVertex2f(x1, y2);
	}
	

	
	public static void drawRectWHI(float x, float y, float w, float h, float inset) {
		drawRectWHII(x, y, w, h, inset, inset);
	}
	
	public static void drawRectXYI(float x1, float y1, float x2, float y2, float inset) {
		drawRectXYII(x1, y1, x2, y2, inset, inset);
	}
	
	public static void buildRectWHI(float x, float y, float w, float h, float inset) {
		buildRectWHII(x, y, w, h, inset, inset);
	}
	
	public static void buildRectXYI(float x1, float y1, float x2, float y2, float inset) {
		buildRectXYII(x1, y1, x2, y2, inset, inset);
	}
	
	
	public static void drawRectWHII(float x, float y, float w, float h, float insetX, float insetY) {
		drawRectXYII(x, y, x+w, y+h, insetX, insetY);
	}
	
	public static void drawRectXYII(float x1, float y1, float x2, float y2, float insetX, float insetY) {
		glBegin(GL_QUADS);
			buildRectXYII(x1, y1, x2, y2, insetX, insetY);
		glEnd();
	}
	
	public static void buildRectWHII(float x, float y, float w, float h, float insetX, float insetY) {
		buildRectXYII(x, y, x+w, y+h, insetX, insetY);
	}
	
	public static void buildRectXYII(float x1, float y1, float x2, float y2, float insetX, float insetY) {
		buildRectXY(x1+insetX, y1+insetY, x2-insetX, y2-insetY);
	}
	

	public static void drawCircle(float x, float y, float dia) {
		State s = state.get();
		int list = s.circleList;
		if (list == 0) {
			list = glGenLists(1);
			glNewList(list, GL_COMPILE_AND_EXECUTE);
				drawCircleArc(0, 0, 1, 0, Math.PI*2);
			glEndList();
			s.circleList = list;
		}
		glPushMatrix();
			glTranslatef(x, y, 0);
			glScalef(dia, dia, 1);
			glCallList(list);
		glPopMatrix();
	}
	
	public static void drawCircleArc(float x, float y, int dia, double start, double end) {
		double r = dia/2D;
		glBegin(GL_TRIANGLE_FAN);
			glVertex2f(x, y);
			
			int detail = 80;
			for (int i = 0; i <= detail; i++) {
				double t = (i/(double)detail)*(Math.PI*2);
				if (t < start || t > end) continue;
				glVertex2d(x+(Math.sin(t)*r), y+(Math.cos(t)*r));
			}
		glEnd();
	}
	
	
	public static void drawCheckmark(float x, float y) {
		glBegin(GL_QUADS);
			buildCheckmark(x, y);
		glEnd();
	}
	
	public static void buildCheckmark(float x, float y) {
		glVertex2f(x-8.422f, y+1.406f);
		glVertex2f(x-5.592f, y-1.422f);
		glVertex2f(x-2.084f, y+2.086f);
		glVertex2f(x-1.918f, y+7.895f);
		
		glVertex2f(x-2.084f, y+2.086f);
		glVertex2f(x-1.918f, y+7.895f);
		glVertex2f(x+8.482f, y-3.674f);
		glVertex2f(x+5.508f, y-6.350f);
	}
	
}
