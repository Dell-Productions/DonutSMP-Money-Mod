package com.donutmoney.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DonutMoneyClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("donutmoneydisplay");

    @Override
    public void onInitializeClient() {
        LOGGER.info("DonutMoneyClient initializing...");
        ModConfig.load();
        ClientCommands.register();

        LOGGER.info("DonutMoneyClient initialized.");
    }
}
