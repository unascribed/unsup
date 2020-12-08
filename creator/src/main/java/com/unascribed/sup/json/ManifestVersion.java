package com.unascribed.sup.json;

public class ManifestVersion {

	public final String flavor;
	public final int version;
	
	public ManifestVersion(String flavor, int version) {
		this.flavor = flavor;
		this.version = version;
	}

	@Override
	public String toString() {
		return flavor+"-"+version;
	}
	
	public static ManifestVersion parse(String str) {
		int dash = str.indexOf('-');
		if (dash == -1) throw new IllegalArgumentException("unsup_manifest value does not contain a dash");
		String lhs = str.substring(0, dash);
		String rhs = str.substring(dash+1);
		int rhsI;
		try {
			rhsI = Integer.parseInt(rhs);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("unsup_manifest value right-hand side is not a number: "+rhs);
		}
		return new ManifestVersion(lhs, rhsI);
	}
	
}
