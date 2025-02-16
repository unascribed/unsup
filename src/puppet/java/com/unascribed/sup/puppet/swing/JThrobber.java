package com.unascribed.sup.puppet.swing;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.ThrobberAnim;

public class JThrobber extends JComponent {
	
	private final ScheduledExecutorService sched;
	private final ThrobberAnim anim = new ThrobberAnim();
	private long delayNs = 0;
	
	public JThrobber(ScheduledExecutorService sched) {
		this.sched = sched;
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
		
		if (anim.update()) {
			g2d.setColor(getForeground());
			g2d.fillOval(((getWidth()-dia)/2), ((getHeight()-dia)/2), dia, dia);
			long time = 4000-anim.animateDoneTime;
			g2d.setColor(getBackground());
			if (time > 0) {
				double a = 1-Math.sin((time/4000D)*(Math.PI/2));
				int inset = (int)(8+((dia-8)*a));
				g2d.fillOval(((getWidth()-dia)/2)+(inset/2), ((getHeight()-dia)/2)+(inset/2), dia-inset, dia-inset);
			} else if (time < -1000) {
				Puppet.reportDone();
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
		} else {
			float from = anim.progress - 90;
			float length = anim.barMinLength + anim.barExtraLength;
			
			g2d.setStroke(new BasicStroke(4));
			g2d.setColor(getForeground());
			g2d.drawArc(((getWidth()-dia)/2)+2, ((getHeight()-dia)/2)+2, dia-4, dia-4, (int)from, (int)length);
		}
		if (isDisplayable()) {
			if (delayNs == 0) {
				int hz = 0;
				try {
					for (GraphicsDevice dev : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
						hz = Math.max(hz, dev.getDisplayMode().getRefreshRate());
					}
				} catch (Throwable t) {}
				if (hz < 30) hz = 30;
				// Swing can only run so hard
				if (hz > 240) hz = 240;
				delayNs = TimeUnit.SECONDS.toNanos(1)/hz;
			}
			sched.schedule(() -> SwingUtilities.invokeLater(() -> repaint()), delayNs, TimeUnit.NANOSECONDS);
		}
	}
	
	public void animateDone() {
		anim.animateDone();
	}
}
