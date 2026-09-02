package dev.voidmark.client.visual;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/**
 * Vanilla-looking Hypixel kick: dirt background, {@code connect.failed} title,
 * frozen remaining time, Retry, and Back to server list. Time updates on
 * the next reconnect, not while this screen is open.
 */
public final class FakeBanScreen extends Screen {
	private final Screen parent;
	private final ServerData server;
	private final Component reason;

	public FakeBanScreen(Screen parent, ServerData server, Component reason) {
		super(Component.translatable("connect.failed"));
		this.parent = parent;
		this.server = server;
		this.reason = reason;
	}

	@Override
	protected void init() {
		LinearLayout layout = LinearLayout.vertical().spacing(8);
		layout.defaultCellSetting().alignHorizontallyCenter();
		layout.addChild(new StringWidget(getTitle(), font));
		layout.addChild(new MultiLineTextWidget(reason, font)
			.setMaxWidth(Math.max(200, width - 50))
			.setCentered(true));
		layout.addChild(Button.builder(Component.literal("Retry"), button -> retry())
			.width(Button.DEFAULT_WIDTH)
			.build(), settings -> settings.paddingTop(16));
		layout.addChild(Button.builder(Component.translatable("gui.toMenu"), button -> minecraft.setScreen(parent))
			.width(Button.DEFAULT_WIDTH)
			.build());
		layout.arrangeElements();
		FrameLayout.centerInRectangle(layout, getRectangle());
		layout.visitWidgets(this::addRenderableWidget);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private void retry() {
		if (server == null || server.ip == null || server.ip.isBlank()) {
			minecraft.setScreen(parent);
			return;
		}
		ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(server.ip), server, false, null);
	}
}
