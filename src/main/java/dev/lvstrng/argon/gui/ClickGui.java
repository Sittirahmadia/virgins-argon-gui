package dev.lvstrng.argon.gui;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.ClickGUI;
import dev.lvstrng.argon.module.setting.*;
import dev.lvstrng.argon.utils.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static dev.lvstrng.argon.Argon.mc;

public final class ClickGui extends Screen {

    // ── Layout constants ────────────────────────────────────────────────────────
    private static final int PANEL_W      = 660;
    private static final int PANEL_H      = 420;
    private static final int TAB_H        = 36;
    private static final int MODULE_COL_W = 200;
    private static final int MODULE_ROW_H = 24;
    private static final int SETTINGS_X   = MODULE_COL_W;
    private static final int SETTINGS_W   = PANEL_W - MODULE_COL_W;
    private static final int SETTING_ROW_H = 30;
    private static final int PADDING       = 8;

    // ── State ────────────────────────────────────────────────────────────────────
    private int panelX, panelY;
    private boolean dragging;
    private double dragOffX, dragOffY;

    private Category activeCategory = Category.COMBAT;
    private Module   activeModule   = null;

    private float moduleScrollY   = 0;
    private float targetScrollY   = 0;
    private float settingScrollY  = 0;
    private float settingTargetY  = 0;

    // Animated alpha for background
    private float bgAlpha = 0;

    // Dragging state for sliders/minmax
    private boolean sliderDragging    = false;
    private boolean minSliderDragging = false;
    private boolean maxSliderDragging = false;
    private Setting<?> draggingSettting = null;

    // Keybind listening
    private Setting<?> listeningKeybind = null;

    public ClickGui() {
        super(Text.empty());
    }

