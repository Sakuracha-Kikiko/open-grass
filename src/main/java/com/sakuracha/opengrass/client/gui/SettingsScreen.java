package com.sakuracha.opengrass.client.gui;

import com.sakuracha.opengrass.Config;
import com.sakuracha.opengrass.LBGMode;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the Open Grass settings screen.
 *
 * @author LambdAurora, Sakuracha Kikiko
 */
public final class SettingsScreen extends Screen {
    private static final String API_URL = "https://lambdaurora.dev/projects/lambdabettergrass/documentation/";

    private final @Nullable Screen parent;
    private List<FormattedCharSequence> infoLines = List.of();
    private FormattedCharSequence readMoreLine = FormattedCharSequence.EMPTY;
    private int readMoreTop;
    private int readMoreLeft;
    private int readMoreRight;

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("open_grass.menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonHeight = 20;

        this.addRenderableWidget(Button.builder(this.modeText(), button -> {
                    Config.setMode(Config.mode().next());
                    this.reload();
                    button.setMessage(this.modeText());
                })
                .bounds(this.width / 2 - 205, this.height / 4 - buttonHeight, 200, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("open_grass.tooltip.mode",
                        LBGMode.OFF.getText(), LBGMode.FASTEST.getText(), LBGMode.FAST.getText(), LBGMode.FANCY.getText())))
                .build());

        this.addRenderableWidget(Button.builder(this.betterSnowText(), button -> {
                    Config.setBetterSnow(!Config.betterSnow());
                    this.reload();
                    button.setMessage(this.betterSnowText());
                })
                .bounds(this.width / 2 + 5, this.height / 4 - buttonHeight, 200, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("open_grass.tooltip.better_snow")))
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("controls.reset"), button -> {
                    Config.reset();
                    this.reload();
                    this.rebuildWidgets();
                })
                .bounds(this.width / 2 - 155, this.height - 29, 150, buttonHeight)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 155 + 160, this.height - 29, 150, buttonHeight)
                .build());

        this.buildLabels();
    }

    private Component modeText() {
        return Component.translatable("open_grass.option.mode").append(": ").append(Config.mode().getText());
    }

    private Component betterSnowText() {
        return Component.translatable("open_grass.option.better_snow").append(": ")
                .append(Component.translatable(Config.betterSnow() ? "options.on" : "options.off")
                        .withStyle(Config.betterSnow() ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private void buildLabels() {
        var text = Component.empty();
        text.append(Component.translatable("open_grass.menu.title.info").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        text.append("\n");
        text.append(Component.translatable("open_grass.menu.info.1")).append("\n");
        text.append(Component.translatable("open_grass.menu.info.2")).append(" ");
        text.append(Component.translatable("open_grass.menu.info.3"));

        this.infoLines = this.font.split(text, Math.max(100, this.width - 80));

        int y = this.height / 2;

        Component readMore = Component.translatable("open_grass.menu.info.read_more", "[lambdaurora.dev]")
                .withStyle(ChatFormatting.GREEN);
        this.readMoreLine = readMore.getVisualOrderText();
        this.readMoreTop = y + this.infoLines.size() * this.font.lineHeight + 5;
        int width = this.font.width(readMore);
        this.readMoreLeft = this.width / 2 - width / 2;
        this.readMoreRight = this.readMoreLeft + width;
    }

    private void reload() {
        var client = Minecraft.getInstance();
        if (client.levelRenderer != null) {
            client.levelRenderer.allChanged();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int y = this.height / 2;
        for (var line : this.infoLines) {
            graphics.drawCenteredString(this.font, line, this.width / 2, y, 0xFFFFFF);
            y += this.font.lineHeight;
        }

        graphics.drawCenteredString(this.font, this.readMoreLine, this.width / 2, this.readMoreTop, 0x55FF55);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= this.readMoreTop && mouseY <= this.readMoreTop + this.font.lineHeight
                && mouseX >= this.readMoreLeft && mouseX <= this.readMoreRight) {
            Util.getPlatform().openUri(API_URL);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
