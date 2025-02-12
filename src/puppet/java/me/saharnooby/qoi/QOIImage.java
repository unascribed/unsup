package me.saharnooby.qoi;

import javax.annotation.NotNull;

/**
 * A bundle of QOI image metadata and raw pixel data.
 */
public final class QOIImage {

	/**
	 * Image width. Positive value.
	 */
	private final int width;
	/**
	 * Image height. Positive value.
	 */
	private final int height;
	/**
	 * Channel count. Supported values are 3 (no alpha) and 4 (with alpha).
	 */
	private final int channels;
	/**
	 * Color space of the image.
	 */
	@NotNull
	private final QOIColorSpace colorSpace;
	/**
	 * Raw pixel data in the form of [R, G, B, (A,) ...].
	 * The array has (width * height * channels) elements.
	 * Alpha is present when channel count is 4.
	 */
	private final byte @NotNull [] pixelData;
	
	public QOIImage(int width, int height, int channels, QOIColorSpace colorSpace, byte @NotNull [] pixelData) {
		this.width = width;
		this.height = height;
		this.channels = channels;
		this.colorSpace = colorSpace;
		this.pixelData = pixelData;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getChannels() {
		return channels;
	}

	public QOIColorSpace getColorSpace() {
		return colorSpace;
	}

	public byte[] getPixelData() {
		return pixelData;
	}

}
