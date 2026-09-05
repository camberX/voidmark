package dev.voidmark.client.farming;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmark.client.ui.Theme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

/**
 * Temporary farming controls: swap Attack/Destroy with Jump and latch the
 * swapped Attack/Destroy binding until it is pressed again.
 */
public final class FarmKeys {
	private static InputConstants.Key attackKey;
	private static InputConstants.Key jumpKey;
	private static double sensitivity;
	private static boolean enabled;
	private static boolean attackLatched;
	private static boolean physicalWasDown;

	private FarmKeys() {
	}

	public static int toggle() {
		Minecraft client = Minecraft.getInstance();
		if (enabled) {
			disable(client, true);
		} else {
			enable(client);
		}
		return 1;
	}

	public static void tick(Minecraft client) {
		if (!enabled || client == null || attackKey == null || client.getWindow() == null) {
			return;
		}
		boolean physicalDown = physicalDown(client, jumpKey);
		if (client.screen == null && physicalDown && !physicalWasDown) {
			attackLatched = !attackLatched;
		}
		physicalWasDown = physicalDown;
		client.options.keyAttack.setDown(attackLatched);
	}

	public static void restore() {
		if (enabled) {
			disable(Minecraft.getInstance(), false);
		}
	}

	private static void enable(Minecraft client) {
		attackKey = InputConstants.getKey(client.options.keyAttack.saveString());
		jumpKey = InputConstants.getKey(client.options.keyJump.saveString());
		sensitivity = client.options.sensitivity().get();
		attackLatched = false;
		physicalWasDown = physicalDown(client, jumpKey);

		client.options.keyAttack.setDown(false);
		client.options.keyJump.setDown(false);
		client.options.keyAttack.setKey(jumpKey);
		client.options.keyJump.setKey(attackKey);
		client.options.sensitivity().set(0.0);
		KeyMapping.resetMapping();
		enabled = true;
		message(client, "Farm keys enabled · controls swapped · attack toggles · sensitivity minimum");
	}

	private static void disable(Minecraft client, boolean notify) {
		client.options.keyAttack.setDown(false);
		client.options.keyJump.setDown(false);
		client.options.keyAttack.setKey(attackKey);
		client.options.keyJump.setKey(jumpKey);
		client.options.sensitivity().set(sensitivity);
		KeyMapping.resetMapping();
		client.options.save();

		enabled = false;
		attackLatched = false;
		physicalWasDown = false;
		attackKey = null;
		jumpKey = null;
		if (notify) {
			message(client, "Farm keys disabled · controls and sensitivity restored");
		}
	}

	private static boolean physicalDown(Minecraft client, InputConstants.Key key) {
		if (key == null || key.equals(InputConstants.UNKNOWN)) {
			return false;
		}
		return switch (key.getType()) {
			case KEYSYM -> InputConstants.isKeyDown(client.getWindow(), key.getValue());
			case MOUSE -> GLFW.glfwGetMouseButton(client.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
			default -> false;
		};
	}

	private static void message(Minecraft client, String text) {
		if (client.gui != null) {
			MutableComponent line = Component.literal("VOIDMARK")
				.withStyle(style(Theme.ACCENT).withBold(true))
				.append(Component.literal(" | ").withStyle(style(Theme.MUTED)))
				.append(Component.literal(text).withStyle(style(Theme.TEXT)));
			client.gui.getChat().addClientSystemMessage(line);
		}
	}

	private static Style style(int color) {
		return Style.EMPTY.withColor(color & 0xFFFFFF);
	}
}
