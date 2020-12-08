package com.unascribed.sup.json;

import java.util.function.IntPredicate;

import com.unascribed.sup.Creator;
import com.unascribed.sup.IntPredicates;

import blue.endless.jankson.JsonGrammar;

public abstract class AbstractManifest {

	protected transient final String desiredFlavor;
	protected transient final int currentVersion;
	protected transient final IntPredicate acceptedVersions;
	
	public ManifestVersion unsup_manifest;
	public transient int manifest_version;
	
	protected AbstractManifest() {
		ManifestData md = getClass().getAnnotation(ManifestData.class);
		desiredFlavor = md.flavor();
		acceptedVersions = IntPredicates.inRangeInclusive(md.minVersion(), md.maxVersion() == -1 ? md.currentVersion() : md.maxVersion());
		currentVersion = md.currentVersion();
	}
	
	public void validate() {
		if (unsup_manifest == null)
			throw new ReportableException("unsup_manifest key is missing or malformed");
		if (!(desiredFlavor.equals(unsup_manifest.flavor)))
			throw new ReportableException("Manifest is of flavor "+unsup_manifest.flavor+", but we expected "+desiredFlavor);
		if (!acceptedVersions.test(unsup_manifest.version))
			throw new ReportableException("Don't know how to parse "+unsup_manifest+" manifest (unknown version; our current version is "+desiredFlavor+"-"+currentVersion+")");
	}
	
	protected static <T extends AbstractManifest> T init(T manifest) {
		manifest.unsup_manifest = new ManifestVersion(manifest.desiredFlavor, manifest.currentVersion);
		manifest.manifest_version = manifest.currentVersion;
		return manifest;
	}
	
	@Override
	public String toString() {
		return Creator.jkson.toJson(this).toJson(JsonGrammar.JANKSON);
	}
	
}
