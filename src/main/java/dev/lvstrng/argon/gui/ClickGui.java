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
	private boolean categoryDropdownOpen;
	private Color dropdownColor;
	private int panelX, panelY, panelWidth, panelHeight;
	private boolean layoutInitialized;
	private boolean draggingPanel;
	private boolean resizingPanel;
	private boolean searchFocused;
	private int dragX, dragY;
	private final StringBuilder searchQuery = new StringBuilder();
	private long openedAt;
	private static final int CONTENT_PADDING = 24;
	private static final int TOP_BAR_HEIGHT = 88;
	private static final int MIN_PANEL_WIDTH = 520;
	private static final int MIN_PANEL_HEIGHT = 340;

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
				window.setPanelBounds(panelX + CONTENT_PADDING, panelY + TOP_BAR_HEIGHT + 28, panelWidth - CONTENT_PADDING * 2);
				window.render(context, mouseX, mouseY, delta);
			}
		}
		renderCategoryDropdown(context, mouseX, mouseY);
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

		if (dropdownColor == null) dropdownColor = new Color(8, 11, 18, 0);
		else dropdownColor = new Color(8, 11, 18, dropdownColor.getAlpha());
		dropdownColor = ColorUtils.smoothAlphaTransition(0.05F, Math.min(235, ClickGUI.alphaWindow.getValueInt() + 30), dropdownColor);

		int r = Math.max(12, ClickGUI.roundQuads.getValueInt() + 8);
		renderDepth(context, panelX, panelY, panelWidth, panelHeight, r);
		RenderUtils.renderRoundedQuad(context.getMatrices(), panelColor,
				panelX, panelY, panelX + panelWidth, panelY + panelHeight,
				r, r, r, r, 18);

		context.fillGradient(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 4,
				Utils.getMainColor(240, 0).getRGB(), Utils.getMainColor(170, 5).getRGB());
		TextRenderer.drawString("ARGON", context, panelX + CONTENT_PADDING, panelY + 30, Color.WHITE.getRGB());
		TextRenderer.drawString("Dropdown command deck", context, panelX + CONTENT_PADDING, panelY + 48, new Color(150, 153, 165).getRGB());

		renderSearchBar(context, mouseX, mouseY);
		renderCategorySelector(context, mouseX, mouseY);

		TextRenderer.drawString(selectedCategory.name, context, panelX + CONTENT_PADDING, panelY + 92, Color.WHITE.getRGB());
		TextRenderer.drawString("Left click toggles modules • Right click opens settings", context,
				panelX + CONTENT_PADDING, panelY + 110, new Color(145, 148, 160).getRGB());

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
		int w = Math.min(250, panelWidth / 3);
		int x = panelX + panelWidth - CONTENT_PADDING - w;
		int y = panelY + 24;
		Color glow = searchFocused || isSearchHovered(mouseX, mouseY) ? Utils.getMainColor(95, 1) : new Color(255, 255, 255, 20);
		RenderUtils.renderRoundedQuad(context.getMatrices(), glow, x - 2, y - 2, x + w + 2, y + 26, 9, 9, 9, 9, 12);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(5, 8, 14, 215), x, y, x + w, y + 24, 8, 8, 8, 8, 12);
		String text = searchQuery.isEmpty() ? "Search modules..." : searchQuery.toString();
		TextRenderer.drawString(text, context, x + 8, y + 16, searchQuery.isEmpty() ? new Color(108, 114, 130).getRGB() : Color.WHITE.getRGB());
	}

	private void renderCategorySelector(DrawContext context, int mouseX, int mouseY) {
		int x = panelX + CONTENT_PADDING;
		int y = panelY + 64;
		int w = Math.min(260, panelWidth - CONTENT_PADDING * 2);
		Color glow = isCategorySelectorHovered(mouseX, mouseY) || categoryDropdownOpen ? Utils.getMainColor(120, selectedCategory.ordinal()) : new Color(255, 255, 255, 22);
		RenderUtils.renderRoundedQuad(context.getMatrices(), glow, x - 2, y - 2, x + w + 2, y + 30, 10, 10, 10, 10, 12);
		RenderUtils.renderRoundedQuad(context.getMatrices(), dropdownColor, x, y, x + w, y + 28, 9, 9, 9, 9, 12);
		TextRenderer.drawString("Category: " + selectedCategory.name, context, x + 12, y + 18, Color.WHITE.getRGB());
		TextRenderer.drawString(categoryDropdownOpen ? "▲" : "▼", context, x + w - 20, y + 18, new Color(190, 220, 255).getRGB());
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


	private void renderCategoryDropdown(DrawContext context, int mouseX, int mouseY) {
		if (!categoryDropdownOpen) return;
		int x = panelX + CONTENT_PADDING;
		int y = panelY + 96;
		int w = Math.min(260, panelWidth - CONTENT_PADDING * 2);
		int rowHeight = 28;
		int h = Category.values().length * rowHeight + 8;
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(0, 0, 0, 105), x + 4, y + 6, x + w + 4, y + h + 6, 12, 12, 12, 12, 14);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(8, 11, 18, 242), x, y, x + w, y + h, 12, 12, 12, 12, 14);
		for (Category category : Category.values()) {
			int rowY = y + 4 + category.ordinal() * rowHeight;
			boolean selected = category == selectedCategory;
			boolean hovered = isCategoryHovered(category, mouseX, mouseY);
			Color rowColor = selected ? Utils.getMainColor(130, category.ordinal()) : new Color(255, 255, 255, hovered ? 24 : 0);
			RenderUtils.renderRoundedQuad(context.getMatrices(), rowColor, x + 6, rowY, x + w - 6, rowY + 24, 7, 7, 7, 7, 8);
			TextRenderer.drawString(category.name, context, x + 18, rowY + 16, selected ? Color.WHITE.getRGB() : new Color(190, 193, 205).getRGB());
		}
	}

	private boolean isCategorySelectorHovered(double mouseX, double mouseY) {
		int x = panelX + CONTENT_PADDING;
		int y = panelY + 64;
		int w = Math.min(260, panelWidth - CONTENT_PADDING * 2);
		return mouseX > x && mouseX < x + w && mouseY > y && mouseY < y + 28;
	}

	private boolean isCategoryHovered(Category category, double mouseX, double mouseY) {
		if (!categoryDropdownOpen) return false;
		int x = panelX + CONTENT_PADDING;
		int y = panelY + 100 + category.ordinal() * 28;
		int w = Math.min(260, panelWidth - CONTENT_PADDING * 2);
		return mouseX > x + 6 && mouseX < x + w - 6 && mouseY > y && mouseY < y + 24;
	}

	private boolean isSearchHovered(double mouseX, double mouseY) {
		int w = Math.min(250, panelWidth / 3);
		int x = panelX + panelWidth - CONTENT_PADDING - w;
		return mouseX > x && mouseX < x + w && mouseY > panelY + 24 && mouseY < panelY + 48;
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
		return panelHeight - TOP_BAR_HEIGHT;
	}

	@Override public boolean shouldPause() { return false; }

	@Override
	public void close() {
		Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
		onGuiClose();
	}

	public void onGuiClose() {
		mc.setScreenAndRender(Argon.INSTANCE.previousScreen);
		currentColor = null; panelColor = null; dropdownColor = null;
		draggingPanel = false; resizingPanel = false; searchFocused = false; categoryDropdownOpen = false;
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
		if (button == 0 && isCategorySelectorHovered(mouseX, mouseY)) {
			categoryDropdownOpen = !categoryDropdownOpen;
			searchFocused = false;
			return true;
		}
		if (button == 0 && categoryDropdownOpen) {
			for (Category category : Category.values()) {
				if (isCategoryHovered(category, mouseX, mouseY)) {
					selectedCategory = category;
					categoryDropdownOpen = false;
					return true;
				}
			}
			categoryDropdownOpen = false;
		}
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
