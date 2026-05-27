package com.donutmoney.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("donutmoneydisplay.json").toFile();
    private static final File BACKUP_FILE = FabricLoader.getInstance().getConfigDir().resolve("donutmoneydisplay.backup.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE;

    public String donutApiKey = "860676e56e9c40f7801643012386c678";
    public int scalePercent = 70;
    public boolean showBackground = true;
    public double yOffset = 12.0;
    public boolean showMoney = true;
    public boolean showLeaderboardRank = true;
    public boolean debugLog = false;

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                System.err.println("Failed to load config: " + e.getMessage());
            }
        }
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
            save();
        }
    }
    
    public static void save() {
        if (INSTANCE == null) return;
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
        try (FileWriter writer = new FileWriter(BACKUP_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            System.err.println("Failed to save backup config: " + e.getMessage());
        }
    }
}
