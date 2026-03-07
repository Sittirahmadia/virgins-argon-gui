package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public abstract class RenderableSetting {
	public MinecraftClient mc = MinecraftClient.getInstance();
	public ModuleButton parent;
	public Setting<?> setting;
	public int offset;
	public Color currentColor;
	public boolean mouseOver;
	int x, y, width, height;

	public RenderableSetting(ModuleButton parent, Setting<?> setting, int offset) {
		this.parent = parent;
		this.setting = setting;
		this.offset = offset;
	}

	public int parentX()      { return parent.parent.getX(); }
	public int parentY()      { return parent.parent.getY(); }
	public int parentWidth()  { return parent.parent.getWidth(); }
	public int parentHeight() { return parent.parent.getHeight(); }
	public int parentOffset() { return parent.offset; }

	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		mouseOver = isHovered(mouseX, mouseY);
		x = parentX(); y = parentY() + parentOffset() + offset;
		width = parentX() + parentWidth(); height = parentY() + parentOffset() + offset + parentHeight();

		// Setting row background (slightly darker than module row)
		context.fill(x, y, width, height, currentColor.getRGB());

		// Thin separator at bottom
		context.fill(x + 6, height - 1, width - 6, height, new Color(255, 255, 255, 10).getRGB());
	}

	public void renderDescription(DrawContext context, int mouseX, int mouseY, float delta) {
		if (!isHovered(mouseX, mouseY) || setting.getDescription() == null || parent.parent.dragging) return;
		CharSequence desc = setting.getDescription();
		int tw = TextRenderer.getWidth(desc);
		int cx = mc.getWindow().getWidth() / 2;
		int tx = cx - tw / 2;
		int ty = mc.getWindow().getHeight() / 2 + 302;

		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(14, 14, 17, 210),
				tx - 7, ty - 11, tx + tw + 7, ty + 13, 3, 3, 3, 3, 10);
		TextRenderer.drawString(desc, context, tx, ty, new Color(175, 175, 180).getRGB());
	}

	public void onUpdate() {
		if (currentColor == null) currentColor = new Color(10, 10, 13, 0);
		else currentColor = new Color(10, 10, 13, currentColor.getAlpha());
		if (currentColor.getAlpha() != 125)
			currentColor = ColorUtils.smoothAlphaTransition(0.05F, 125, currentColor);
	}

	public void onGuiClose() { currentColor = null; }
	public void keyPressed(int keyCode, int scanCode, int modifiers) {}
	public void mouseClicked(double mouseX, double mouseY, int button) {}
	public void mouseReleased(double mouseX, double mouseY, int button) {}
	public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {}

	public boolean isHovered(double mx, double my) {
		return mx > parentX() && mx < parentX() + parentWidth()
				&& my > offset + parentOffset() + parentY()
				&& my < offset + parentOffset() + parentY() + parentHeight();
	}
}
