package com.unascribed.sup.agent.signing;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Base64;

import com.unascribed.sup.util.Bases;

import net.i2p.crypto.eddsa.EdDSAPublicKey;

public class SignifyProvider implements SigProvider {

	static final byte[] Ed = { 'E', 'd' };
	
	private final long keyId;
	private final SigProvider inner;
	private final byte[] expectedFormat;
	
	public SignifyProvider(long keyId, EdDSAPublicKey key) throws InvalidKeyException {
		this.expectedFormat = Ed;
		this.keyId = keyId;
		this.inner = new RawEdDSAProvider(key);
	}
	
	@Override
	public boolean verify(byte[] data, byte[] signature) throws SignatureException {
		signature = Base64.getDecoder().decode(new String(signature, StandardCharsets.ISO_8859_1).split("\n")[1].trim());
		if (!checkFormat(signature, expectedFormat)) {
			throw new SignatureException("Signature is not in a known format");
		}
		long keyId = toLong(signature, expectedFormat.length);
		if (keyId != this.keyId) {
			throw new SignatureException("Data is signed with the wrong key (expected "+Bases.longToHex(this.keyId)+" but got "+Bases.longToHex(this.keyId)+")");
		}
		signature = Arrays.copyOfRange(signature, expectedFormat.length+8, signature.length);
		return inner.verify(data, signature);
	}

	public static long toLong(byte[] data, int ofs) {
		return
			  (data[ofs+0] & 0xFFL) << 56
			| (data[ofs+1] & 0xFFL) << 48
			| (data[ofs+2] & 0xFFL) << 40
			| (data[ofs+3] & 0xFFL) << 32
			| (data[ofs+4] & 0xFFL) << 24
			| (data[ofs+5] & 0xFFL) << 16
			| (data[ofs+6] & 0xFFL) << 8
			| (data[ofs+7] & 0xFFL);
	}
	
	static boolean checkFormat(byte[] data, byte[] format) {
		for (int i = 0; i < format.length; i++) {
			if (i >= data.length || data[i] != format[i]) {
				return false;
			}
		}
		return true;
	}
	
}
