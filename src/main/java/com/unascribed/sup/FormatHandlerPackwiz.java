package com.unascribed.sup;

import java.io.IOException;
import java.net.URL;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.moandjiezana.toml.Toml;
import com.unascribed.flexver.FlexVerComparator;
import com.unascribed.sup.PuppetHandler.AlertMessageType;
import com.unascribed.sup.PuppetHandler.AlertOption;
import com.unascribed.sup.PuppetHandler.AlertOptionType;

public class FormatHandlerPackwiz extends FormatHandler {
	
	static CheckResult check(URL src) throws IOException {
		Agent.log("INFO", "Loading packwiz-format manifest from "+src);
		Version ourVersion = Version.fromJson(Agent.state.getObject("current_version"));
		Toml pack = IOHelper.loadToml(src, 4*K, new URL(src, "unsup.sig"));
		String fmt = pack.getString("pack-format");
		if (!fmt.startsWith("packwiz:") || FlexVerComparator.compare("packwiz:1.1.0", fmt) < 0)
			throw new IOException("Cannot read unknown pack-format "+fmt);
		JsonObject pwstate = Agent.state.getObject("packwiz");
		if (pwstate == null) {
			pwstate = new JsonObject();
			Agent.state.put("packwiz", pwstate);
		}
		Toml indexMeta = pack.getTable("index");
		if (indexMeta == null)
			throw new IOException("Malformed pack.toml: [index] table is missing");
		String indexDoublet = indexMeta.getString("hash-format")+":"+indexMeta.getString("hash");
		if (!indexDoublet.equals(pwstate.getString("lastIndexHash"))) {
			if (ourVersion == null) {
				ourVersion = new Version("null", 0);
			}
			Version theirVersion = new Version(pack.getString("version"), ourVersion.code+1);
			JsonObject newState = new JsonObject(Agent.state);
			pwstate = new JsonObject(pwstate);
			newState.put("packwiz", pwstate);
			Agent.log("INFO", "Update available - our index state is "+pwstate.getString("lastIndexHash")+", theirs is "+indexDoublet);
			String interlude = " from "+ourVersion.name+" to "+theirVersion.name;
			if (ourVersion.name.equals(theirVersion.name)) {
				interlude = "";
			}
			boolean bootstrapping = !pwstate.containsKey("lastIndexHash");
			if (!bootstrapping) {
				AlertOption updateResp = PuppetHandler.openAlert("Update available",
						"<b>An update"+interlude+" is available!</b><br/>Do you want to install it?",
						AlertMessageType.QUESTION, AlertOptionType.YES_NO, AlertOption.YES);
				if (updateResp == AlertOption.NO) {
					Agent.log("INFO", "Ignoring update by user choice.");
					return new CheckResult(ourVersion, theirVersion, null);
				}
			}
			PuppetHandler.updateTitle(bootstrapping ? "Bootstrapping..." : "Updating...", false);
			PuppetHandler.updateSubtitle("Calculating update");
			Toml index = IOHelper.loadToml(new URL(src, indexMeta.getString("file")), 64*K,
					parseFunc(indexMeta.getString("hash-format")), indexMeta.getString("hash"));
			UpdatePlan<FileToDownload> plan = new UpdatePlan<>(bootstrapping, ourVersion.name, theirVersion.name, newState);
			JsonObject lastState = pwstate.getObject("lastState");
			if (lastState != null && lastState.getArray("files") != null) {
				HashFunction func = HashFunction.byName(lastState.getString("func"));
				for (Object ele : lastState.getArray("files")) {
					if (ele instanceof JsonObject) {
						JsonObject obj = (JsonObject)ele;
						plan.expectedState.put(obj.getString("path"), new FileState(func, obj.getString("hash"), -1));
					}
				}
			}
			JsonObject metafileState = pwstate.getObject("metafileState");
			if (metafileState == null) {
				metafileState = new JsonObject();
				pwstate.put("metafileState", metafileState);
			}
			HashFunction func = parseFunc(index.getString("hash-format"));
			int metafileCount = 0;
			for (Toml file : index.getTables("files")) {
				if (file.getBoolean("metafile", false)) {
					metafileCount++;
				} else {
					String path = file.getString("file");
					String hash = file.getString("hash");
					FileToDownload f = new FileToDownload();
					f.state = new FileState(func, hash, -1);
					f.url = new URL(src, path);
					if (!plan.expectedState.containsKey(path)) {
						plan.expectedState.put(path, FileState.EMPTY);
					} else if (plan.expectedState.get(path).equals(f.state)) {
						continue;
					}
					plan.files.put(path, f);
				}
			}
			int metafilesRetrieved = 0;
			PuppetHandler.updateSubtitle("Retrieving metafiles");
			PuppetHandler.updateTitle(bootstrapping ? "Bootstrapping..." : "Updating...", true);
			for (Toml file : index.getTables("files")) {
				String path = file.getString("file");
				String hash = file.getString("hash");
				if (path == null || hash == null) continue;
				if (file.getBoolean("metafile", false)) {
					String metafileDoublet = (func+":"+hash);
					if (metafileDoublet.equals(metafileState.getString(path)))
						continue;
					Agent.log("INFO", "Retrieving metafile for "+path);
					PuppetHandler.updateSubtitle("Retrieving metafile "+path);
					Toml metafile = IOHelper.loadToml(new URL(src, path), 8*K, func, hash);
					metafilesRetrieved++;
					PuppetHandler.updateProgress((metafilesRetrieved*1000)/metafileCount);
					String side = metafile.getString("side");
					metafileState.put(path, metafileDoublet);
					if (side != null && !side.equals("both") && !side.equals(Agent.detectedEnv)) {
						Agent.log("INFO", "Skipping "+path+" as it's not eligible for env "+Agent.detectedEnv);
						continue;
					}
					path = path.replace("\\", "/");
					String pfx;
					int slash = path.lastIndexOf('/');
					if (slash >= 0) {
						pfx = path.substring(0, slash+1);
					} else {
						pfx = "";
					}
					path = pfx+metafile.getString("filename");
					Toml download = metafile.getTable("download");
					FileToDownload f = new FileToDownload();
					HashFunction thisFunc = parseFunc(download.getString("hash-format"));
					String thisHash = download.getString("hash");
					if (thisFunc == HashFunction.MURMUR2_CF) {
						thisHash = Murmur2MessageDigest.decToHex(thisHash);
					}
					f.state = new FileState(thisFunc, thisHash, -1);
					if (!plan.expectedState.containsKey(path)) {
						plan.expectedState.put(path, FileState.EMPTY);
					} else if (plan.expectedState.get(path).equals(f.state)) {
						continue;
					}
					String url = download.getString("url");
					if (url != null) {
						f.url = new URL(url);
					} else {
						String mode = download.getString("mode");
						if (Util.b64Str("bWV0YWRhdGE6Y3Vyc2Vmb3JnZQ==").equals(mode)) {
							// Not a virus. Trust me, I'm a dolphin
							Toml tbl = metafile.getTable(Util.b64Str("dXBkYXRlLmN1cnNlZm9yZ2U="));
							f.hostile = true;
							String str = Long.toString(tbl.getLong(Util.b64Str("ZmlsZS1pZA==")));
							int i = (str.length()+1)/2;
							String l = str.substring(0, i);
							String r = str.substring(i);
							while (r.startsWith("0")) r = r.substring(1);
							f.url = new URL(String.format(Util.b64Str("aHR0cHM6Ly9tZWRpYWZpbGV6LmZvcmdlY2RuLm5ldC9maWxlcy8lcy8lcy8lcw=="),
									l, r, metafile.getString(Util.b64Str("ZmlsZW5hbWU=")).replace("+", "%2B")));
						} else {
							throw new IOException("Cannot update "+path+" - unrecognized download mode "+mode);
						}
					}
					plan.files.put(path, f);
				}
			}
			lastState = new JsonObject();
			lastState.put("func", func.name);
			JsonArray files = new JsonArray();
			lastState.put("files", files);
			
			return new CheckResult(ourVersion, theirVersion, plan);
		}
		return new CheckResult(ourVersion, ourVersion, null);
	}
	
	@SuppressWarnings("deprecation")
	static HashFunction parseFunc(String str) {
		switch (str) {
			case "md5": return HashFunction.MD5;
			case "sha1": return HashFunction.SHA1;
			case "sha256": return HashFunction.SHA2_256;
			case "sha384": return HashFunction.SHA2_384;
			case "sha512": return HashFunction.SHA2_512;
			case "murmur2": return HashFunction.MURMUR2_CF;
			default: throw new IllegalArgumentException("Unknown packwiz hash function "+str);
		}
	}
	
}
