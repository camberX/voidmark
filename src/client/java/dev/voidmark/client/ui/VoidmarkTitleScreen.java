package dev.voidmark.client.ui;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.voidmark.client.render.GuiDraw;
import dev.voidmark.client.render.Starfield;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Musics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Voidmark title screen: dark starfield, tall pane buttons, no dirt background.
 */
public class VoidmarkTitleScreen extends Screen {
	private static final float BUTTON_W = 252;
	private static final float BUTTON_H = 32;
	private static final float BUTTON_GAP = 8;
	private static final float SPLIT_GAP = 8;

	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Float> hovers = new HashMap<>();
	private long lastNs = System.nanoTime();
	private float dt = 0.016f;
	private float appear;
	private String status = "";

	public VoidmarkTitleScreen() {
		super(Component.translatable("narrator.screen.title"));
	}

	@Override
	protected void init() {
		Theme.refresh();
		appear = 0f;
		status = "";
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public net.minecraft.sounds.Music getBackgroundMusic() {
		return Musics.MENU;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int top = 0xFF05070D;
		int bot = 0xFF000000 | Theme.mix(0x0B0E14, Theme.ACCENT & 0xFFFFFF, 0.06f);
		GuiDraw.fillGradient(graphics, 0, 0, width, height, top, bot);
		try {
			Starfield.drawSky(graphics, width, height);
		} catch (Throwable ignored) {
		}
		GuiDraw.fill(graphics, 0, 0, width, 48, 0x66000000);
		GuiDraw.fill(graphics, 0, height - 36, width, 36, 0x88000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		tickAnim();
		hits.clear();
		Font font = minecraft.font;
		float fade = appear;

		float colW = BUTTON_W;
		float colX = (width - colW) * 0.5f;
		float stackH = BUTTON_H * 3 + BUTTON_GAP * 2 + 14 + BUTTON_H;
		float colY = Mth.clamp((height - stackH) * 0.42f, 56, height - stackH - 48);

		GuiDraw.title(graphics, font, "VOIDMARK", colX, colY - 28, Anim.fade(Theme.TEXT, fade));
		GuiDraw.rounded(graphics, colX, colY - 14, 18, 2, 1, Anim.fade(Theme.ACCENT, fade));
		String ver = "v" + modVersion();
		GuiDraw.small(graphics, font, ver, colX + 22, colY - 16, Anim.fade(Theme.ACCENT, fade));

		float y = colY;
		y = button(graphics, font, mouseX, mouseY, colX, y, colW, "Singleplayer", true, fade, this::openSingleplayer);
		y = button(graphics, font, mouseX, mouseY, colX, y, colW, "Multiplayer", multiplayerOpen(), fade, this::openMultiplayer);
		y = button(graphics, font, mouseX, mouseY, colX, y, colW, "Realms", multiplayerOpen(), fade, this::openRealms);
		y += 6;
		float half = (colW - SPLIT_GAP) * 0.5f;
		button(graphics, font, mouseX, mouseY, colX, y, half, "Options", true, fade, this::openOptions);
		button(graphics, font, mouseX, mouseY, colX + half + SPLIT_GAP, y, half, "Quit", true, fade, this::quit);

		String user = minecraft.getUser() == null ? "" : minecraft.getUser().getName();
		if (!user.isBlank()) {
			GuiDraw.small(graphics, font, user, width - 12 - GuiDraw.smallWidth(font, user), 10, Anim.fade(Theme.MUTED, fade));
		}
		if (!status.isBlank()) {
			GuiDraw.small(graphics, font, status, colX, y + BUTTON_H + 10, Anim.fade(Theme.WARN, fade));
		}

		float footY = height - 18;
		link(graphics, font, mouseX, mouseY, 12, footY, "Language", fade, this::openLanguage);
		link(graphics, font, mouseX, mouseY, 12 + GuiDraw.smallWidth(font, "Language") + 14, footY, "Accessibility", fade, this::openAccessibility);

		String mc = "Minecraft " + SharedConstants.getCurrentVersion().name();
		GuiDraw.small(graphics, font, mc, (width - GuiDraw.smallWidth(font, mc)) * 0.5f, footY, Anim.fade(Theme.MUTED, fade));

		String copy = "Copyright Mojang AB. Do not distribute!";
		float copyW = GuiDraw.smallWidth(font, copy);
		float copyX = width - 12 - copyW;
		link(graphics, font, mouseX, mouseY, copyX, footY, copy, fade, this::openCredits);
	}

	private float button(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		float w,
		String label,
		boolean enabled,
		float fade,
		Runnable action
	) {
		boolean hovered = enabled && GuiDraw.hovered(mouseX, mouseY, x, y, w, BUTTON_H);
		float hover = anim("btn-" + label, hovered ? 1f : 0f);
		int fill = Theme.withAlpha(Theme.mix(Theme.CARD, Theme.CARD_HOVER, hover), (Theme.CARD >>> 24) & 0xFF);
		int outline = hover > 0.55f ? Theme.ACCENT : Theme.LINE;
		GuiDraw.panel(graphics, x, y, w, BUTTON_H, 7, Anim.fade(fill, fade), Anim.fade(outline, fade), enabled ? Theme.ACCENT : 0);
		int text = enabled ? Theme.TEXT : Theme.MUTED;
		GuiDraw.menu(graphics, font, label, x + 14, GuiDraw.middle(y, BUTTON_H), Anim.fade(text, fade));
		if (enabled) {
			hits.add(new Hit(x, y, w, BUTTON_H, action));
		}
		return y + BUTTON_H + BUTTON_GAP;
	}

	private void link(
		GuiGraphicsExtractor graphics,
		Font font,
		int mouseX,
		int mouseY,
		float x,
		float y,
		String label,
		float fade,
		Runnable action
	) {
		float w = GuiDraw.smallWidth(font, label);
		boolean hovered = GuiDraw.hovered(mouseX, mouseY, x - 2, y - 2, w + 4, 12);
		int color = hovered ? Theme.ACCENT : Theme.MUTED;
		GuiDraw.small(graphics, font, label, x, y, Anim.fade(color, fade));
		hits.add(new Hit(x - 2, y - 2, w + 4, 12, action));
	}

	private float anim(String key, float target) {
		float current = hovers.getOrDefault(key, 0f);
		float next = Anim.exp(current, target, 18f, dt);
		hovers.put(key, next);
		return next;
	}

	private void tickAnim() {
		long now = System.nanoTime();
		dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		appear = Anim.exp(appear, 1f, 11f, dt);
	}

	private boolean multiplayerOpen() {
		return minecraft.allowsMultiplayer();
	}

	private void clickSound() {
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
	}

	private void openSingleplayer() {
		clickSound();
		minecraft.setScreen(new SelectWorldScreen(this));
	}

	private void openMultiplayer() {
		if (!multiplayerOpen()) {
			status = "Multiplayer is disabled on this account.";
			return;
		}
		clickSound();
		minecraft.setScreen(new JoinMultiplayerScreen(this));
	}

	private void openRealms() {
		if (!multiplayerOpen()) {
			status = "Realms needs multiplayer enabled.";
			return;
		}
		clickSound();
		minecraft.setScreen(new RealmsMainScreen(this));
	}

	private void openOptions() {
		clickSound();
		minecraft.setScreen(new OptionsScreen(this, minecraft.options, false));
	}

	private void openLanguage() {
		clickSound();
		minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()));
	}

	private void openAccessibility() {
		clickSound();
		minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options));
	}

	private void openCredits() {
		clickSound();
		minecraft.setScreen(new CreditsAndAttributionScreen(this));
	}

	private void quit() {
		clickSound();
		minecraft.stop();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubled);
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (hit.contains(event.x(), event.y())) {
				hit.click.run();
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	private static String modVersion() {
		return FabricLoader.getInstance()
			.getModContainer("voidmark")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("1.1.170");
	}

	private record Hit(float x, float y, float w, float h, Runnable click) {
		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}
}
