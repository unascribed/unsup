package com.unascribed.sup;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import com.unascribed.sup.data.ConflictType;
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
					Translate.addTranslation(k.substring(8), translations.get(k));
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
		for (ConflictType c : ConflictType.values()) {
			del.openMessageDialog("xx", "dialog.conflict.title",
					"dialog.conflict.leadin."+c.translationKey+"¤foo/bar.jar¤dialog.conflict.body¤dialog.conflict.aside_trailer",
					AlertMessageType.QUESTION, new String[] {"option.yes_to_all", "option.yes", "option.no_to_all", "option.no", "option.cancel"}, "option.yes");
		}
		del.openFlavorDialog("foobar", Arrays.asList());
		Puppet.startMainThreadRunner();
	}

}
