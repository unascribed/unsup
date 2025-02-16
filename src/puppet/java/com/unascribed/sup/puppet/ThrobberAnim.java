package com.unascribed.sup.puppet;

import java.util.concurrent.TimeUnit;

public class ThrobberAnim {

	public long lastTimeAnimated = System.nanoTime();
	
	public final float spinSpeed = 230f;
	public int barMinLength = 16;
	public int barMaxLength = 270;
	public final long pauseGrowingTime = 200;
	
	public int pausedTimeWithoutGrowing;
	public int timeStartGrowing;
	
	public double barSpinCycleTime = 460;
	public boolean barGrowingFromFront;
	public float barExtraLength;
	public float progress;
	
	public boolean animateDone;
	public long animateDoneTime;
	
	public void animateDone() {
		barMaxLength = 360;
		animateDoneTime = 0;
		animateDone = true;
		barGrowingFromFront = true;
	}

	public boolean update() {
		// Based on https://github.com/pnikosis/materialish-progress under Apache License 2.0
		// Exact snippets and commit:
		// - https://github.com/pnikosis/materialish-progress/blob/ef285e08d6a2bf31f594f908fb476891a12f316b/library/src/main/java/com/pnikosis/materialishprogress/ProgressWheel.java#L284-L308
		// - https://github.com/pnikosis/materialish-progress/blob/ef285e08d6a2bf31f594f908fb476891a12f316b/library/src/main/java/com/pnikosis/materialishprogress/ProgressWheel.java#L356-L382
		// (this implementation is *probably* overcomplicated, and material progress is
		//  likely just a combination of sines, but it's an implementation i'm familiar with
		//  and know i like the appearance of.)
		double deltaTime = (System.nanoTime() - lastTimeAnimated) / (double)TimeUnit.MILLISECONDS.toNanos(1);
		double deltaNormalized = deltaTime * spinSpeed / 1000.0f;
		
		if (animateDoneTime > 0) {
			animateDoneTime += deltaTime;
			return true;
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
			float destLength = (barMaxLength - barMinLength);

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

		float length = barMinLength + barExtraLength;
		
		if (length >= 359 && animateDone) {
			animateDoneTime = 1;
		}
		return false;
	}
	
}
