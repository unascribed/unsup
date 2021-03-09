package com.unascribed.sup;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public enum HashFunction {
	@Deprecated MD5("MD5", "MD5", 128, true),
	@Deprecated SHA1("SHA-1", "SHA-1", 160, true),
	SHA2_256("SHA-2 256", "SHA-256", 256, false),
	SHA2_384("SHA-2 384", "SHA-384", 384, false),
	SHA2_512("SHA-2 512", "SHA-512", 512, false),
	;
	
	private static final Map<String, HashFunction> BY_NAME = new HashMap<>();
	
	public final String name;
	private final String alg;
	public final int sizeInBits;
	public final int sizeInBytes;
	public final int sizeInHexChars;
	public final boolean insecure;
	public final String emptyHash;
	
	HashFunction(String name, String alg, int sizeInBits, boolean insecure) {
		this.name = name;
		this.alg = alg;
		this.sizeInBits = sizeInBits;
		this.sizeInBytes = (sizeInBits+7)/8;
		this.sizeInHexChars = sizeInBytes*2;
		this.insecure = insecure;
		this.emptyHash = Util.toHexString(createMessageDigest().digest());
	}
	
	public MessageDigest createMessageDigest() {
		try {
			return MessageDigest.getInstance(alg);
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public static HashFunction byName(String name) {
		if (!BY_NAME.containsKey(name)) throw new IllegalArgumentException("No hash function with name "+name);
		return BY_NAME.get(name);
	}
	
	static {
		for (HashFunction func : values()) {
			BY_NAME.put(func.name, func);
		}
	}
}
