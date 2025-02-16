package com.unascribed.sup.agent.pieces;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

import com.unascribed.sup.util.Bases;

/**
 * Implements CurseForge's variant of Murmur2, used for the hashes of particularly old files.
 */
public class Murmur2CFMessageDigest extends MessageDigest {
	private static final int M32 = 0x5bd1e995;
	private static final int R32 = 24;
	// algorithm is seeded with the length :/
	private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

	public Murmur2CFMessageDigest() {
		super("Murmur2-CF");
	}

	@Override
	protected void engineUpdate(byte[] input, int offset, int len) {
		for (int i = offset; i < offset+len; i++) {
			engineUpdate(input[i]);
		}
	}

	@Override
	protected void engineUpdate(byte input) {
		if (input == 9 || input == 10 || input == 13 || input == 32) {
			// modification: strips whitespace (?!?!?!?!?!)
			return;
		}
		baos.write(input&0xFF);
	}

	@Override
	protected void engineReset() {
		baos.reset();
	}

	@Override
	protected byte[] engineDigest() {
		byte[] data = baos.toByteArray();
		int length = baos.size();
		
		// Initialize the hash to a random value
		int h = 1 ^ length;

		// Mix 4 bytes at a time into the hash
		final int nblocks = length >> 2;

		// body
		for (int i = 0; i < nblocks; i++) {
			final int index = (i << 2);
			int k = getLittleEndianInt(data, index);
			k *= M32;
			k ^= k >>> R32;
			k *= M32;
			h *= M32;
			h ^= k;
		}

		// Handle the last few bytes of the input array
		final int index = (nblocks << 2);
		switch (length - index) {
			case 3:
				h ^= (data[index + 2] & 0xff) << 16;
			case 2:
				h ^= (data[index + 1] & 0xff) << 8;
			case 1:
				h ^= (data[index] & 0xff);
				h *= M32;
		}

		// Do a few final mixes of the hash to ensure the last few
		// bytes are well-incorporated.
		h ^= h >>> 13;
		h *= M32;
		h ^= h >>> 15;

		return toByteArray(h);
	}

	private static byte[] toByteArray(int h) {
		return new byte[] {(byte) (h >> 24), (byte) (h >> 16), (byte) (h >> 8), (byte) h};
	}

	private static int getLittleEndianInt(final byte[] data, final int index) {
		return ((data[index    ] & 0xff)      ) |
		       ((data[index + 1] & 0xff) <<  8) |
		       ((data[index + 2] & 0xff) << 16) |
		       ((data[index + 3] & 0xff) << 24);
	}
	
	public static String decToHex(String dec) {
		return Bases.bytesToHex(toByteArray((int)Long.parseLong(dec)));
	}
	
}