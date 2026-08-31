package dev.voidmark.client.ui;

import dev.voidmark.client.config.VoidmarkConfig;

public final class Anim {
	private Anim() {
	}

	public static float exp(float current, float target, float speed, float dt) {
		if (!VoidmarkConfig.get().uiAnimations) {
			return target;
		}
		if (Math.abs(target - current) < 0.003f) {
			return target;
		}
		return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
	}

	public static int fade(int color, float t) {
		int a = (color >>> 24) & 0xFF;
		int na = Math.max(0, Math.min(255, Math.round(a * t)));
		return (na << 24) | (color & 0x00FFFFFF);
	}
}
