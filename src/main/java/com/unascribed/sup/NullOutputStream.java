package com.unascribed.sup;

import java.io.IOException;
import java.io.OutputStream;

class NullOutputStream extends OutputStream {

	public static final NullOutputStream INSTANCE = new NullOutputStream();
	
	private NullOutputStream() {}
	
	@Override
	public void write(int b) throws IOException {

	}

	@Override
	public void write(byte[] b) throws IOException {
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
	}

	@Override
	public void flush() throws IOException {
	}

	@Override
	public void close() throws IOException {
	}
	
	@Override
	public String toString() {
		return "NullOutputStream.INSTANCE";
	}

}
