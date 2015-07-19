package com.alto.mover.backgroundservice;

interface BackgroundServiceListener {
	void handleUpdate();
	String getUniqueID();
}
