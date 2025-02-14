package com.unascribed.sup;

import java.util.List;

import com.unascribed.sup.data.FlavorGroup;

public interface PuppetDelegate {

	void build();
	
	void setVisible(boolean visible);
	
	void setProgressIndeterminate();
	void setProgressDeterminate();
	void setDone();
	
	void setProgress(int permil);
	
	void setTitle(String title);
	void setSubtitle(String subtitle);
	
	void openChoiceDialog(String name, String title, String body, String[] options, String def);
	void openMessageDialog(String name, String title, String body, AlertMessageType messageType, String[] options, String def);
	void openFlavorDialog(String name, List<FlavorGroup> groups);
	
}
