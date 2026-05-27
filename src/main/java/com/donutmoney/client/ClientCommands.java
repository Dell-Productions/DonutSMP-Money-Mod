package com.donutmoney.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ClientCommands {
    private ClientCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("moneyapikey")
                .then(argument("key", StringArgumentType.greedyString())
                    .executes((ctx) -> {
                        String key = StringArgumentType.getString(ctx, "key");
                        key = String.valueOf(key == null ? "" : key).trim();
                        if (key.isEmpty()) return 0;
                        ModConfig cfg = ModConfig.get();
                        cfg.donutApiKey = key;
                        ModConfig.save();
                        LeaderboardManager.forceUpdate();
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.displayClientMessage(Component.literal("Donut Money Display: saved API key.").withStyle(ChatFormatting.GREEN), false);
                        }
                        return 1;
                    })
                )
                .executes((ctx) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("Usage: /moneyapikey <key>").withStyle(ChatFormatting.YELLOW), false);
                    }
                    return 1;
                })
            );
        });
    }
}

