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
	private int barMaxLength = 270;
	private final long pauseGrowingTime = 200;
	
	private int pausedTimeWithoutGrowing;
	private int timeStartGrowing;
	
	private double barSpinCycleTime = 460;
	private boolean barGrowingFromFront;
	private float barExtraLength;
	private float progress;
	
	private boolean animateDone;
	private long animateDoneTime;
	private boolean animateDoneDone;
	
	public void animateDone() {
		barMaxLength = 360;
		animateDone = true;
		barGrowingFromFront = true;
	}
	
	
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
		
		int dia = 40;
		
		// Based on https://github.com/pnikosis/materialish-progress under Apache License 2.0
		// Exact snippets and commit:
		// - https://github.com/pnikosis/materialish-progress/blob/ef285e08d6a2bf31f594f908fb476891a12f316b/library/src/main/java/com/pnikosis/materialishprogress/ProgressWheel.java#L284-L308
		// - https://github.com/pnikosis/materialish-progress/blob/ef285e08d6a2bf31f594f908fb476891a12f316b/library/src/main/java/com/pnikosis/materialishprogress/ProgressWheel.java#L356-L382
		// (this implementation is *probably* overcomplicated, and material progress is
		//  likely just a combination of sines, but it's an implementation i'm familiar with
		//  and know i like the appearance of.)
		long deltaTime = (System.nanoTime() - lastTimeAnimated) / TimeUnit.MILLISECONDS.toNanos(1);
		float deltaNormalized = deltaTime * spinSpeed / 1000.0f;
		
		if (animateDoneTime > 0) {
			g2d.setColor(getForeground());
			g2d.fillOval(((getWidth()-dia)/2), ((getHeight()-dia)/2), dia, dia);
			long time = 4000-animateDoneTime;
			g2d.setColor(getBackground());
			if (time > 0) {
				double a = 1-Math.sin((time/4000D)*(Math.PI/2));
				int inset = (int)(8+((dia-8)*a));
				g2d.fillOval(((getWidth()-dia)/2)+(inset/2), ((getHeight()-dia)/2)+(inset/2), dia-inset, dia-inset);
			} else if (!animateDoneDone && time < -1000) {
				animateDoneDone = true;
				System.out.println("doneAnimating");
			}
			// ?????
			int cX = (getWidth()/2)-(dia/15)-1;
			int cY = (getHeight()/2)+(dia/21)+1;
			g2d.setStroke(new BasicStroke(4));
			g2d.drawPolyline(new int[] {
					cX-(dia/5), cX, cX, cX+(dia*2/6)
			}, new int[] {
					cY, cY+(dia/5)-1, cY+(dia/5)-1, cY-(dia/5)
			}, 4);
			animateDoneTime += deltaTime;
			if (isDisplayable()) {
				Puppet.sched.schedule(() -> repaint(), 15, TimeUnit.MILLISECONDS);
			}
			return;
		}

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
		
		if (length >= 359 && animateDone) {
			animateDoneTime = 1;
		}

		//canvas.drawArc(circleBounds, from, length, false, barPaint);
		g2d.setStroke(new BasicStroke(4));
//		g2d.setColor(Color.RED);
//		g2d.fillRect((getWidth()-dia)/2, (getHeight()-dia)/2, dia, dia);
		g2d.setColor(getForeground());
		g2d.drawArc(((getWidth()-dia)/2)+2, ((getHeight()-dia)/2)+2, dia-4, dia-4, (int)from, (int)length);
		if (isDisplayable()) {
			Puppet.sched.schedule(() -> repaint(), 15, TimeUnit.MILLISECONDS);
		}
	}
}
