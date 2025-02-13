package com.unascribed.sup;

import java.io.IOException;
import java.io.InputStream;

import org.brotli.dec.BrotliInputStream;

import com.unascribed.sup.opengl.GLPuppet;

import me.saharnooby.qoi.QOIColorSpace;
import me.saharnooby.qoi.QOIDecoder;
import me.saharnooby.qoi.QOIImage;

public class WindowIcons {

	public static final QOIImage lowres = load("unsup-16");
	public static final QOIImage highres = load("unsup");

	private static QOIImage load(String name) {
		try (InputStream in = new BrotliInputStream(GLPuppet.class.getClassLoader().getResourceAsStream("com/unascribed/sup/assets/"+name+".qoi.br"))) {
			return QOIDecoder.decode(in, 4);
		} catch (IOException | NullPointerException e) {
			Puppet.log("ERROR", "Failed to load "+name+".qoi", e);
			return new QOIImage(1, 1, 4, QOIColorSpace.LINEAR, new byte[4]);
		}
	}
	
}
