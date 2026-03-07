package dev.lvstrng.argon.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.gui.Window;
import dev.lvstrng.argon.gui.components.settings.*;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.setting.*;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.lvstrng.argon.Argon.mc;

public final class ModuleButton {
	public List<RenderableSetting> settings = new ArrayList<>();
	public Window parent;
	public Module module;
	public int offset;
	public boolean extended;
	public int settingOffset;
	public Color currentColor;
	public Color defaultColor = new Color(200, 200, 205);
	public Color currentAlpha;
	public AnimationUtils animation = new AnimationUtils(0);

	public ModuleButton(Window parent, Module module, int offset) {
		this.parent = parent;
		this.module = module;
		this.offset = offset;
		this.extended = false;

		settingOffset = parent.getHeight();
		for (Setting<?> setting : module.getSettings()) {
			if (setting instanceof BooleanSetting s)       settings.add(new CheckBox(this, s, settingOffset));
			else if (setting instanceof NumberSetting s)   settings.add(new Slider(this, s, settingOffset));
			else if (setting instanceof ModeSetting<?> s)  settings.add(new ModeBox(this, s, settingOffset));
			else if (setting instanceof KeybindSetting s)  settings.add(new KeybindBox(this, s, settingOffset));
			else if (setting instanceof StringSetting s)   settings.add(new StringBox(this, s, settingOffset));
			else if (setting instanceof MinMaxSetting s)   settings.add(new MinMaxSlider(this, s, settingOffset));
			settingOffset += parent.getHeight();
		}
	}

	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (parent.getY() + offset > MinecraftClient.getInstance().getWindow().getHeight()) return;

		for (RenderableSetting rs : settings) rs.onUpdate();

		// Row bg color
		if (currentColor == null) currentColor = new Color(14, 14, 17, 0);
		else currentColor = new Color(14, 14, 17, currentColor.getAlpha());
		currentColor = ColorUtils.smoothAlphaTransition(0.05F, 155, currentColor);

		int idx = Argon.INSTANCE.getModuleManager().getModulesInCategory(module.getCategory()).indexOf(module);
		Color targetNameColor = module.isEnabled() ? Utils.getMainColor(255, idx) : new Color(195, 195, 200);
		if (!defaultColor.equals(targetNameColor))
			defaultColor = ColorUtils.smoothColorTransition(0.1F, targetNameColor, defaultColor);

		boolean isLast = parent.moduleButtons.get(parent.moduleButtons.size() - 1) == this;
		int r = ClickGUI.roundQuads.getValueInt();

		if (!isLast) {
			context.fill(parent.getX(), parent.getY() + offset,
					parent.getX() + parent.getWidth(), parent.getY() + parent.getHeight() + offset,
					currentColor.getRGB());
		} else {
			RenderUtils.renderRoundedQuad(context.getMatrices(), currentColor,
					parent.getX(), parent.getY() + offset,
					parent.getX() + parent.getWidth(), parent.getY() + parent.getHeight() + offset,
					0, 0, animation.getValue() > 30 ? 0 : r, animation.getValue() > 30 ? 0 : r, 50);
		}

		// Left accent bar — always rendered, full opacity if enabled, dim if disabled
		int barAlpha = module.isEnabled() ? 255 : 55;
		context.fillGradient(
				parent.getX(),     parent.getY() + offset + 4,
				parent.getX() + 2, parent.getY() + offset + parent.getHeight() - 4,
				Utils.getMainColor(barAlpha, idx).getRGB(),
				Utils.getMainColor(barAlpha, idx + 1).getRGB());

		// Module name
		int nameW = TextRenderer.getWidth(module.getName());
		TextRenderer.drawString(module.getName(), context,
				parent.getX() + parent.getWidth() / 2 - nameW / 2,
				parent.getY() + offset + parent.getHeight() / 2 + 3,
				defaultColor.getRGB());

		renderHover(context, mouseX, mouseY);
		renderSettings(context, mouseX, mouseY, delta);

		if (extended)
			for (RenderableSetting rs : settings)
				rs.renderDescription(context, mouseX, mouseY, delta);

