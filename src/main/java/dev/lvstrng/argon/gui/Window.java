package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.ArrayList;

public final class Window {
	public ArrayList<ModuleButton> moduleButtons = new ArrayList<>();
	public int x, y;
	private int width;
	private final int height;
	public Color currentColor;
	private final Category category;
	public boolean dragging, extended;
	private int dragX, dragY;
	private int prevX, prevY;
	private int scrollOffset;
	public ClickGui parent;

	public Window(int x, int y, int width, int height, Category category, ClickGui parent) {
		this.x = x;       this.y = y;
		this.width = width; this.height = height;
		this.dragging = false; this.extended = true;
		this.category = category; this.parent = parent;
		this.prevX = x;   this.prevY = y;

		int offset = height;
		for (Module module : new ArrayList<>(Argon.INSTANCE.getModuleManager().getModulesInCategory(category))) {
			moduleButtons.add(new ModuleButton(this, module, offset));
			offset += height;
		}
	}

	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int toAlpha = ClickGUI.alphaWindow.getValueInt();
		int r = ClickGUI.roundQuads.getValueInt();

		// Smooth alpha
		if (currentColor == null) currentColor = new Color(14, 14, 17, 0);
		else currentColor = new Color(14, 14, 17, currentColor.getAlpha());
		if (currentColor.getAlpha() != toAlpha)
			currentColor = ColorUtils.smoothAlphaTransition(0.05F, toAlpha, currentColor);

		updateButtons(delta);
		for (ModuleButton mb : moduleButtons)
			if (parent.matchesSearch(mb.module)) mb.render(context, mouseX, mouseY, delta);
	}

	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		for (ModuleButton mb : moduleButtons) if (parent.matchesSearch(mb.module)) mb.keyPressed(keyCode, scanCode, modifiers);
	}

	public void onGuiClose() {
		currentColor = null; dragging = false;
		for (ModuleButton mb : moduleButtons) mb.onGuiClose();
	}

	public boolean isDraggingAlready() {
		for (Window w : parent.windows)
			if (w.dragging) return true;
		return false;
	}

	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (extended)
			for (ModuleButton mb : moduleButtons) if (parent.matchesSearch(mb.module)) mb.mouseClicked(mouseX, mouseY, button);
	}

	public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
		if (extended)
			for (ModuleButton mb : moduleButtons) if (parent.matchesSearch(mb.module)) mb.mouseDragged(mouseX, mouseY, button, dX, dY);
	}

	public void updateButtons(float delta) {
		int offset = scrollOffset;
		for (ModuleButton mb : moduleButtons) {
			if (!parent.matchesSearch(mb.module)) {
				mb.offset = -10000;
				continue;
			}
			mb.animation.animate(0.5 * delta,
					mb.extended ? height * (mb.settings.size() + 1) : height);
			mb.offset = offset;
			offset += (int) mb.animation.getValue() + 8;
		}
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragging) dragging = false;
		for (ModuleButton mb : moduleButtons) mb.mouseReleased(mouseX, mouseY, button);
	}

	public void mouseScrolled(double mouseX, double mouseY, double h, double v) {
		int contentHeight = 0;
		for (ModuleButton mb : moduleButtons) {
			if (!parent.matchesSearch(mb.module)) continue;
			contentHeight += height + 8;
			if (mb.extended) contentHeight += mb.settings.size() * height;
		}
		int maxScroll = Math.min(0, parent.getContentHeight() - 70 - contentHeight);
		scrollOffset = MathHelper.clamp((int)(scrollOffset + v * 24), maxScroll, 0);
	}

	public int getX() { return prevX; }
	public int getY() { return prevY; }
	public void setX(int x) { this.x = x; }
	public void setY(int y) { this.y = y; }
	public int getWidth()   { return width; }
	public int getHeight()  { return height; }
	public Category getCategory() { return category; }
	public void setPanelBounds(int x, int y, int width) {
		this.x = x; this.y = y; this.prevX = x; this.prevY = y;
		this.width = width;
	}

	public boolean isHovered(double mx, double my) {
		return mx > x && mx < x + width && my > y && my < y + height;
	}

	public void updatePosition(double mouseX, double mouseY, float delta) {
		prevX = x; prevY = y;
		if (dragging) {
			x = (int) MathUtils.goodLerp(0.3f * delta, x, mouseX - dragX);
			y = (int) MathUtils.goodLerp(0.3f * delta, y, mouseY - dragY);
		}
	}
}
