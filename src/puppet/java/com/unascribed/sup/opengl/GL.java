package com.unascribed.sup.opengl;

import org.lwjgl.opengl.GL14;

class GL extends GL14 {

	public static void glColorPacked3i(int color) {
		glColor3f(((color >> 16)&0xFF)/255f, ((color >> 8)&0xFF)/255f, ((color >> 0)&0xFF)/255f);
	}
	
	public static void glColorPacked4i(int color) {
		glColor4f(((color >> 16)&0xFF)/255f, ((color >> 8)&0xFF)/255f, ((color >> 0)&0xFF)/255f, ((color >> 24)&0xFF)/255f);
	}
	
}
