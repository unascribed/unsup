package com.unascribed.sup;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;

import com.formdev.flatlaf.util.UIScale;

public class DualIcon implements Icon {

	private final Icon left;
	private final Icon right;
	
	public DualIcon(Icon left, Icon right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		left.paintIcon(c, g, x, y);
		right.paintIcon(c, g, x+UIScale.scale(4)+left.getIconWidth(), y);
	}

	@Override
	public int getIconWidth() {
		return left.getIconWidth()+right.getIconWidth()+5;
	}

	@Override
	public int getIconHeight() {
		return Math.max(left.getIconHeight(), right.getIconHeight());
	}

}
