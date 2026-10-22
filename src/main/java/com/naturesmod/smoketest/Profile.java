package com.naturesmod.smoketest;

import java.nio.file.Path;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import com.naturesmod.NaturesMod;

/**
 * A CPU profile of the smoke test, recorded with Java Flight Recorder inside the server itself
 * (the test ends by halting the server, which a recording started from the command line would not
 * survive). CI summarises it to show what the mod costs: .github/profile/hotspots.py.
 */
final class Profile {
	private static Recording recording;

	private Profile() {
	}

	static void start() {
		try {
			recording = new Recording(Configuration.getConfiguration("profile"));
			recording.start();
		} catch (Exception | LinkageError e) {
			NaturesMod.LOGGER.warn("Could not start the smoke test's CPU profile: {}", e.toString());
		}
	}

	static void save() {
		if (recording == null) {
			return;
		}
		try {
			recording.dump(Path.of("profile.jfr"));
			recording.close();
		} catch (Exception e) {
			NaturesMod.LOGGER.warn("Could not save the smoke test's CPU profile: {}", e.toString());
		}
		recording = null;
	}
}
