package com.unascribed.sup;

import java.io.IOException;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess.Export;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.SyntaxError;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class Rclone {

	public static void main(String[] args) throws IOException {
		Context ctx = Context.create("wasm");
		ctx.eval(Source.newBuilder("wasm", ClassLoader.getSystemResource("rclone.wasm")).build());
		System.out.println(ctx.getBindings("wasm").getMember("main").getMember("run").execute());
	}
	
	public static class JSON {
		
		private final Jankson jkson = Jankson.builder().build();
		
		@Export
		public Value parse(String json) {
			try {
				return Value.asValue(jkson.fromJson(json, JsonObject.class));
			} catch (SyntaxError e) {
				throw new IllegalArgumentException(e);
			}
		}
		
		@Export
		public String stringify(Map<String, Object> map) {
			return jkson.toJson(map).toJson();
		}
	}
	
}
