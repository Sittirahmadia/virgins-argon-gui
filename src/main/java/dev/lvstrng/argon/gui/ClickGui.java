package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.ColorUtils;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.TextRenderer;
import dev.lvstrng.argon.utils.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.lvstrng.argon.Argon.mc;

public final class ClickGui extends Screen {
	public List<Window> windows = new ArrayList<>();
	public Color currentColor;
	public Category selectedCategory = Category.COMBAT;
	private Color panelColor;
	private Color sidebarColor;
	private int panelX, panelY, panelWidth, panelHeight;
	private boolean layoutInitialized;
	private boolean draggingPanel;
	private boolean resizingPanel;
	private boolean searchFocused;
	private int dragX, dragY;
	private final StringBuilder searchQuery = new StringBuilder();
	private long openedAt;
	private float previewYaw;
	private static final int SIDEBAR_WIDTH = 150;
	private static final int MIN_PANEL_WIDTH = 600;
	private static final int MIN_PANEL_HEIGHT = 360;

	public ClickGui() {
		super(Text.empty());
		for (Category category : Category.values()) {
			windows.add(new Window(0, 0, 420, 30, category, this));
		}
	}

	public boolean isDraggingAlready() {
		for (Window window : windows)
			if (window.dragging) return true;
		return draggingPanel || resizingPanel;
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
		renderAnimatedBackground(context);
		renderPanel(context, mouseX, mouseY, delta);
		for (Window window : windows) {
			if (window.getCategory() == selectedCategory) {
				window.setPanelBounds(panelX + SIDEBAR_WIDTH + 18, panelY + 104, panelWidth - SIDEBAR_WIDTH - 226);
				window.render(context, mouseX, mouseY, delta);
			}
		}
		renderPreviewPanel(context, mouseX, mouseY, delta);
		RenderUtils.scaledProjection();
	}

