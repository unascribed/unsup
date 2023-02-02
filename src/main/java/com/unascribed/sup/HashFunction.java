package com.unascribed.sup;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public enum HashFunction {
	@Deprecated MD5("MD5", "MD5", 128, true),
	@Deprecated SHA1("SHA-1", "SHA-1", 160, true),
	SHA2_256("SHA-2 256", "SHA-256", 256, false),
	SHA2_384("SHA-2 384", "SHA-384", 384, false),
	SHA2_512("SHA-2 512", "SHA-512", 512, false),
	SHA2_512_256("SHA-2 512/256", "SHA-512/256", 256, false),
	
	@Deprecated MURMUR2_CF("Murmur2-CF", Murmur2MessageDigest::new, 32, true)
	;
	
	private static final Map<String, HashFunction> BY_NAME = new HashMap<>();
	
	public final String name;
	private final Supplier<MessageDigest> supplier;
	public final int sizeInBits;
	public final int sizeInBytes;
	public final int sizeInHexChars;
	public final boolean insecure;
	public final String emptyHash;
	
	private boolean hasWarned = false;
	
	HashFunction(String name, String alg, int sizeInBits, boolean insecure) {
		this(name, () -> {
			try {
				return MessageDigest.getInstance(alg);
			} catch (NoSuchAlgorithmException e) {
				throw new AssertionError(e);
			}
		}, sizeInBits, insecure);
	}
	
	HashFunction(String name, Supplier<MessageDigest> supplier, int sizeInBits, boolean insecure) {
		this.name = name;
		this.supplier = supplier;
		this.sizeInBits = sizeInBits;
		this.sizeInBytes = (sizeInBits+7)/8;
		this.sizeInHexChars = sizeInBytes*2;
		this.insecure = insecure;
		this.emptyHash = Util.toHexString(createMessageDigest().digest());
	}
	
	public MessageDigest createMessageDigest() {
		return supplier.get();
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public static HashFunction byName(String name) {
		if (!BY_NAME.containsKey(name)) throw new IllegalArgumentException("No hash function with name "+name);
		HashFunction func = BY_NAME.get(name);
		if (func != null && func.insecure && !func.hasWarned) {
			func.hasWarned = true;
			if (Agent.publicKey != null) {
				Agent.log("WARN", "Using insecure hash function "+func+" for a signed manifest! This is a very bad idea!");
			} else {
				Agent.log("WARN", "Using insecure hash function "+func);
			}
		}
		return func;
	}
	
	static {
		for (HashFunction func : values()) {
			BY_NAME.put(func.name, func);
		}
	}
}
