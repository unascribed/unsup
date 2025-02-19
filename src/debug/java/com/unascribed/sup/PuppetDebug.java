package com.unascribed.sup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.unascribed.sup.agent.pieces.QDIni;
import com.unascribed.sup.data.FlavorGroup;
import com.unascribed.sup.puppet.ColorChoice;
import com.unascribed.sup.puppet.Puppet;
import com.unascribed.sup.puppet.PuppetDelegate;
import com.unascribed.sup.puppet.Translate;
import com.unascribed.sup.puppet.opengl.GLPuppet;

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
		Puppet.sched.execute(() -> {
		del.build();
		del.setTitle("Bootstrapping…");
		del.setSubtitle("Downloading mods/yttr-8.20.737.jar, config/emi.css");
		del.setProgressDeterminate();
		del.setProgress(200);
		del.openMessageDialog("xx", "dialog.update.title", "dialog.update.named¤1.0.3¤1.2.1", AlertMessageType.QUESTION, new String[] {"option.yes", "option.no"}, "option.yes");
//		del.openMessageDialog("xx", "dialog.conflict.title",
//				"dialog.conflict.leadin.local_changed_remote_deleted¤config/waila/waila.json¤dialog.conflict.body¤dialog.conflict.aside_trailer",
//				AlertMessageType.QUESTION, new String[] {"option.yes_to_all", "option.yes", "option.no_to_all", "option.no", "option.cancel"}, "option.yes");
//		for (AlertMessageType amt : AlertMessageType.values()) {
//			del.openMessageDialog("xx", "Test dialog",
//					"This is a test dialog!!!!!!",
//					amt, new String[] {"option.yes", "option.cancel"}, "option.yes");
//		}
		try {
			List<FlavorGroup> li = (List<FlavorGroup>)new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode("rO0ABXNyABNqYXZhLnV0aWwuQXJyYXlMaXN0eIHSHZnHYZ0DAAFJAARzaXpleHAAAAAFdwQAAAAFc3IAI2NvbS51bmFzY3JpYmVkLnN1cC5kYXRhLkZsYXZvckdyb3VwCXYj63P4TaACAAZMAAdjaG9pY2VzdAAQTGphdmEvdXRpbC9MaXN0O0wACWRlZkNob2ljZXQAEkxqYXZhL2xhbmcvU3RyaW5nO0wADWRlZkNob2ljZU5hbWVxAH4ABEwAC2Rlc2NyaXB0aW9ucQB+AARMAAJpZHEAfgAETAAEbmFtZXEAfgAEeHBzcQB+AAAAAAADdwQAAAADc3IAMGNvbS51bmFzY3JpYmVkLnN1cC5kYXRhLkZsYXZvckdyb3VwJEZsYXZvckNob2ljZehVB0kI8sjVAgAEWgADZGVmTAALZGVzY3JpcHRpb25xAH4ABEwAAmlkcQB+AARMAARuYW1lcQB+AAR4cAB0ALVZb3Ugd2FudCB0byBjcmVhdGUgYSBuZXcgd29ybGQgd2l0aCBub3JtYWwgdGVycmFpbiBnZW5lcmF0aW9uLCBvciBqb2luIGEgc2VydmVyIHVzaW5nIHRoaXMgZmxhdm9yLjx1bD48bGk+UG93ZXJDb252ZXJ0ZXJzIGRpc2FibGVkPC9saT48bGk+QWxsIEV4dHJhQmlvbWVzWEwgYmlvbWVzIGVuYWJsZWQ8L2xpPjwvdWw+dAAIc3RhbmRhcmR0AAhTdGFuZGFyZHNxAH4ABwB0AQxZb3Ugd2FudCB0byBsb2FkIGFuIGV4aXN0aW5nIEZUQiBVbHRpbWF0ZSB3b3JsZCBhbmQgY29udGludWUgd2hlcmUgeW91IGxlZnQgb2ZmLCBvciBqb2luIGEgc2VydmVyIHVzaW5nIHRoaXMgZmxhdm9yLjx1bD48bGk+UG93ZXJDb252ZXJ0ZXJzIGVuYWJsZWQ8L2xpPjxsaT5TYW1lIGJpb21lcyBhcyBGVEIgVWx0aW1hdGU8L2xpPjwvdWw+Cgo8Yj5XYXJuaW5nOiBUaGlzIGhhcyBub3QgYmVlbiB0aG9yb3VnaGx5IHRlc3RlZCE8L2I+IEFsd2F5cyBrZWVwIGJhY2t1cHMhdAAKY29tcGF0aWJsZXQACkNvbXBhdGlibGVzcQB+AAcAdAGpWW91IHdhbnQgdG8gY3JlYXRlIGEgbmV3IHdvcmxkIHdpdGggRW1pJ3MgMS40Ljcgc2t5YmxvY2sgY2hhbGxlbmdlLCBvciBqb2luIGEgc2VydmVyIHVzaW5nIHRoaXMgZmxhdm9yLgo8dWw+PGxpPlBvd2VyQ29udmVydGVycyBkaXNhYmxlZDwvbGk+PGxpPkVtaSdzIFNreWJsb2NrIGVuYWJsZWQ8L2xpPjwvdWw+PGI+RW1pJ3Mgc2t5YmxvY2sgY2hhbGxlbmdlIGlzIDxpPnZlcnkgaGFyZDwvaT4uPC9iPiBZb3Ugd2lsbCBuZWVkIGFuIGludGltYXRlIGZhbWlsaWFyaXR5IHdpdGggdGhlIG1vZHMgYW5kIGp1ZGljaW91cyB1c2Ugb2YgTkVJIHRvIGdldCBhbnl3aGVyZS4KCjxpPkhpbnQ6PC9pPiBZb3UgY2FuIGdldCBkcm9uZXMgZnJvbSBTY3JhcGJveGVzLCBhbmQgQWx2ZWFyeSBTd2FybWVyIHByaW5jZXNzZXMgPGk+d2lsbCBub3Q8L2k+IGRpZS50AAhza3libG9ja3QACFNreWJsb2NreHBwdAAnSG93IGRvIHlvdSB3YW50IHRvIHBsYXkgUmV3aW5kIFVwc2lsb24/dAAGZmxhdm9ydAAGRmxhdm9yc3EAfgACc3EAfgAAAAAAAncEAAAAAnNxAH4ABwB0AFhVc2UgQ0MtbGljZW5zZWQgaGFwcHkgY2hpcHR1bmVzIGNob3NlbiBieSB0aGUgUmV3aW5kIHByb2plY3QsIGNyZWF0ZWQgYnkgeHljZSBhbmQgcmFkaXgudAANbXVzaWNfdXBzaWxvbnQAB1Vwc2lsb25zcQB+AAcAdAD6RmVsaXhNb29nJ3Mgbm9zdGFsZ2ljIGNvdmVycyBvZiBDYWxsIE1lIE1heWJlLCBQaXJhdGVzIG9mIHRoZSBDYXJpYmJlYW4sIERpc2NvcmQsIGFuZCBXZSBEaWRuJ3QgU3RhcnQgVGhlIEZpcmUsIHNhbWUgYXMgRlRCIFVsdGltYXRlIGFuZCBWb3hlbEJveC4KCjxpPjxiPlBvdGVudGlhbGx5IGEgY29weXJpZ2h0IGhhemFyZCBkdWUgdG8gcmV1c2VkIG1lbG9kaWVzPC9iPiDigJQgbm90IHJlY29tbWVuZGVkIGZvciBzdHJlYW1lcnMuPC9pPnQAC211c2ljX3ZveGVsdAAHQ2xhc3NpY3hwcHQAHFdoYXQgbWVudSBtdXNpYyBkbyB5b3Ugd2FudD90AAVtdXNpY3QACk1lbnUgTXVzaWNzcQB+AAJzcQB+AAAAAAACdwQAAAACc3EAfgAHAHQAoVVzZSBSZWkncyBNaW5pbWFwLiBUaGlzIHdhcyByZW1vdmVkIGZyb20gRlRCIHBhY2tzIGF0IHRoZSB0aW1lIGR1ZSB0byBpdCBleHBvc2luZyB0aGUgSVAgb2YgcHJpdmF0ZSBzZXJ2ZXJzIGluIFlvdVR1YmUgdmlkZW9zLCBidXQgaW4gZ2VuZXJhbCBpcyBhIHZlcnkgZ29vZCBtb2QudAAMcmVpc19taW5pbWFwdAANUmVpJ3MgTWluaW1hcHNxAH4ABwB0ALZVc2UgVm94ZWxNYXAuIFRoaXMgaXMgdGhlIG1vZCB0aGF0IHdhcyBpbmNsdWRlZCBpbiBGVEIgVWx0aW1hdGUsIGJ1dCBpdCdzIGEgbGl0dGxlIGphbmt5IGFuZCBoYXMgZmV3ZXIgZmVhdHVyZXMgdGhhbiBSZWkncy4KCjxpPlJlcXVpcmVzIExpdGVMb2FkZXIuIE5pdGVMb2FkZXIgd2lsbCBiZSBpbnN0YWxsZWQuPC9pPnQACHZveGVsbWFwdAAIVm94ZWxNYXB4cHB0AB5XaGljaCBtaW5pbWFwIG1vZCBkbyB5b3Ugd2FudD90AAdtaW5pbWFwdAAHTWluaW1hcHNxAH4AAnNxAH4AAAAAAAJ3BAAAAAJzcQB+AAcBdAAEbnVsbHQAC2dyZWd0ZWNoX29udAACT25zcQB+AAcAdAAEbnVsbHQADGdyZWd0ZWNoX29mZnQAA09mZnhwcHQBIkdyZWdUZWNoIGlzIGEgc3ByYXdsaW5nIGFkZG9uIHRvIEluZHVzdHJpYWxDcmFmdDIgd2l0aCBhIGxvdCBvZiBpbnRlcmVzdGluZyBhbmQgcG93ZXJmdWwgbWFjaGluZXJ5LiBJdCBtYWtlcyBzb21lIHN3ZWVwaW5nIGJhbGFuY2UgY2hhbmdlcyB0byBvdGhlciBtb2RzLCBhbmQgd2FzIG5vdCBpbmNsdWRlZCBpbiBVbHRpbWF0ZS4gSG93ZXZlciwgaXQgd2FzIGluY2x1ZGVkIGluIHRoZSBNaW5kQ3JhY2sgUGFjaywgYW5kIHdhcyB0aGUgbWFpbiBkaWZmZXJlbmNlIGJldHdlZW4gdGhhdCBhbmQgVWx0aW1hdGUudAAIZ3JlZ3RlY2h0AAhHcmVnVGVjaHNxAH4AAnNxAH4AAAAAAAJ3BAAAAAJzcQB+AAcBdAAEbnVsbHQADGRhcnRjcmFmdF9vbnQAAk9uc3EAfgAHAHQABG51bGx0AA1kYXJ0Y3JhZnRfb2ZmdAADT2ZmeHBwdACsRGFydENyYWZ0IGlzIGEgcG93ZXJmdWwgY29udGVudCBtb2Qgd2l0aCBhIGxvdCBvZiBmdW5jdGlvbmFsaXR5LiBVcHNpbG9uRml4ZXMgdG9uZXMgZG93biBzb21lIG9mIGl0cyBtb3N0IG92ZXJwb3dlcmVkIHRoaW5ncywgYnV0IGl0J3MgZGl2aXNpdmUgYW5kIHdhcyBub3QgaW4gRlRCIFVsdGltYXRlLnQACWRhcnRjcmFmdHQACURhcnRDcmFmdHg="))).readObject();
			List<FlavorGroup> test = new ArrayList<>();
			test.addAll(li);
			del.openFlavorDialog("foobar", test);
		} catch (ClassNotFoundException e) {
		} catch (IOException e) {
		}
		});
		Puppet.sched.schedule(() -> {
		del.setVisible(true);
		}, 1, TimeUnit.SECONDS);
		Puppet.startMainThreadRunner();
	}

}
