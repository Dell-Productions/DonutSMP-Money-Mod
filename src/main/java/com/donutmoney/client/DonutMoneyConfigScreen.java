package com.donutmoney.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DonutMoneyConfigScreen extends Screen {
    private final Screen parent;
    private EditBox apiInput;

    public DonutMoneyConfigScreen(Screen parent) {
        super(Component.literal("Donut Money Display"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = this.width;
        int labelY = 60;
        int inputY = 78;
        int inputW = Math.min(320, w - 40);
        int inputX = (w - inputW) / 2;

        this.apiInput = new EditBox(this.font, inputX, inputY, inputW, 20, Component.literal("DonutSMP API"));
        String cur = "";
        try {
            ModConfig cfg = ModConfig.get();
            cur = cfg == null ? "" : String.valueOf(cfg.donutApiKey == null ? "" : cfg.donutApiKey);
        } catch (Throwable ignored) {
        }
        this.apiInput.setValue(cur);
        this.apiInput.setMaxLength(256);
        this.addRenderableWidget(this.apiInput);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), (b) -> {
            saveAndClose();
        }).bounds((w / 2) - 105, this.height - 40, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), (b) -> {
            onClose();
        }).bounds((w / 2) + 5, this.height - 40, 100, 20).build());

        this.setInitialFocus(this.apiInput);
    }

    private void saveAndClose() {
        String v = this.apiInput == null ? "" : String.valueOf(this.apiInput.getValue());
        v = v.trim();
        if (!v.isEmpty()) {
            ModConfig cfg = ModConfig.get();
            cfg.donutApiKey = v;
            ModConfig.save();
            LeaderboardManager.forceUpdate();
        }
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        guiGraphics.drawCenteredString(this.font, Component.literal("DonutSMP API:"), this.width / 2, 60, 0xFFFFFF);
    }
}

