package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class CheckBox extends RenderableSetting {
	private final BooleanSetting setting;
	private Color hoverColor;

	public CheckBox(ModuleButton parent, Setting<?> setting, int offset) {
		super(parent, setting, offset);
		this.setting = (BooleanSetting) setting;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int idx = parent.settings.indexOf(this);
		int ry = parentY() + parentOffset() + offset;
		int rh = parentHeight();

		// Label
		TextRenderer.drawString(setting.getName(), context,
				parentX() + 8, ry + rh / 2 + 3,
				new Color(195, 195, 200).getRGB());

		// Toggle pill (right-aligned)
		boolean val = setting.getValue();
		int pillW = 24; int pillH = 12;
		int pillX = parentX() + parentWidth() - pillW - 7;
		int pillY = ry + (rh - pillH) / 2;

		Color pillBg = val ? Utils.getMainColor(210, idx) : new Color(40, 40, 45, 210);
		RenderUtils.renderRoundedQuad(context.getMatrices(), pillBg,
				pillX, pillY, pillX + pillW, pillY + pillH,
				6, 6, 6, 6, 8);

		// Thumb circle
		double thumbX = val ? pillX + pillW - 10.0 : pillX + 2.0;
		RenderUtils.renderCircle(context.getMatrices(), Color.WHITE,
				thumbX + 4, pillY + 6, 4, 12);

		// Hover overlay
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
			setting.setValue(setting.getOriginalValue());
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (isHovered(mouseX, mouseY) && button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			setting.toggle();
	}
}
