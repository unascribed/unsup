package com.unascribed.sup.json.manifest;

import java.util.List;

import com.unascribed.sup.HashFunction;
import com.unascribed.sup.json.AbstractManifest;
import com.unascribed.sup.json.ManifestData;

@ManifestData(flavor="update", currentVersion=1)
public class UpdateManifest extends AbstractManifest {

	public HashFunction hash_function;
	public List<Change> changes;
	
	public static class Change {
		public String path;
		public List<String> envs;
		
		public String from_hash;
		public long from_size;
		
		public String to_hash;
		public long to_size;
		
		public String url;
	}
	
	
	
	protected UpdateManifest() {}
	
	public static UpdateManifest create() {
		return init(new UpdateManifest());
	}
}
