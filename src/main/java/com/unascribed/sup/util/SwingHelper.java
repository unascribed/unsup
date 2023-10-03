package com.unascribed.sup.util;

import javax.imageio.ImageIO;
import javax.swing.JPopupMenu;

// in main rather than puppet for use from the Creator
public class SwingHelper {

	public static void fixSwing() {
		// enable a bunch of nice things that are off by default for legacy compat
		// use OpenGL if possible
		System.setProperty("sun.java2d.opengl", "true");
		// do not use DirectX, it's buggy. software is better if OGL support is missing
		System.setProperty("sun.java2d.d3d", "false");
		System.setProperty("sun.java2d.noddraw", "true");
		// force font antialiasing
		System.setProperty("awt.useSystemAAFontSettings", "on");
		System.setProperty("swing.aatext", "true");
		System.setProperty("swing.useSystemFontSettings", "true");
		// only call invalidate as needed
		System.setProperty("java.awt.smartInvalidate", "true");
		// disable Metal's abuse of bold fonts
		System.setProperty("swing.boldMetal", "false");
		// always create native windows for popup menus (allows animations to play, etc)
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);
		// no ImageIO, I don't want you to write tons of tiny files to the disk, to be quite honest
		ImageIO.setUseCache(false);
	}

}
