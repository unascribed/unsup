package com.unascribed.sup.json.manifest;

import java.util.ArrayList;
import java.util.List;

import com.unascribed.sup.HashFunction;
import com.unascribed.sup.json.AbstractManifest;
import com.unascribed.sup.json.ManifestData;
import com.unascribed.sup.json.OrderedVersion;

@ManifestData(flavor="bootstrap", currentVersion=1)
public class BootstrapManifest extends AbstractManifest {

	public OrderedVersion version;
	public HashFunction hash_function;
	public List<BootstrapFile> files = new ArrayList<>();
	
	public static class BootstrapFile {
		public String path;
		public List<String> envs;
		
		public String hash;
		public long size;
		
		public String url;
	}
	
	
	
	protected BootstrapManifest() {}
	
	public static BootstrapManifest create() {
		return init(new BootstrapManifest());
	}
	
}
