package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.ColorUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.lvstrng.argon.Argon.mc;

public final class ClickGui extends Screen {
	public List<Window> windows = new ArrayList<>();
	public Color currentColor;

	public ClickGui() {
		super(Text.empty());
		int offsetX = 30;
		for (Category category : Category.values()) {
			windows.add(new Window(offsetX, 40, 200, 28, category, this));
			offsetX += 218;
		}
	}

	public boolean isDraggingAlready() {
		for (Window window : windows)
			if (window.dragging) return true;
		return false;
	}

	@Override
	protected void setInitialFocus() {
		if (client == null) return;
		super.setInitialFocus();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (mc.currentScreen != this) return;

		if (Argon.INSTANCE.previousScreen != null)
			Argon.INSTANCE.previousScreen.render(context, 0, 0, delta);

		// Background dim
		if (currentColor == null) currentColor = new Color(0, 0, 0, 0);
		else currentColor = new Color(0, 0, 0, currentColor.getAlpha());
		int targetAlpha = ClickGUI.background.getValue() ? 160 : 0;
		if (currentColor.getAlpha() != targetAlpha)
			currentColor = ColorUtils.smoothAlphaTransition(0.05F, targetAlpha, currentColor);
		if (currentColor.getAlpha() > 0)
			context.fill(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight(), currentColor.getRGB());

		RenderUtils.unscaledProjection();
		mouseX *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
		mouseY *= (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
		super.render(context, mouseX, mouseY, delta);

		for (Window window : windows) {
			window.render(context, mouseX, mouseY, delta);
			window.updatePosition(mouseX, mouseY, delta);
		}
		RenderUtils.scaledProjection();
	}

	@Override public boolean shouldPause() { return false; }

	@Override
	public void close() {
		Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
		onGuiClose();
	}

	public void onGuiClose() {
		mc.setScreenAndRender(Argon.INSTANCE.previousScreen);
		currentColor = null;
		for (Window window : windows) window.onGuiClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		for (Window w : windows) w.keyPressed(keyCode, scanCode, modifiers);
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		mouseX *= (int) mc.getWindow().getScaleFactor();
		mouseY *= (int) mc.getWindow().getScaleFactor();
		for (Window w : windows) w.mouseClicked(mouseX, mouseY, button);
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
		mouseX *= (int) mc.getWindow().getScaleFactor();
		mouseY *= (int) mc.getWindow().getScaleFactor();
		for (Window w : windows) w.mouseDragged(mouseX, mouseY, button, dX, dY);
		return super.mouseDragged(mouseX, mouseY, button, dX, dY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
		mouseY *= mc.getWindow().getScaleFactor();
		for (Window w : windows) w.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
		return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		mouseX *= (int) mc.getWindow().getScaleFactor();
		mouseY *= (int) mc.getWindow().getScaleFactor();
		for (Window w : windows) w.mouseReleased(mouseX, mouseY, button);
		return super.mouseReleased(mouseX, mouseY, button);
	}
}