	private void updatePanelLayout() {
		int screenWidth = mc.getWindow().getFramebufferWidth();
		int screenHeight = mc.getWindow().getFramebufferHeight();
		if (!layoutInitialized) {
			panelWidth = Math.min(820, screenWidth - 60);
			panelHeight = Math.min(520, screenHeight - 60);
			panelX = (screenWidth - panelWidth) / 2;
			panelY = (screenHeight - panelHeight) / 2;
			openedAt = System.currentTimeMillis();
			layoutInitialized = true;
		}
		panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(panelWidth, screenWidth - 24));
		panelHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(panelHeight, screenHeight - 24));
		panelX = Math.max(8, Math.min(panelX, screenWidth - panelWidth - 8));
		panelY = Math.max(8, Math.min(panelY, screenHeight - panelHeight - 8));
	}

	private void renderPanel(DrawContext context, int mouseX, int mouseY, float delta) {
		if (panelColor == null) panelColor = new Color(12, 13, 18, 0);
		else panelColor = new Color(12, 13, 18, panelColor.getAlpha());
		panelColor = ColorUtils.smoothAlphaTransition(0.05F, ClickGUI.alphaWindow.getValueInt(), panelColor);

		if (sidebarColor == null) sidebarColor = new Color(8, 9, 14, 0);
		else sidebarColor = new Color(8, 9, 14, sidebarColor.getAlpha());
		sidebarColor = ColorUtils.smoothAlphaTransition(0.05F, Math.min(235, ClickGUI.alphaWindow.getValueInt() + 35), sidebarColor);

		int r = Math.max(12, ClickGUI.roundQuads.getValueInt() + 8);
		renderDepth(context, panelX, panelY, panelWidth, panelHeight, r);
		RenderUtils.renderRoundedQuad(context.getMatrices(), panelColor,
				panelX, panelY, panelX + panelWidth, panelY + panelHeight,
				r, r, r, r, 18);
		RenderUtils.renderRoundedQuad(context.getMatrices(), sidebarColor,
				panelX, panelY, panelX + SIDEBAR_WIDTH, panelY + panelHeight,
				r, 0, r, 0, 18);

		context.fillGradient(panelX + SIDEBAR_WIDTH, panelY + 1, panelX + panelWidth - r, panelY + 4,
				Utils.getMainColor(240, 0).getRGB(), Utils.getMainColor(170, 5).getRGB());
		TextRenderer.drawString("ARGON", context, panelX + 24, panelY + 30, Color.WHITE.getRGB());
		TextRenderer.drawString("3D neon command deck", context, panelX + 24, panelY + 48, new Color(150, 153, 165).getRGB());

		renderSearchBar(context, mouseX, mouseY);
		renderCategoryTabs(context, mouseX, mouseY);

		TextRenderer.drawString(selectedCategory.name, context, panelX + SIDEBAR_WIDTH + 20, panelY + 32, Color.WHITE.getRGB());
		TextRenderer.drawString("Left click toggles modules • Right click opens settings", context,
				panelX + SIDEBAR_WIDTH + 20, panelY + 50, new Color(145, 148, 160).getRGB());

		int grip = 18;
		RenderUtils.renderRoundedQuad(context.getMatrices(), isResizeHovered(mouseX, mouseY) ? Utils.getMainColor(120, 3) : new Color(255, 255, 255, 24),
				panelX + panelWidth - grip, panelY + panelHeight - grip, panelX + panelWidth - 6, panelY + panelHeight - 6,
				6, 6, 6, 6, 8);
	}

	private void renderDepth(DrawContext context, int x, int y, int w, int h, int r) {
		for (int i = 18; i > 0; i -= 3) {
			RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(0, 0, 0, 6 + i),
					x + i / 2.0, y + i, x + w + i / 2.0, y + h + i,
					r + i / 2.0, r + i / 2.0, r + i / 2.0, r + i / 2.0, 16);
		}
	}

	private void renderSearchBar(DrawContext context, int mouseX, int mouseY) {
		int x = panelX + 18;
		int y = panelY + 70;
		int w = SIDEBAR_WIDTH - 36;
		Color glow = searchFocused || isSearchHovered(mouseX, mouseY) ? Utils.getMainColor(95, 1) : new Color(255, 255, 255, 20);
		RenderUtils.renderRoundedQuad(context.getMatrices(), glow, x - 2, y - 2, x + w + 2, y + 24, 9, 9, 9, 9, 12);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(5, 8, 14, 215), x, y, x + w, y + 22, 8, 8, 8, 8, 12);
		String text = searchQuery.isEmpty() ? "Search modules..." : searchQuery.toString();
		TextRenderer.drawString(text, context, x + 8, y + 15, searchQuery.isEmpty() ? new Color(108, 114, 130).getRGB() : Color.WHITE.getRGB());
	}

	private void renderCategoryTabs(DrawContext context, int mouseX, int mouseY) {
		int tabY = panelY + 108;
		int index = 0;
		for (Category category : Category.values()) {
			boolean selected = category == selectedCategory;
			int y = tabY + index * 38;
			Color tabColor = selected ? Utils.getMainColor(150, index) : new Color(255, 255, 255, isCategoryHovered(category, mouseX, mouseY) ? 22 : 0);
			RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(0, 0, 0, selected ? 85 : 35),
					panelX + 18, y + 4, panelX + SIDEBAR_WIDTH - 10, y + 34, 10, 10, 10, 10, 12);
			RenderUtils.renderRoundedQuad(context.getMatrices(), tabColor,
					panelX + 14, y, panelX + SIDEBAR_WIDTH - 14, y + 30,
					8, 8, 8, 8, 12);
			TextRenderer.drawString(category.name, context, panelX + 30, y + 19,
					selected ? Color.WHITE.getRGB() : new Color(190, 193, 205).getRGB());
			index++;
		}
	}

	private void renderAnimatedBackground(DrawContext context) {
		long time = System.currentTimeMillis() - openedAt;
		int step = 34;
		for (int x = panelX - 80; x < panelX + panelWidth + 80; x += step) {
			int shifted = (int) ((x + time / 30) % step);
			context.fill(panelX + shifted + x - panelX, panelY - 30, panelX + shifted + x - panelX + 1, panelY + panelHeight + 30, new Color(80, 130, 255, 18).getRGB());
		}
		for (int y = panelY - 80; y < panelY + panelHeight + 80; y += step) {
			context.fill(panelX - 30, y, panelX + panelWidth + 30, y + 1, new Color(120, 80, 255, 14).getRGB());
		}
		for (int i = 0; i < 24; i++) {
			double phase = (time / 900.0) + i;
			int px = panelX + (int) ((Math.sin(phase * 0.8) * 0.5 + 0.5) * panelWidth);
			int py = panelY + (int) ((Math.cos(phase * 0.6) * 0.5 + 0.5) * panelHeight);
			RenderUtils.renderCircle(context.getMatrices(), Utils.getMainColor(45, i), px, py, 2 + (i % 3), 12);
		}
	}

	private void renderPreviewPanel(DrawContext context, int mouseX, int mouseY, float delta) {
		int x = panelX + panelWidth - 190;
		int y = panelY + 86;
		int w = 166;
		int h = panelHeight - 112;
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(0, 0, 0, 68), x + 5, y + 8, x + w + 5, y + h + 8, 14, 14, 14, 14, 16);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(10, 12, 20, 210), x, y, x + w, y + h, 14, 14, 14, 14, 16);
		TextRenderer.drawString("3D Preview", context, x + 16, y + 24, Color.WHITE.getRGB());
		TextRenderer.drawString("drag-ready player", context, x + 16, y + 42, new Color(140, 145, 160).getRGB());

		previewYaw += delta * 0.55f;
		int cx = x + w / 2;
		int cy = y + 125;
		drawWirePlayer(context, cx, cy, previewYaw);
		drawHitbox(context, cx, cy + 2, previewYaw);

		TextRenderer.drawString("Hitbox", context, x + 16, y + h - 54, new Color(210, 220, 255).getRGB());
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(40, 120, 255, 42), x + 16, y + h - 38, x + w - 16, y + h - 18, 7, 7, 7, 7, 8);
		context.fillGradient(x + 20, y + h - 31, x + w - 20, y + h - 27, Utils.getMainColor(210, 1).getRGB(), Utils.getMainColor(160, 5).getRGB());
	}

	private void drawWirePlayer(DrawContext context, int cx, int cy, float yaw) {
		int sway = (int) (Math.sin(yaw / 16.0) * 8);
		Color body = Utils.getMainColor(160, 2);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(255, 255, 255, 24), cx - 22 + sway, cy - 58, cx + 22 + sway, cy + 44, 8, 8, 8, 8, 8);
		RenderUtils.renderCircle(context.getMatrices(), body, cx + sway, cy - 72, 17, 22);
		RenderUtils.renderRoundedQuad(context.getMatrices(), body, cx - 18 + sway, cy - 52, cx + 18 + sway, cy + 16, 7, 7, 7, 7, 10);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(130, 170, 255, 120), cx - 44 + sway / 2, cy - 44, cx - 28 + sway / 2, cy + 18, 6, 6, 6, 6, 8);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(130, 170, 255, 120), cx + 28 + sway / 2, cy - 44, cx + 44 + sway / 2, cy + 18, 6, 6, 6, 6, 8);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(90, 120, 255, 130), cx - 18 + sway, cy + 18, cx - 4 + sway, cy + 72, 5, 5, 5, 5, 8);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(90, 120, 255, 130), cx + 4 + sway, cy + 18, cx + 18 + sway, cy + 72, 5, 5, 5, 5, 8);
	}

	private void drawHitbox(DrawContext context, int cx, int cy, float yaw) {
		int skew = (int) (Math.sin(yaw / 18.0) * 10);
		Color line = new Color(90, 220, 255, 80);
		context.fill(cx - 48 + skew, cy - 96, cx + 48 + skew, cy - 94, line.getRGB());
		context.fill(cx - 48 - skew, cy + 82, cx + 48 - skew, cy + 84, line.getRGB());
		context.fill(cx - 49 + skew, cy - 96, cx - 47 - skew, cy + 84, line.getRGB());
		context.fill(cx + 47 + skew, cy - 96, cx + 49 - skew, cy + 84, line.getRGB());
	}

	private boolean isCategoryHovered(Category category, double mouseX, double mouseY) {
		int index = category.ordinal();
		int y = panelY + 108 + index * 38;
		return mouseX > panelX + 14 && mouseX < panelX + SIDEBAR_WIDTH - 14 && mouseY > y && mouseY < y + 30;
	}

	private boolean isSearchHovered(double mouseX, double mouseY) {
		return mouseX > panelX + 18 && mouseX < panelX + SIDEBAR_WIDTH - 18 && mouseY > panelY + 70 && mouseY < panelY + 92;
	}

	private boolean isHeaderHovered(double mouseX, double mouseY) {
		return mouseX > panelX && mouseX < panelX + panelWidth && mouseY > panelY && mouseY < panelY + 64;
	}

	private boolean isResizeHovered(double mouseX, double mouseY) {
		return mouseX > panelX + panelWidth - 22 && mouseX < panelX + panelWidth && mouseY > panelY + panelHeight - 22 && mouseY < panelY + panelHeight;
	}

	public boolean matchesSearch(Module module) {
		if (searchQuery.isEmpty()) return true;
		String query = searchQuery.toString().toLowerCase(Locale.ROOT);
		String description = module.getDescription() == null ? "" : module.getDescription().toString();
		return module.getName().toString().toLowerCase(Locale.ROOT).contains(query)
				|| description.toLowerCase(Locale.ROOT).contains(query);
	}

	public int getContentHeight() {
		return panelHeight;
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
		draggingPanel = false; resizingPanel = false; searchFocused = false;
		for (Window window : windows) window.onGuiClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchFocused) {
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
				searchQuery.deleteCharAt(searchQuery.length() - 1);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				searchFocused = false;
				return true;
			}
		}
		for (Window w : windows) w.keyPressed(keyCode, scanCode, modifiers);
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (searchFocused && !Character.isISOControl(chr)) {
			searchQuery.append(chr);
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		mouseX *= (int) mc.getWindow().getScaleFactor();
		mouseY *= (int) mc.getWindow().getScaleFactor();
		searchFocused = isSearchHovered(mouseX, mouseY);
		if (button == 0 && isResizeHovered(mouseX, mouseY)) {
			resizingPanel = true;
			return true;
		}
		if (button == 0 && isHeaderHovered(mouseX, mouseY) && !searchFocused) {
			draggingPanel = true;
			dragX = (int) mouseX - panelX;
			dragY = (int) mouseY - panelY;
			return true;
		}
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
		if (draggingPanel) {
			panelX = (int) mouseX - dragX;
			panelY = (int) mouseY - dragY;
			updatePanelLayout();
			return true;
		}
		if (resizingPanel) {
			panelWidth = (int) mouseX - panelX;
			panelHeight = (int) mouseY - panelY;
			updatePanelLayout();
			return true;
		}
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
		if (button == 0) {
			draggingPanel = false;
			resizingPanel = false;
		}
		for (Window w : windows) if (w.getCategory() == selectedCategory) w.mouseReleased(mouseX, mouseY, button);
		return super.mouseReleased(mouseX, mouseY, button);
	}
}
