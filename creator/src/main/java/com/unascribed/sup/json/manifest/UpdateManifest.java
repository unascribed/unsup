package com.unascribed.sup.json.manifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.unascribed.sup.Creator;
import com.unascribed.sup.data.HashFunction;
import com.unascribed.sup.json.AbstractManifest;
import com.unascribed.sup.json.ManifestData;
import com.unascribed.sup.json.OneLineJsonArray;
import com.unascribed.sup.json.SkipNullsMarshallable;

import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.Marshaller;

@ManifestData(flavor="update", currentVersion=1)
public class UpdateManifest extends AbstractManifest {

	public transient boolean dirty = false;
	
	public HashFunction hash_function;
	public List<Change> changes = new ArrayList<>();
	public boolean published;
	
	public static class Change implements SkipNullsMarshallable {
		public String path;
		public List<String> envs;
		public List<String> flavors;
		
		public String from_hash;
		public long from_size;
		
		public String to_hash;
		public long to_size;
		
		public String url;
		
		@Override
		public JsonElement serialize(Marshaller m) {
			JsonObject obj = (JsonObject)SkipNullsMarshallable.super.serialize(m);
			if (obj.containsKey("envs")) obj.put("envs", Creator.copyInto(new OneLineJsonArray(), obj.get("envs")));
			if (obj.containsKey("flavors")) obj.put("flavors", Creator.copyInto(new OneLineJsonArray(), obj.get("flavors")));
			return obj;
		}

		public boolean isUseless() {
			return Objects.equals(from_hash, to_hash) && from_size == to_size;
		}
	}
	
	
	
	protected UpdateManifest() {}
	
	public static UpdateManifest create() {
		return init(new UpdateManifest());
	}
}
