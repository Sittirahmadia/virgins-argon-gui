package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.ColorUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import dev.lvstrng.argon.utils.Utils;
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
	public Category selectedCategory = Category.COMBAT;
	private Color panelColor;
	private Color sidebarColor;
	private int panelX, panelY, panelWidth, panelHeight;
	private static final int SIDEBAR_WIDTH = 150;

	public ClickGui() {
		super(Text.empty());
		for (Category category : Category.values()) {
			windows.add(new Window(0, 0, 420, 30, category, this));
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

		updatePanelLayout();
		renderPanel(context, mouseX, mouseY);
		for (Window window : windows) {
			if (window.getCategory() == selectedCategory) {
				window.setPanelBounds(panelX + SIDEBAR_WIDTH + 18, panelY + 62, panelWidth - SIDEBAR_WIDTH - 36);
				window.render(context, mouseX, mouseY, delta);
			}
		}
		RenderUtils.scaledProjection();
	}

	private void updatePanelLayout() {
		int screenWidth = mc.getWindow().getFramebufferWidth();
		int screenHeight = mc.getWindow().getFramebufferHeight();
		panelWidth = Math.min(720, screenWidth - 60);
		panelHeight = Math.min(460, screenHeight - 60);
		panelX = (screenWidth - panelWidth) / 2;
		panelY = (screenHeight - panelHeight) / 2;
	}

	private void renderPanel(DrawContext context, int mouseX, int mouseY) {
		if (panelColor == null) panelColor = new Color(12, 13, 18, 0);
		else panelColor = new Color(12, 13, 18, panelColor.getAlpha());
		panelColor = ColorUtils.smoothAlphaTransition(0.05F, ClickGUI.alphaWindow.getValueInt(), panelColor);

		if (sidebarColor == null) sidebarColor = new Color(8, 9, 14, 0);
		else sidebarColor = new Color(8, 9, 14, sidebarColor.getAlpha());
		sidebarColor = ColorUtils.smoothAlphaTransition(0.05F, Math.min(235, ClickGUI.alphaWindow.getValueInt() + 35), sidebarColor);

		int r = Math.max(12, ClickGUI.roundQuads.getValueInt() + 8);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(0, 0, 0, 78),
				panelX - 10, panelY - 10, panelX + panelWidth + 10, panelY + panelHeight + 12,
				r + 5, r + 5, r + 5, r + 5, 18);
		RenderUtils.renderRoundedQuad(context.getMatrices(), panelColor,
				panelX, panelY, panelX + panelWidth, panelY + panelHeight,
				r, r, r, r, 18);
		RenderUtils.renderRoundedQuad(context.getMatrices(), sidebarColor,
				panelX, panelY, panelX + SIDEBAR_WIDTH, panelY + panelHeight,
				r, 0, r, 0, 18);

		context.fillGradient(panelX + SIDEBAR_WIDTH, panelY + 1, panelX + panelWidth - r, panelY + 3,
				Utils.getMainColor(220, 0).getRGB(), Utils.getMainColor(170, 5).getRGB());

		TextRenderer.drawString("ARGON", context, panelX + 24, panelY + 32, Color.WHITE.getRGB());
		TextRenderer.drawString("modern client", context, panelX + 24, panelY + 50, new Color(150, 153, 165).getRGB());

		int tabY = panelY + 92;
		int index = 0;
		for (Category category : Category.values()) {
			boolean selected = category == selectedCategory;
			int y = tabY + index * 42;
			Color tabColor = selected ? Utils.getMainColor(150, index) : new Color(255, 255, 255, isCategoryHovered(category, mouseX, mouseY) ? 18 : 0);
			RenderUtils.renderRoundedQuad(context.getMatrices(), tabColor,
					panelX + 14, y, panelX + SIDEBAR_WIDTH - 14, y + 30,
					8, 8, 8, 8, 12);
			TextRenderer.drawString(category.name, context, panelX + 30, y + 19,
					selected ? Color.WHITE.getRGB() : new Color(190, 193, 205).getRGB());
			index++;
		}

		TextRenderer.drawString(selectedCategory.name, context, panelX + SIDEBAR_WIDTH + 20, panelY + 32, Color.WHITE.getRGB());
		TextRenderer.drawString("Left click toggles modules • Right click opens settings", context,
				panelX + SIDEBAR_WIDTH + 20, panelY + 50, new Color(145, 148, 160).getRGB());
	}

	private boolean isCategoryHovered(Category category, double mouseX, double mouseY) {
		int index = category.ordinal();
		int y = panelY + 92 + index * 42;
		return mouseX > panelX + 14 && mouseX < panelX + SIDEBAR_WIDTH - 14 && mouseY > y && mouseY < y + 30;
	}

	@Override public boolean shouldPause() { return false; }

	@Override
	public void close() {
		Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
		onGuiClose();
	}

	public void onGuiClose() {
		mc.setScreenAndRender(Argon.INSTANCE.previousScreen);
		currentColor = null; panelColor = null; sidebarColor = null;
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
		for (Category category : Category.values())
			if (button == 0 && isCategoryHovered(category, mouseX, mouseY))
				selectedCategory = category;
		for (Window w : windows)
			if (w.getCategory() == selectedCategory) w.mouseClicked(mouseX, mouseY, button);
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
		mouseX *= (int) mc.getWindow().getScaleFactor();
		mouseY *= (int) mc.getWindow().getScaleFactor();
		for (Window w : windows) if (w.getCategory() == selectedCategory) w.mouseDragged(mouseX, mouseY, button, dX, dY);
		return super.mouseDragged(mouseX, mouseY, button, dX, dY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
		mouseY *= mc.getWindow().getScaleFactor();
		for (Window w : windows) if (w.getCategory() == selectedCategory) w.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
		return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		mouseX *= (int) mc.getWindow().getScaleFactor();
		mouseY *= (int) mc.getWindow().getScaleFactor();
		for (Window w : windows) if (w.getCategory() == selectedCategory) w.mouseReleased(mouseX, mouseY, button);
		return super.mouseReleased(mouseX, mouseY, button);
	}
}
