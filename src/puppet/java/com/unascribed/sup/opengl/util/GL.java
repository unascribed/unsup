package com.unascribed.sup.opengl.util;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL12;

import com.unascribed.sup.ColorChoice;

public class GL extends GL12 {
	
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
	
}
