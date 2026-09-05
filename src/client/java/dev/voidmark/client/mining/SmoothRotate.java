package dev.voidmark.client.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Timed ease-in-out look, same path as Noamm9 {@code rotateSmoothly}:
 * cubic ease, shortest-path yaw, lerp pitch, then a GCD snap like mouse steps.
 */
public final class SmoothRotate {
	public record Rotation(float yaw, float pitch) {
	}

	private SmoothRotate() {
	}

	public static float normalizeYaw(float yaw) {
		return Mth.wrapDegrees(yaw);
	}

	public static float normalizePitch(float pitch) {
		return Mth.clamp(pitch, -90f, 90f);
	}

	public static float lerp(float from, float to, float t) {
		return from + (to - from) * t;
	}

	public static float interpolateYaw(float from, float to, float t) {
		return from + normalizeYaw(to - from) * t;
	}

	public static float easeInOutCubic(double progress) {
		double t = Mth.clamp(progress, 0.0, 1.0);
		if (t < 0.5) {
			return (float) (4.0 * t * t * t);
		}
		double p = -2.0 * t + 2.0;
		return (float) (1.0 - (p * p * p) / 2.0);
	}

	public static Rotation to(Vec3 from, Vec3 target) {
		double dx = target.x - from.x;
		double dy = target.y - from.y;
		double dz = target.z - from.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) (-(Mth.atan2(dy, horiz) * Mth.RAD_TO_DEG));
		return new Rotation(normalizeYaw(yaw), normalizePitch(pitch));
	}

	public static Rotation fixRot(Rotation next, Rotation last) {
		double sensitivity = 0.5;
		Minecraft client = Minecraft.getInstance();
		if (client.options != null && client.options.sensitivity() != null) {
			sensitivity = client.options.sensitivity().get();
		}
		double factor = sensitivity * 0.6 + 0.2;
		double gcd = factor * factor * factor * 1.2;
		if (gcd < 1.0E-4) {
			gcd = 1.0E-4;
		}
		float dyaw = normalizeYaw(next.yaw - last.yaw);
		float dpitch = next.pitch - last.pitch;
		dyaw -= (float) (dyaw % gcd);
		dpitch -= (float) (dpitch % gcd);
		return new Rotation(last.yaw + dyaw, normalizePitch(last.pitch + dpitch));
	}

	public static void apply(LocalPlayer player, float yaw, float pitch) {
		yaw = player.getYRot() + normalizeYaw(yaw - player.getYRot());
		pitch = player.getXRot() + normalizePitch(pitch - player.getXRot());
		Rotation fixed = fixRot(new Rotation(yaw, pitch), new Rotation(player.getYRot(), player.getXRot()));
		yaw = fixed.yaw();
		pitch = normalizePitch(fixed.pitch());
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
		player.forceSetRotation(yaw, false, pitch, false);
	}

	public static boolean close(Rotation current, Rotation target, float tolerance) {
		return Math.abs(normalizeYaw(current.yaw - target.yaw)) <= tolerance
			&& Math.abs(current.pitch - target.pitch) <= tolerance;
	}
}
