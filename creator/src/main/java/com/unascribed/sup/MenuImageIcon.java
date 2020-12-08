package com.unascribed.sup;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import com.formdev.flatlaf.util.UIScale;

public class MenuImageIcon implements Icon {

	private final BufferedImage src;
	private final BufferedImage scratch;

	private int lastColor;
	private Image lastImage;
	
	private float alpha = 1;
	
	public MenuImageIcon(String resource) {
		URL u = ClassLoader.getSystemResource(resource);
		if (u == null) throw new IllegalArgumentException(resource+" does not exist");
		BufferedImage img;
		try {
			img = ImageIO.read(u);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
		this.src = img;
		this.scratch = createScratch(img);
	}
	
	public MenuImageIcon(BufferedImage img) {
		this.src = img;
		this.scratch = createScratch(img);
	}
	
	
	private static BufferedImage createScratch(BufferedImage img) {
		return new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
	}
	
	public MenuImageIcon withAlpha(float alpha) {
		MenuImageIcon copy = new MenuImageIcon(src);
		copy.alpha = alpha;
		return copy;
	}

	@Override
	public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
		int color = c.getForeground().getRGB();
		if (lastImage == null || lastColor != color) {
			if (lastImage != null) lastImage.flush();
			int[] rgb = new int[src.getWidth()*src.getHeight()];
			src.getRGB(0, 0, src.getWidth(), src.getHeight(), rgb, 0, src.getWidth());
			float a = (c.getForeground().getAlpha()/255f) * this.alpha;
			for (int i = 0; i < rgb.length; i++) {
				float pa = ((rgb[i]>>24)&0xFF)/255f;
				pa *= a;
				rgb[i] = (color&0x00FFFFFF) | ((((int)(pa*255))&0xFF)<<24);
			}
			scratch.setRGB(0, 0, src.getWidth(), src.getHeight(), rgb, 0, src.getWidth());
			lastImage = scratch.getScaledInstance(UIScale.scale(18), UIScale.scale(18), Image.SCALE_SMOOTH);
			lastColor = color;
		}
		g.drawImage(lastImage, x, y, null);
	}


	@Override
	public int getIconWidth() {
		return UIScale.scale(18);
	}


	@Override
	public int getIconHeight() {
		return UIScale.scale(18);
	}
	
}
