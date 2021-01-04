package com.unascribed.sup.json.manifest;

import java.util.ArrayList;
import java.util.List;

import com.unascribed.sup.json.AbstractManifest;
import com.unascribed.sup.json.ManifestData;
import com.unascribed.sup.json.OrderedVersion;

@ManifestData(flavor="root", currentVersion=1)
public class RootManifest extends AbstractManifest {

	public String name;
	public Versions versions = new Versions();
	public CreatorOptions creator = new CreatorOptions();
	
	public static class Versions {
		public OrderedVersion current;
		public List<OrderedVersion> history = new ArrayList<>();
	}
	
	public static class CreatorOptions {
		public List<String> ignore = new ArrayList<>();
	}
	
	
	
	protected RootManifest() {}
	
	public static RootManifest create() {
		return init(new RootManifest());
	}
	
}
