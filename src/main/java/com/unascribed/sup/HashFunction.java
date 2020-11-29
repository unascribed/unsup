package com.unascribed.sup;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

enum HashFunction {
	SHA256("SHA-256", 256),
	SHA384("SHA-384", 384),
	SHA512("SHA-512", 512),
	;
	
	private final String alg;
	public final int sizeInBits;
	public final int sizeInBytes;
	public final int sizeInHexChars;
	
	private HashFunction(String alg, int sizeInBits) {
		this.alg = alg;
		this.sizeInBits = sizeInBits;
		this.sizeInBytes = (sizeInBits+7)/8;
		this.sizeInHexChars = sizeInBytes*2;
	}
	
	public MessageDigest createMessageDigest() {
		try {
			return MessageDigest.getInstance(alg);
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}
}
