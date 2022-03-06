package com.unascribed.sup.json.manifest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.unascribed.sup.json.AbstractManifest;
import com.unascribed.sup.json.ManifestData;
import com.unascribed.sup.json.OrderedVersion;

@ManifestData(flavor="root", currentVersion=1)
public class RootManifest extends AbstractManifest {

	public String name;
	public Versions versions = new Versions();
	public CreatorOptions creator = new CreatorOptions();
	public List<Flavor> flavors = new ArrayList<>();
	
	public static class Versions {
		public OrderedVersion current;
		public List<OrderedVersion> history = new ArrayList<>();
	}
	
	public static class CreatorOptions {
		public Set<String> ignore = new HashSet<>();
	}
	
	public static class Flavor {
		public String id;
		public String name;
		public List<String> envs;
	}
	
	
	protected RootManifest() {}
	
	public static RootManifest create() {
		return init(new RootManifest());
	}
	
}
