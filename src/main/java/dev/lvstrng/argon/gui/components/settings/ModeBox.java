package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.ModeSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class ModeBox extends RenderableSetting {
	public final ModeSetting<?> setting;
	private Color hoverColor;

	public ModeBox(ModuleButton parent, Setting<?> setting, int offset) {
		super(parent, setting, offset);
		this.setting = (ModeSetting<?>) setting;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int idx = parent.settings.indexOf(this);
		int ry = parentY() + parentOffset() + offset;
		int rh = parentHeight();

		TextRenderer.drawString(setting.getName(), context,
				parentX() + 8, ry + rh / 2 + 3, new Color(195, 195, 200).getRGB());

		// Mode chip
		String modeName = setting.getMode().name();
		int chipW = TextRenderer.getWidth(modeName) + 12;
		int chipX = parentX() + parentWidth() - chipW - 7;
		int chipY = ry + (rh - 14) / 2;
		Color accent = Utils.getMainColor(190, idx);

		RenderUtils.renderRoundedQuad(context.getMatrices(),
				new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 35),
				chipX, chipY, chipX + chipW, chipY + 14, 3, 3, 3, 3, 6);
		RenderUtils.renderRoundedOutline(context,
				new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120),
				chipX, chipY, chipX + chipW, chipY + 14, 3, 3, 3, 3, 1, 6);

		int labelX = chipX + chipW / 2 - TextRenderer.getWidth(modeName) / 2;
		TextRenderer.drawString(modeName, context, labelX, chipY + 10, accent.getRGB());

		if (!parent.parent.dragging) {
			int toA = isHovered(mouseX, mouseY) ? 16 : 0;
			if (hoverColor == null) hoverColor = new Color(255, 255, 255, toA);
			else hoverColor = new Color(255, 255, 255, hoverColor.getAlpha());
			if (hoverColor.getAlpha() != toA)
				hoverColor = ColorUtils.smoothAlphaTransition(0.05F, toA, hoverColor);
			context.fill(parentX(), ry, parentX() + parentWidth(), ry + rh, hoverColor.getRGB());
		}
	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		if (mouseOver && parent.extended && keyCode == GLFW.GLFW_KEY_BACKSPACE)
			setting.setModeIndex(setting.getOriginalValue());
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (isHovered(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) setting.cycle();
	}
}
