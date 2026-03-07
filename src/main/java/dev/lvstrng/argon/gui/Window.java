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
	private final int width, height;
	public Color currentColor;
	private final Category category;
	public boolean dragging, extended;
	private int dragX, dragY;
	private int prevX, prevY;
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

		// ── Drop shadow
		RenderUtils.renderRoundedQuad(context.getMatrices(),
				new Color(0, 0, 0, Math.min(currentColor.getAlpha() / 2, 50)),
				prevX - 4, prevY - 4, prevX + width + 4, prevY + height + 4,
				r + 2, r + 2, 0, 0, 8);

		// ── Header background
		RenderUtils.renderRoundedQuad(context.getMatrices(), currentColor,
				prevX, prevY, prevX + width, prevY + height,
				r, r, 0, 0, 50);

		// ── Thin accent line at bottom of header (gradient)
		Color c1 = Utils.getMainColor(220, 0);
		Color c2 = Utils.getMainColor(220, 3);
		context.fillGradient(prevX + r, prevY + height - 2, prevX + width - r, prevY + height,
				c1.getRGB(), c2.getRGB());

		// ── Category label centered
		CharSequence label = category.name;
		int labelW = TextRenderer.getWidth(label);
		TextRenderer.drawString(label, context,
				prevX + width / 2 - labelW / 2,
				prevY + height / 2 + 3,
				Color.WHITE.getRGB());

		updateButtons(delta);
		for (ModuleButton mb : moduleButtons)
			mb.render(context, mouseX, mouseY, delta);
	}

	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		for (ModuleButton mb : moduleButtons) mb.keyPressed(keyCode, scanCode, modifiers);
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
		if (isHovered(mouseX, mouseY) && button == 0 && !isDraggingAlready()) {
			dragging = true;
			dragX = (int)(mouseX - x);
			dragY = (int)(mouseY - y);
		}
		if (extended)
			for (ModuleButton mb : moduleButtons) mb.mouseClicked(mouseX, mouseY, button);
	}

	public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
		if (extended)
			for (ModuleButton mb : moduleButtons) mb.mouseDragged(mouseX, mouseY, button, dX, dY);
	}

	public void updateButtons(float delta) {
		int offset = height;
		for (ModuleButton mb : moduleButtons) {
			mb.animation.animate(0.5 * delta,
					mb.extended ? height * (mb.settings.size() + 1) : height);
			mb.offset = offset;
			offset += (int) mb.animation.getValue();
		}
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && dragging) dragging = false;
		for (ModuleButton mb : moduleButtons) mb.mouseReleased(mouseX, mouseY, button);
	}

	public void mouseScrolled(double mouseX, double mouseY, double h, double v) {
		prevX = x; prevY = y;
		prevY = (int)(prevY + v * 20);
		setY((int)(y + v * 20));
	}

	public int getX() { return prevX; }
	public int getY() { return prevY; }
	public void setX(int x) { this.x = x; }
	public void setY(int y) { this.y = y; }
	public int getWidth()   { return width; }
	public int getHeight()  { return height; }

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
