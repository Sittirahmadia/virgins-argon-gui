package dev.lvstrng.argon.gui.components.settings;

import dev.lvstrng.argon.gui.components.ModuleButton;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.module.setting.Setting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class KeybindBox extends RenderableSetting {
	public KeybindSetting keybind;
	private Color hoverColor;

	public KeybindBox(ModuleButton parent, Setting<?> setting, int offset) {
		super(parent, setting, offset);
		this.keybind = (KeybindSetting) setting;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		int idx = parent.settings.indexOf(this);
		int ry = parentY() + parentOffset() + offset;
		int rh = parentHeight();

		CharSequence label = keybind.isListening()
				? "Listening..."
				: setting.getName() + ": " + KeyUtils.getKey(keybind.getKey());
		Color col = keybind.isListening()
				? Utils.getMainColor(255, idx)
				: new Color(195, 195, 200);
		TextRenderer.drawString(label, context, parentX() + 8, ry + rh / 2 + 3, col.getRGB());

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
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (!isHovered(mouseX, mouseY)) return;
		if (!keybind.isListening()) {
			keybind.toggleListening(); keybind.setListening(true);
		} else {
			if (keybind.isModuleKey()) parent.module.setKey(button);
			keybind.setKey(button); keybind.setListening(false);
		}
	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE && mouseOver) {
			if (keybind.isModuleKey()) parent.module.setKey(keybind.getOriginalKey());
			keybind.setKey(keybind.getOriginalKey()); keybind.setListening(false);
		} else if (keybind.isListening() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
			if (keybind.isModuleKey()) parent.module.setKey(keyCode);
			keybind.setKey(keyCode); keybind.setListening(false);
		}
	}
}