    // ── Screen lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int fw = mc.getWindow().getFramebufferWidth();
        int fh = mc.getWindow().getFramebufferHeight();
        panelX = (fw - PANEL_W) / 2;
        panelY = (fh - PANEL_H) / 2;
        bgAlpha = 0;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        Argon.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
        onGuiClose();
    }

    public void onGuiClose() {
        mc.setScreenAndRender(Argon.INSTANCE.previousScreen);
        listeningKeybind = null;
        sliderDragging = minSliderDragging = maxSliderDragging = false;
    }

    @Override
    protected void setInitialFocus() {
        if (client != null) super.setInitialFocus();
    }

    // ── Main render ──────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mc.currentScreen != this) return;

        // Scale to framebuffer
        int scaledMX = (int) (mouseX * mc.getWindow().getScaleFactor());
        int scaledMY = (int) (mouseY * mc.getWindow().getScaleFactor());

        RenderUtils.unscaledProjection();

        // Animated dimming background
        if (ClickGUI.background.getValue()) {
            bgAlpha = MathHelper.clamp(bgAlpha + delta * 8, 0, 180);
        } else {
            bgAlpha = MathHelper.clamp(bgAlpha - delta * 8, 0, 180);
        }
        if (bgAlpha > 0)
            context.fill(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(),
                    new Color(0, 0, 0, (int) bgAlpha).getRGB());

        // Smooth scroll
        moduleScrollY  = MathHelper.lerp(delta * 0.35f, moduleScrollY, targetScrollY);
        settingScrollY = MathHelper.lerp(delta * 0.35f, settingScrollY, settingTargetY);

        // If previous screen exists, render it behind
        if (Argon.INSTANCE.previousScreen != null)
            Argon.INSTANCE.previousScreen.render(context, 0, 0, delta);

        renderPanel(context, scaledMX, scaledMY, delta);

        RenderUtils.scaledProjection();
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPanel(DrawContext context, int mx, int my, float delta) {
        int r = ClickGUI.roundQuads.getValueInt();
        Color accent = Utils.getMainColor(255, 0);

        // ── Outer shadow / glow ────────────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(0, 0, 0, 60),
                panelX - 4, panelY - 4,
                panelX + PANEL_W + 4, panelY + PANEL_H + 4,
                r + 2, r + 2, r + 2, r + 2, 8);

        // ── Main panel body ────────────────────────────────────────────────────
        int windowAlpha = ClickGUI.alphaWindow.getValueInt();
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(12, 12, 14, windowAlpha),
                panelX, panelY,
                panelX + PANEL_W, panelY + PANEL_H,
                r, r, r, r, 8);

        // ── Tab bar ────────────────────────────────────────────────────────────
        renderTabs(context, mx, my, r);

        // ── Vertical divider between modules and settings ──────────────────────
        int divX = panelX + MODULE_COL_W;
        context.fill(divX, panelY + TAB_H, divX + 1, panelY + PANEL_H,
                new Color(255, 255, 255, 18).getRGB());

        // ── Module list ────────────────────────────────────────────────────────
        renderModuleList(context, mx, my, delta);

        // ── Settings panel ─────────────────────────────────────────────────────
        renderSettingsPanel(context, mx, my, delta);
    }

    // ── Tab bar ──────────────────────────────────────────────────────────────────

    private void renderTabs(DrawContext context, int mx, int my, int r) {
        Category[] cats = Category.values();
        int tabW = PANEL_W / cats.length;

        // Tab bar background
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(8, 8, 10, 200),
                panelX, panelY,
                panelX + PANEL_W, panelY + TAB_H,
                r, r, 0, 0, 8);

        // Bottom border
        context.fill(panelX, panelY + TAB_H - 1, panelX + PANEL_W, panelY + TAB_H,
                new Color(255, 255, 255, 20).getRGB());

        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            int tx = panelX + i * tabW;
            boolean active = cat == activeCategory;
            boolean hovered = mx >= tx && mx < tx + tabW && my >= panelY && my < panelY + TAB_H;

            // Hover highlight
            if (hovered && !active)
                context.fill(tx, panelY, tx + tabW, panelY + TAB_H - 1,
                        new Color(255, 255, 255, 10).getRGB());

            // Active indicator — gradient accent line at bottom of tab
            if (active) {
                Color c1 = Utils.getMainColor(200, i * 3);
                Color c2 = Utils.getMainColor(200, i * 3 + 3);
                context.fillGradient(tx + 10, panelY + TAB_H - 2, tx + tabW - 10, panelY + TAB_H,
                        c1.getRGB(), c2.getRGB());
                // Subtle active background tint
                context.fill(tx, panelY, tx + tabW, panelY + TAB_H - 2,
                        new Color(255, 255, 255, 8).getRGB());
            }

            // Tab label
            Color textCol = active ? Color.WHITE : new Color(160, 160, 165);
            TextRenderer.drawString(cat.name, context,
                    tx + tabW / 2 - TextRenderer.getWidth(cat.name) / 2, panelY + TAB_H / 2 + 4, textCol.getRGB());
        }
    }

    // ── Module list ───────────────────────────────────────────────────────────────

    private void renderModuleList(DrawContext context, int mx, int my, float delta) {
        List<Module> modules = Argon.INSTANCE.getModuleManager().getModulesInCategory(activeCategory);

        int listX  = panelX;
        int listY  = panelY + TAB_H;
        int listH  = PANEL_H - TAB_H;

        // Scissor clip for scrolling
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                listX, mc.getWindow().getFramebufferHeight() - (listY + listH),
                MODULE_COL_W, listH);

        int baseY = listY + PADDING - (int) moduleScrollY;

        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            int rowY = baseY + i * MODULE_ROW_H;

            if (rowY + MODULE_ROW_H < listY || rowY > listY + listH) continue;

            boolean hovered = mx >= listX && mx < listX + MODULE_COL_W
                    && my >= rowY && my < rowY + MODULE_ROW_H;
            boolean isActive = mod == activeModule;
            boolean enabled  = mod.isEnabled();

            // Row hover bg
            if (hovered || isActive)
                context.fill(listX, rowY, listX + MODULE_COL_W, rowY + MODULE_ROW_H,
                        new Color(255, 255, 255, isActive ? 14 : 8).getRGB());

            // Left accent line when enabled
            if (enabled) {
                Color c1 = Utils.getMainColor(255, i);
                Color c2 = Utils.getMainColor(255, i + 1);
                context.fillGradient(listX, rowY + 3, listX + 2, rowY + MODULE_ROW_H - 3,
                        c1.getRGB(), c2.getRGB());
            }

            // Module name — colored if enabled, grey if disabled
            Color nameColor = enabled
                    ? Utils.getMainColor(255, i)
                    : new Color(180, 180, 185);
            TextRenderer.drawString(mod.getName(), context,
                    listX + PADDING, rowY + MODULE_ROW_H / 2 + 4, nameColor.getRGB());

            // Settings arrow indicator (right side), shown when module has settings
            if (!mod.getSettings().isEmpty()) {
                Color arrowCol = isActive
                        ? Utils.getMainColor(200, i)
                        : new Color(100, 100, 105);
                TextRenderer.drawString(">", context,
                        listX + MODULE_COL_W - 14, rowY + MODULE_ROW_H / 2 + 4, arrowCol.getRGB());
            }

            // Separator line
            if (i < modules.size() - 1)
                context.fill(listX + PADDING, rowY + MODULE_ROW_H - 1,
                        listX + MODULE_COL_W - PADDING, rowY + MODULE_ROW_H,
                        new Color(255, 255, 255, 8).getRGB());
        }

        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
    }

    // ── Settings panel ────────────────────────────────────────────────────────────

    private void renderSettingsPanel(DrawContext context, int mx, int my, float delta) {
        int sx = panelX + SETTINGS_X + 1;
        int sy = panelY + TAB_H;
        int sw = SETTINGS_W - 1;
        int sh = PANEL_H - TAB_H;

        if (activeModule == null) {
            // Placeholder hint
            String hint = "< Select a module";
            TextRenderer.drawString(hint, context,
                    sx + sw / 2 - TextRenderer.getWidth(hint) / 2,
                    sy + sh / 2 + 4,
                    new Color(80, 80, 85).getRGB());
            return;
        }

        // Module title area
        int titleAreaH = 40;
        RenderUtils.renderRoundedQuad(context.getMatrices(),
                new Color(8, 8, 10, 160),
                sx, sy,
                sx + sw, sy + titleAreaH,
                0, 0, 0, 0, 4);

        // Module name large
        Color accent = Utils.getMainColor(255, 0);
        TextRenderer.drawString(activeModule.getName(), context,
                sx + PADDING, sy + titleAreaH / 2 + 4, Color.WHITE.getRGB());

        // Enabled badge
        boolean enabled = activeModule.isEnabled();
        Color badgeColor = enabled ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220) : new Color(60, 60, 65, 220);
        String badgeText = enabled ? "ON" : "OFF";
        int badgeW = TextRenderer.getWidth(badgeText) + 14;
        int badgeX = sx + sw - badgeW - PADDING;
        int badgeY = sy + (titleAreaH - 16) / 2;
        RenderUtils.renderRoundedQuad(context.getMatrices(), badgeColor,
                badgeX, badgeY, badgeX + badgeW, badgeY + 16, 3, 3, 3, 3, 6);
        TextRenderer.drawString(badgeText, context,
                badgeX + badgeW / 2 - TextRenderer.getWidth(badgeText) / 2, badgeY + 12, Color.WHITE.getRGB());

        // Title bottom border
        context.fillGradient(sx + PADDING, sy + titleAreaH - 1, sx + sw - PADDING, sy + titleAreaH,
                accent.getRGB(), new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30).getRGB());

        // ── Settings list ──────────────────────────────────────────────────────
        List<Setting<?>> settings = activeModule.getSettings();
        if (settings.isEmpty()) {
            TextRenderer.drawString("No settings", context,
                    sx + sw / 2 - TextRenderer.getWidth("No settings") / 2, sy + titleAreaH + 30 + 4,
                    new Color(80, 80, 85).getRGB());
            return;
        }

        com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                sx, mc.getWindow().getFramebufferHeight() - (sy + sh),
                sw, sh - titleAreaH);

        int baseY = sy + titleAreaH + PADDING - (int) settingScrollY;

        for (int i = 0; i < settings.size(); i++) {
            Setting<?> s = settings.get(i);
            int ry = baseY + i * SETTING_ROW_H;

            if (ry + SETTING_ROW_H < sy + titleAreaH || ry > sy + sh) continue;

            boolean rowHov = mx >= sx && mx < sx + sw && my >= ry && my < ry + SETTING_ROW_H;

            // Row background
            if (rowHov)
                context.fill(sx, ry, sx + sw, ry + SETTING_ROW_H,
                        new Color(255, 255, 255, 8).getRGB());

            renderSettingRow(context, mx, my, s, i, sx, ry, sw);

            // Separator
            if (i < settings.size() - 1)
                context.fill(sx + PADDING, ry + SETTING_ROW_H - 1,
                        sx + sw - PADDING, ry + SETTING_ROW_H,
                        new Color(255, 255, 255, 8).getRGB());
        }

        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
    }

    private void renderSettingRow(DrawContext context, int mx, int my,
                                   Setting<?> s, int idx, int sx, int ry, int sw) {
        Color accent1 = Utils.getMainColor(255, idx);
        Color accent2 = Utils.getMainColor(255, idx + 1);
        int midY = ry + SETTING_ROW_H / 2;

        if (s instanceof BooleanSetting bs) {
            // Name
            TextRenderer.drawString(s.getName(), context,
                    sx + PADDING, midY + 4, new Color(210, 210, 215).getRGB());

            // Toggle pill
            boolean val = bs.getValue();
            int pillW = 28; int pillH = 14;
            int pillX = sx + sw - pillW - PADDING;
            int pillY = midY - pillH / 2;

            Color pillBg = val
                    ? new Color(accent1.getRed(), accent1.getGreen(), accent1.getBlue(), 220)
                    : new Color(50, 50, 55, 220);
            RenderUtils.renderRoundedQuad(context.getMatrices(), pillBg,
                    pillX, pillY, pillX + pillW, pillY + pillH, 7, 7, 7, 7, 8);

            // Thumb
            int thumbX = val ? pillX + pillW - 11 : pillX + 3;
            RenderUtils.renderCircle(context.getMatrices(), Color.WHITE,
                    thumbX + 4, pillY + 7, 4, 12);

        } else if (s instanceof NumberSetting ns) {
            // Name + value
            String label = s.getName() + ": " + ns.getValue();
            TextRenderer.drawString(label, context,
                    sx + PADDING, ry + 7, new Color(210, 210, 215).getRGB());

            // Slim progress bar
            int barX  = sx + PADDING;
            int barY  = ry + SETTING_ROW_H - 7;
            int barW  = sw - PADDING * 2;
            int barH  = 3;

            context.fill(barX, barY, barX + barW, barY + barH,
                    new Color(40, 40, 45, 200).getRGB());

            double pct = (ns.getValue() - ns.getMin()) / (ns.getMax() - ns.getMin());
            int fillW = (int) (barW * pct);
            if (fillW > 0)
                context.fillGradient(barX, barY, barX + fillW, barY + barH,
                        accent1.getRGB(), accent2.getRGB());

            // Draggable knob
            double knobX = barX + fillW;
            if (s == draggingSettting || (mx >= knobX - 5 && mx <= knobX + 5 && my >= barY - 3 && my <= barY + barH + 3))
                RenderUtils.renderCircle(context.getMatrices(), Color.WHITE, knobX, barY + 1.5, 4, 10);

        } else if (s instanceof MinMaxSetting mms) {
            String label = s.getName() + ": " + mms.getMinValue() + " - " + mms.getMaxValue();
            TextRenderer.drawString(label, context,
                    sx + PADDING, ry + 7, new Color(210, 210, 215).getRGB());

            int barX = sx + PADDING;
            int barY = ry + SETTING_ROW_H - 7;
            int barW = sw - PADDING * 2;
            int barH = 3;

            context.fill(barX, barY, barX + barW, barY + barH,
                    new Color(40, 40, 45, 200).getRGB());

            double minPct = (mms.getMinValue() - mms.getMin()) / (mms.getMax() - mms.getMin());
            double maxPct = (mms.getMaxValue() - mms.getMin()) / (mms.getMax() - mms.getMin());
            int fillStartX = barX + (int) (barW * minPct);
            int fillEndX   = barX + (int) (barW * maxPct);

            if (fillEndX > fillStartX)
                context.fillGradient(fillStartX, barY, fillEndX, barY + barH,
                        accent1.getRGB(), accent2.getRGB());

            RenderUtils.renderCircle(context.getMatrices(), Color.WHITE, fillStartX, barY + 1.5, 4, 10);
            RenderUtils.renderCircle(context.getMatrices(), Color.WHITE, fillEndX,   barY + 1.5, 4, 10);

        } else if (s instanceof ModeSetting<?> ms) {
            TextRenderer.drawString(s.getName(), context,
                    sx + PADDING, midY + 4, new Color(210, 210, 215).getRGB());

            // Mode pill chip
            String modeName = ms.getMode().name();
            int chipW = TextRenderer.getWidth(modeName) + 16;
            int chipX = sx + sw - chipW - PADDING;
            int chipY = midY - 9;

            RenderUtils.renderRoundedQuad(context.getMatrices(),
                    new Color(accent1.getRed(), accent1.getGreen(), accent1.getBlue(), 50),
                    chipX, chipY, chipX + chipW, chipY + 18, 4, 4, 4, 4, 6);
            RenderUtils.renderRoundedOutline(context,
                    new Color(accent1.getRed(), accent1.getGreen(), accent1.getBlue(), 120),
                    chipX, chipY, chipX + chipW, chipY + 18, 4, 4, 4, 4, 1, 6);
            TextRenderer.drawString(modeName, context,
                    chipX + chipW / 2 - TextRenderer.getWidth(modeName) / 2, chipY + 13, accent1.getRGB());

        } else if (s instanceof KeybindSetting ks) {
            boolean listening = s == listeningKeybind;
            String keyLabel = listening ? "Press key..." : s.getName() + ": " + KeyUtils.getKey(ks.getKey());
            Color kc = listening ? accent1 : new Color(210, 210, 215);
            TextRenderer.drawString(keyLabel, context,
                    sx + PADDING, midY + 4, kc.getRGB());

        } else if (s instanceof StringSetting ss2) {
            String txt = s.getName() + ": " + (ss2.getValue().length() > 12
                    ? ss2.getValue().substring(0, 12) + "..." : ss2.getValue());
            TextRenderer.drawString(txt, context,
                    sx + PADDING, midY + 4, new Color(210, 210, 215).getRGB());
        }
    }

    // ── Mouse events ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) (mouseX * mc.getWindow().getScaleFactor());
        int my = (int) (mouseY * mc.getWindow().getScaleFactor());

        // Start panel drag
        if (isInTabBar(mx, my) && button == 0) {
            dragging = true;
            dragOffX = mx - panelX;
            dragOffY = my - panelY;
        }

        // Tab click
        Category[] cats = Category.values();
        int tabW = PANEL_W / cats.length;
        if (my >= panelY && my < panelY + TAB_H) {
            for (int i = 0; i < cats.length; i++) {
                int tx = panelX + i * tabW;
                if (mx >= tx && mx < tx + tabW) {
                    if (activeCategory != cats[i]) {
                        activeCategory = cats[i];
                        activeModule = null;
                        targetScrollY = 0;
                        settingTargetY = 0;
                    }
                    break;
                }
            }
        }

        // Module list click
        if (mx >= panelX && mx < panelX + MODULE_COL_W
                && my >= panelY + TAB_H && my < panelY + PANEL_H) {

            List<Module> modules = Argon.INSTANCE.getModuleManager().getModulesInCategory(activeCategory);
            int baseY = panelY + TAB_H + PADDING - (int) moduleScrollY;

            for (int i = 0; i < modules.size(); i++) {
                Module mod = modules.get(i);
                int rowY = baseY + i * MODULE_ROW_H;
                if (my >= rowY && my < rowY + MODULE_ROW_H) {
                    if (button == 0) mod.toggle();
                    if (button == 1) {
                        activeModule = (activeModule == mod) ? null : mod;
                        settingTargetY = 0;
                    }
                    break;
                }
            }
        }

        // Settings panel clicks
        if (activeModule != null
                && mx >= panelX + SETTINGS_X + 1 && mx < panelX + PANEL_W
                && my >= panelY + TAB_H + 40 && my < panelY + PANEL_H) {

            int sx   = panelX + SETTINGS_X + 1;
            int sw   = SETTINGS_W - 1;
            int baseY = panelY + TAB_H + 40 + PADDING - (int) settingScrollY;
            List<Setting<?>> settings = activeModule.getSettings();

            for (int i = 0; i < settings.size(); i++) {
                Setting<?> s = settings.get(i);
                int ry = baseY + i * SETTING_ROW_H;

                if (my < ry || my >= ry + SETTING_ROW_H) continue;

                if (s instanceof BooleanSetting bs && button == 0) {
                    // Check pill click
                    int pillW = 28;
                    int pillX = sx + sw - pillW - PADDING;
                    int midY  = ry + SETTING_ROW_H / 2;
                    int pillY = midY - 7;
                    if (mx >= pillX && mx <= pillX + pillW && my >= pillY && my <= pillY + 14)
                        bs.toggle();
                    else
                        bs.toggle(); // click anywhere on row

                } else if (s instanceof NumberSetting ns && button == 0) {
                    draggingSettting = s;
                    sliderDragging = true;
                    slideNumber(ns, mx, sx, sw);

                } else if (s instanceof MinMaxSetting mms && button == 0) {
                    draggingSettting = s;
                    double barX  = sx + PADDING;
                    double barW  = sw - PADDING * 2;
                    double minPx = barX + (mms.getMinValue() - mms.getMin()) / (mms.getMax() - mms.getMin()) * barW;
                    double maxPx = barX + (mms.getMaxValue() - mms.getMin()) / (mms.getMax() - mms.getMin()) * barW;
                    if (Math.abs(mx - minPx) < Math.abs(mx - maxPx)) {
                        minSliderDragging = true;
                        slideMinMax(mms, mx, sx, sw, true);
                    } else {
                        maxSliderDragging = true;
                        slideMinMax(mms, mx, sx, sw, false);
                    }

                } else if (s instanceof ModeSetting<?> ms && button == 0) {
                    ms.cycle();

                } else if (s instanceof KeybindSetting ks) {
                    if (button == 0) {
                        listeningKeybind = (listeningKeybind == s) ? null : s;
                    }
                }
                break;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        int mx = (int) (mouseX * mc.getWindow().getScaleFactor());
        int my = (int) (mouseY * mc.getWindow().getScaleFactor());

        if (dragging && button == 0) {
            panelX = (int) (mx - dragOffX);
            panelY = (int) (my - dragOffY);
        }

        if (sliderDragging && draggingSettting instanceof NumberSetting ns) {
            int sx = panelX + SETTINGS_X + 1;
            int sw = SETTINGS_W - 1;
            slideNumber(ns, mx, sx, sw);
        }

        if (draggingSettting instanceof MinMaxSetting mms) {
            int sx = panelX + SETTINGS_X + 1;
            int sw = SETTINGS_W - 1;
            if (minSliderDragging) slideMinMax(mms, mx, sx, sw, true);
            if (maxSliderDragging && !minSliderDragging) slideMinMax(mms, mx, sx, sw, false);
        }

        return super.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
            sliderDragging = minSliderDragging = maxSliderDragging = false;
            draggingSettting = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
        int mx = (int) (mouseX * mc.getWindow().getScaleFactor());
        int my = (int) (mouseY * mc.getWindow().getScaleFactor());

        int speed = 20;
        if (mx >= panelX && mx < panelX + MODULE_COL_W && my >= panelY + TAB_H && my < panelY + PANEL_H) {
            List<Module> modules = Argon.INSTANCE.getModuleManager().getModulesInCategory(activeCategory);
            int maxScroll = Math.max(0, modules.size() * MODULE_ROW_H - (PANEL_H - TAB_H) + PADDING * 2);
            targetScrollY = (float) MathHelper.clamp(targetScrollY - vAmt * speed, 0, maxScroll);
        }

        if (activeModule != null && mx >= panelX + SETTINGS_X && mx < panelX + PANEL_W
                && my >= panelY + TAB_H && my < panelY + PANEL_H) {
            List<Setting<?>> settings = activeModule.getSettings();
            int maxScroll = Math.max(0, settings.size() * SETTING_ROW_H - (PANEL_H - TAB_H - 40) + PADDING * 2);
            settingTargetY = (float) MathHelper.clamp(settingTargetY - vAmt * speed, 0, maxScroll);
        }

        return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Keybind listening
        if (listeningKeybind instanceof KeybindSetting ks) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listeningKeybind = null;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                ks.setKey(ks.getOriginalKey());
                listeningKeybind = null;
            } else {
                if (ks.isModuleKey() && activeModule != null)
                    activeModule.setKey(keyCode);
                ks.setKey(keyCode);
                listeningKeybind = null;
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private boolean isInTabBar(int mx, int my) {
        return mx >= panelX && mx < panelX + PANEL_W && my >= panelY && my < panelY + TAB_H;
    }

    private void slideNumber(NumberSetting ns, int mx, int sx, int sw) {
        double barX = sx + PADDING;
        double barW = sw - PADDING * 2;
        double pct  = MathHelper.clamp((mx - barX) / barW, 0, 1);
        ns.setValue(MathUtils.roundToDecimal(pct * (ns.getMax() - ns.getMin()) + ns.getMin(), ns.getIncrement()));
    }

    private void slideMinMax(MinMaxSetting mms, int mx, int sx, int sw, boolean isMin) {
        double barX = sx + PADDING;
        double barW = sw - PADDING * 2;
        double pct  = MathHelper.clamp((mx - barX) / barW, 0, 1);
        double val  = MathUtils.roundToDecimal(pct * (mms.getMax() - mms.getMin()) + mms.getMin(), mms.getIncrement());
        if (isMin) mms.setMinValue(val); else mms.setMaxValue(val);
    }
}
