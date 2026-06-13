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
	private float iconSpin;

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

		int r = ClickGUI.roundQuads.getValueInt();
		boolean hovered = isHovered(mouseX, mouseY);
		int lift = hovered && !parent.dragging ? 4 : 0;
		iconSpin += delta * 0.08f;

		Color cardColor = module.isEnabled()
				? new Color(22, 24, 33, Math.min(230, currentColor.getAlpha() + 45))
				: new Color(16, 17, 24, Math.min(215, currentColor.getAlpha() + 35));
		if (module.isEnabled())
			RenderUtils.renderRoundedQuad(context.getMatrices(), Utils.getMainColor(40, idx),
					parent.getX() - 3, parent.getY() + offset - 3,
					parent.getX() + parent.getWidth() + 3, parent.getY() + parent.getHeight() + offset + 3,
					r + 5, r + 5, r + 5, r + 5, 14);
		RenderUtils.renderRoundedQuad(context.getMatrices(), new Color(0, 0, 0, 50),
				parent.getX() + 2, parent.getY() + offset + 4 - lift,
				parent.getX() + parent.getWidth() + 2, parent.getY() + parent.getHeight() + offset + 4 - lift,
				r + 3, r + 3, r + 3, r + 3, 14);
		RenderUtils.renderRoundedQuad(context.getMatrices(), cardColor,
				parent.getX(), parent.getY() + offset - lift,
				parent.getX() + parent.getWidth(), parent.getY() + parent.getHeight() + offset - lift,
				r + 2, r + 2, r + 2, r + 2, 14);

		// Left accent bar — always rendered, full opacity if enabled, dim if disabled
		int barAlpha = module.isEnabled() ? 255 : 55;
		context.fillGradient(
				parent.getX() + 8, parent.getY() + offset + 8 - lift,
				parent.getX() + 12, parent.getY() + offset + parent.getHeight() - 8 - lift,
				Utils.getMainColor(barAlpha, idx).getRGB(),
				Utils.getMainColor(barAlpha, idx + 1).getRGB());

		// Module name
		TextRenderer.drawString(module.getName(), context,
				parent.getX() + 24,
				parent.getY() + offset + parent.getHeight() / 2 + 3 - lift,
				defaultColor.getRGB());

		TextRenderer.drawString(module.isEnabled() ? "ON" : "OFF", context,
				parent.getX() + parent.getWidth() - 34,
				parent.getY() + offset + parent.getHeight() / 2 + 3 - lift,
				(module.isEnabled() ? Utils.getMainColor(230, idx) : new Color(120, 123, 135)).getRGB());

		renderFloatingIcon(context, idx, lift);
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

	private void renderFloatingIcon(DrawContext context, int idx, int lift) {
		double bob = Math.sin(iconSpin + idx) * 2.5;
		int cx = parent.getX() + parent.getWidth() - 62;
		int cy = parent.getY() + offset + parent.getHeight() / 2 - 1 - lift + (int) bob;
		RenderUtils.renderCircle(context.getMatrices(), new Color(0, 0, 0, 90), cx + 2, cy + 4, 12, 18);
		RenderUtils.renderCircle(context.getMatrices(), Utils.getMainColor(module.isEnabled() ? 150 : 55, idx), cx, cy, 11, 18);
		String icon = module.getName().length() > 0 ? module.getName().toString().substring(0, 1).toUpperCase() : "?";
		TextRenderer.drawString(icon, context, cx - 3, cy + 5, Color.WHITE.getRGB());
	}

	private void renderHover(DrawContext context, int mouseX, int mouseY) {
		if (parent.dragging) return;
		int toA = isHovered(mouseX, mouseY) ? 18 : 0;
		if (currentAlpha == null) currentAlpha = new Color(255, 255, 255, toA);
		else currentAlpha = new Color(255, 255, 255, currentAlpha.getAlpha());
		if (currentAlpha.getAlpha() != toA)
			currentAlpha = ColorUtils.smoothAlphaTransition(0.05F, toA, currentAlpha);
		int lift = isHovered(mouseX, mouseY) ? 4 : 0;
		context.fill(parent.getX(), parent.getY() + offset - lift,
				parent.getX() + parent.getWidth(), parent.getY() + parent.getHeight() + offset - lift,
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
