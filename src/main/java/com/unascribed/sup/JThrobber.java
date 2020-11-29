package com.unascribed.sup;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.concurrent.TimeUnit;

import javax.swing.JComponent;

class JThrobber extends JComponent {
	private long lastTimeAnimated = System.nanoTime();
	
	private final float spinSpeed = 230f;
	private final int barLength = 16;
	private final int barMaxLength = 270;
	private final long pauseGrowingTime = 200;
	
	private int pausedTimeWithoutGrowing;
	private int timeStartGrowing;
	
	private double barSpinCycleTime = 460;
	private boolean barGrowingFromFront;
	private float barExtraLength;
	private float progress;
	
	
	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D)g;
		// Bring Java2D up to the present
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// With this set to DEFAULT, stroke geometry will be sloppily approximated; especially visible on animated arcs
		g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
		
		// Based on https://github.com/pnikosis/materialish-progress under Apache License 2.0
		// Exact snippets and commit:
		// - https://github.com/pnikosis/materialish-progress/blob/ef285e08d6a2bf31f594f908fb476891a12f316b/library/src/main/java/com/pnikosis/materialishprogress/ProgressWheel.java#L284-L308
		// - https://github.com/pnikosis/materialish-progress/blob/ef285e08d6a2bf31f594f908fb476891a12f316b/library/src/main/java/com/pnikosis/materialishprogress/ProgressWheel.java#L356-L382
		// (this implementation is *probably* overcomplicated, and material progress is
		//  likely just a combination of sines, but it's an implementation i'm familiar with
		//  and know i like the appearance of.)
		long deltaTime = (System.nanoTime() - lastTimeAnimated) / TimeUnit.MILLISECONDS.toNanos(1);
		float deltaNormalized = deltaTime * spinSpeed / 1000.0f;

		if (pausedTimeWithoutGrowing >= pauseGrowingTime) {
			timeStartGrowing += deltaTime;

			if (timeStartGrowing > barSpinCycleTime) {
				// We completed a size change cycle
				// (growing or shrinking)
				timeStartGrowing -= barSpinCycleTime;
				// if(barGrowingFromFront) {
				pausedTimeWithoutGrowing = 0;
				// }
				barGrowingFromFront = !barGrowingFromFront;
			}

			float distance = (float) Math.cos((timeStartGrowing / barSpinCycleTime + 1) * Math.PI) / 2 + 0.5f;
			float destLength = (barMaxLength - barLength);

			if (barGrowingFromFront) {
				barExtraLength = distance * destLength;
			} else {
				float newLength = destLength * (1 - distance);
				progress += (barExtraLength - newLength);
				barExtraLength = newLength;
			}
		} else {
			pausedTimeWithoutGrowing += deltaTime;
		}

		progress += deltaNormalized;
		if (progress > 360) {
			progress -= 360f;
		}
		lastTimeAnimated = System.nanoTime();

		float from = progress - 90;
		float length = barLength + barExtraLength;

		//canvas.drawArc(circleBounds, from, length, false, barPaint);
		g2d.setStroke(new BasicStroke(4));
		int dia = 40;
//		g2d.setColor(Color.RED);
//		g2d.fillRect((getWidth()-dia)/2, (getHeight()-dia)/2, dia, dia);
		g2d.setColor(getForeground());
		g2d.drawArc(((getWidth()-dia)/2)+2, ((getHeight()-dia)/2)+2, dia-4, dia-4, (int)from, (int)length);
		if (isDisplayable()) {
			Puppet.sched.schedule(() -> repaint(), 15, TimeUnit.MILLISECONDS);
		}
	}
}
