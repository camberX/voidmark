package dev.voidmark.client.media;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

final class MediaProcesses {
	private MediaProcesses() {
	}

	static String run(String[] command, int timeoutMs) {
		try {
			Process process = new ProcessBuilder(command)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
			String out;
			try (InputStream stream = process.getInputStream()) {
				out = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			}
			if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				return null;
			}
			if (process.exitValue() != 0) {
				return null;
			}
			return out;
		} catch (Exception exception) {
			return null;
		}
	}
}