		// Description tooltip
		if (isHovered(mouseX, mouseY) && !parent.dragging && module.getDescription() != null) {
			CharSequence desc = module.getDescription();
			int tw = TextRenderer.getWidth(desc);
			int cx = mc.getWindow().getFramebufferWidth() / 2;
			int tx = cx - tw / 2;
			int ty = mc.getWindow().getFramebufferHeight() / 2 + 302;

			RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(14, 14, 17, 210),
					tx - 7, ty - 11, tx + tw + 7, ty + 13, 3, 3, 3, 3, 10);
			// Accent top line on tooltip
			Color tc = Utils.getMainColor(180, idx);
			context.fillGradient(tx - 7, ty - 11, tx + tw + 7, ty - 10,
					tc.getRGB(), Utils.getMainColor(180, idx + 1).getRGB());
			TextRenderer.drawString(desc, context, tx, ty, new Color(185, 185, 190).getRGB());
		}
	}

	private void renderHover(DrawContext context, int mouseX, int mouseY) {
		if (parent.dragging) return;
		int toA = isHovered(mouseX, mouseY) ? 18 : 0;
		if (currentAlpha == null) currentAlpha = new Color(255, 255, 255, toA);
		else currentAlpha = new Color(255, 255, 255, currentAlpha.getAlpha());
		if (currentAlpha.getAlpha() != toA)
			currentAlpha = ColorUtils.smoothAlphaTransition(0.05F, toA, currentAlpha);
		context.fill(parent.getX(), parent.getY() + offset,
				parent.getX() + parent.getWidth(), parent.getY() + parent.getHeight() + offset,
				currentAlpha.getRGB());
	}

	private void renderSettings(DrawContext context, int mouseX, int mouseY, float delta) {
		RenderSystem.enableScissor(
				parent.getX(),
				(int)(mc.getWindow().getHeight() - (parent.getY() + offset + animation.getValue())),
				parent.getWidth(),
				(int) animation.getValue());

		if (animation.getValue() > parent.getHeight()) {
			for (RenderableSetting rs : settings)
				rs.render(context, mouseX, mouseY, delta);

			for (RenderableSetting rs : settings) {
				if (rs instanceof Slider slider) {
					RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 170),
							slider.parentX() + Math.max(slider.lerpedOffsetX, 2.5),
							slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
					RenderUtils.renderCircle(context.getMatrices(), slider.currentColor1.brighter(),
							slider.parentX() + Math.max(slider.lerpedOffsetX, 2.5),
							slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
				} else if (rs instanceof MinMaxSlider slider) {
					RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 170),
							slider.parentX() + Math.max(slider.lerpedOffsetMinX, 2.5),
							slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
					RenderUtils.renderCircle(context.getMatrices(), slider.currentColor1.brighter(),
							slider.parentX() + Math.max(slider.lerpedOffsetMinX, 2.5),
							slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
					RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 170),
							slider.parentX() + Math.max(slider.lerpedOffsetMaxX, 2.5),
							slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 6, 15);
					RenderUtils.renderCircle(context.getMatrices(), slider.currentColor1.brighter(),
							slider.parentX() + Math.max(slider.lerpedOffsetMaxX, 2.5),
							slider.parentY() + slider.offset + slider.parentOffset() + 27.5, 5, 15);
				}
			}
		}
		RenderSystem.disableScissor();
	}

	public void onExtend() {
		for (ModuleButton mb : parent.moduleButtons) mb.extended = false;
	}

	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		for (RenderableSetting rs : settings) rs.keyPressed(keyCode, scanCode, modifiers);
	}

	public void mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
		if (extended)
			for (RenderableSetting rs : settings) rs.mouseDragged(mouseX, mouseY, button, dX, dY);
	}

	public void mouseClicked(double mouseX, double mouseY, int button) {
		if (isHovered(mouseX, mouseY)) {
			if (button == 0) module.toggle();
			if (button == 1 && !module.getSettings().isEmpty()) {
				if (!extended) onExtend();
				extended = !extended;
			}
		}
		if (extended)
			for (RenderableSetting rs : settings) rs.mouseClicked(mouseX, mouseY, button);
	}

	public void onGuiClose() {
		currentAlpha = null; currentColor = null;
		for (RenderableSetting rs : settings) rs.onGuiClose();
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
		for (RenderableSetting rs : settings) rs.mouseReleased(mouseX, mouseY, button);
	}

	public boolean isHovered(double mx, double my) {
		return mx > parent.getX() && mx < parent.getX() + parent.getWidth()
				&& my > parent.getY() + offset && my < parent.getY() + offset + parent.getHeight();
	}
}
