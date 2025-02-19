package com.unascribed.sup.puppet.opengl;

import org.lwjgl.system.JNI;

import com.unascribed.sup.puppet.Puppet;

public class ABRTHandler {

	public static void install() {
		sun.misc.Signal.handle(new sun.misc.Signal("ABRT"), sig -> {
			Puppet.log("ERROR", "SIGABRT caught! Doing something bogus to cause a different native crash Hotspot will catch!");
			JNI.callJJ(0xBAADF00DBAADF00DL, 0xDEADBEEFDEADBEEFL);
		});
	}

}
