package com.unascribed.sup.json.manifest;

import java.util.ArrayList;
import java.util.List;

import com.unascribed.sup.Creator;
import com.unascribed.sup.HashFunction;
import com.unascribed.sup.json.AbstractManifest;
import com.unascribed.sup.json.ManifestData;
import com.unascribed.sup.json.OneLineJsonArray;
import com.unascribed.sup.json.OrderedVersion;
import com.unascribed.sup.json.SkipNullsMarshallable;

import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.Marshaller;

@ManifestData(flavor="bootstrap", currentVersion=1)
public class BootstrapManifest extends AbstractManifest {

	public OrderedVersion version;
	public HashFunction hash_function;
	public List<BootstrapFile> files = new ArrayList<>();
	
	public static class BootstrapFile implements SkipNullsMarshallable {
		public String path;
		public List<String> envs;
		public List<String> flavors;
		
		public String hash;
		public long size;
		
		public String url;
		
		@Override
		public JsonElement serialize(Marshaller m) {
			JsonObject obj = (JsonObject)SkipNullsMarshallable.super.serialize(m);
			if (obj.containsKey("envs")) obj.put("envs", Creator.copyInto(new OneLineJsonArray(), obj.get("envs")));
			if (obj.containsKey("flavors")) obj.put("flavors", Creator.copyInto(new OneLineJsonArray(), obj.get("flavors")));
			return obj;
		}
	}
	
	
	
	protected BootstrapManifest() {}
	
	public static BootstrapManifest create() {
		return init(new BootstrapManifest());
	}
	
}
