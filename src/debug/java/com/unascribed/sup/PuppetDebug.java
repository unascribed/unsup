package com.unascribed.sup;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.unascribed.sup.opengl.GLPuppet;
import com.unascribed.sup.pieces.QDIni;

public class PuppetDebug {

	public static void main(String[] args) {
		ColorChoice.usePrettyDefaults = true;

		URL u = GLPuppet.class.getClassLoader().getResource("com/unascribed/sup/presets/lang/en-US.ini");
		QDIni translations = null;
		try (InputStream in = u.openStream()) {
			translations = QDIni.load("<preset en-US>", in);
		} catch (IOException e) {
		}
		if (translations != null) {
			for (String k : translations.keySet()) {
				if (k.startsWith("strings.")) {
					Puppet.addTranslation(k.substring(8), translations.get(k));
				}
			}
		}
		
		PuppetDelegate del = GLPuppet.start();
		del.build();
		del.setVisible(true);
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		Puppet.startMainThreadRunner();
	}

}
