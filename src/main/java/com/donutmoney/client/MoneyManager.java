package com.donutmoney.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class MoneyManager {
    private static final Map<String, Double> moneyCache = new ConcurrentHashMap<>();
    private static final Map<String, String> formattedMoneyCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastFetchTime = new ConcurrentHashMap<>();
    private static final Set<String> pendingFetches = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static volatile long lastInvalidKeyWarnAt = 0L;
    private static final int MAX_PENDING_FETCHES = 25;
    private static final long CACHE_EXPIRE_MS_SLOW = 180_000; // 3 minutes cache for distant players
    private static final long CACHE_EXPIRE_MS_FAST = 15_000;  // 15 seconds cache for nearby players
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)) // Increased timeout
            .build();
    private static final Gson gson = new Gson();
    
    public static String getFormattedMoney(String playerName, boolean fastUpdate) {
        getMoney(playerName, fastUpdate);
        String cached = formattedMoneyCache.get(playerName);
        if (cached != null && !cached.isEmpty()) return cached;
        Double money = moneyCache.get(playerName);
        if (money == null) return null;
        String formatted = formatMoney(money);
        formattedMoneyCache.put(playerName, formatted);
        return formatted;
    }

    public static Double getMoney(String playerName, boolean fastUpdate) {
        checkAndFetch(playerName, fastUpdate);
        return moneyCache.get(playerName);
    }
    
    // Overload for backward compatibility or simple calls
    public static Double getMoney(String playerName) {
        return getMoney(playerName, false);
    }

    private static void checkAndFetch(String playerName, boolean fastUpdate) {
        long now = System.currentTimeMillis();
        long cacheExpire = fastUpdate ? CACHE_EXPIRE_MS_FAST : CACHE_EXPIRE_MS_SLOW;

        // If not in cache, or expired, queue a fetch
        // Check if we have both money and shards (or at least attempted fetch)
        boolean hasData = moneyCache.containsKey(playerName); 
        
        if (!hasData || (now - lastFetchTime.getOrDefault(playerName, 0L) > cacheExpire)) {
            // Only fetch if not already pending and haven't tried recently (10s cooldown)
            if (!pendingFetches.contains(playerName) && (now - lastFetchTime.getOrDefault(playerName, 0L) > 10_000)) {
                if (pendingFetches.size() >= MAX_PENDING_FETCHES) {
                    if (DebugLog.rateLimit("donutmoney:skip_fetch_overload", 5000)) {
                        DonutMoneyClient.LOGGER.info("[DonutMoney][Debug] Skipping fetch: too many pending requests ({}).", pendingFetches.size());
                    }
                    return;
                }
                lastFetchTime.put(playerName, now);
                fetchMoney(playerName);
            }
        }
    }

    private static void fetchMoney(String playerName) {
        pendingFetches.add(playerName);
        executor.submit(() -> {
            try {
                String apiKey = "";
                try {
                apiKey = ModConfig.get().donutApiKey;
            } catch (Exception e) {
                // Ignore config load errors
            }

                if (apiKey == null || apiKey.trim().isEmpty()) {
                    apiKey = "860676e56e9c40f7801643012386c678";
                }
                
                String encodedName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.donutsmp.net/v1/stats/" + encodedName))
                        .timeout(Duration.ofSeconds(5))
                        .GET();
                
                if (apiKey != null && !apiKey.isEmpty()) {
                    reqBuilder.header("Authorization", "Bearer " + apiKey);
                }

                HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    // System.out.println("[DonutMoney] API Success for " + playerName);
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    // Handle different possible response structures
                    double money = 0;
                    boolean found = false;

                    // Helper to extract stats from an object
                    JsonObject statsObj = null;

                    if (json.has("result") && json.get("result").isJsonObject()) {
                        statsObj = json.getAsJsonObject("result");
                    } else if (json.has("data") && json.get("data").isJsonObject()) {
                        statsObj = json.getAsJsonObject("data");
                    } else if (json.has("money")) {
                        statsObj = json;
                    }

                    if (statsObj != null) {
                        if (statsObj.has("money")) {
                            money = parseMoney(statsObj.get("money"));
                            found = true;
                        }
                    }

                    if (found) {
                        moneyCache.put(playerName, money);
                        formattedMoneyCache.put(playerName, formatMoney(money));
                    } else {
                        DonutMoneyClient.LOGGER.warn("Stats field not found in response for {}", playerName);
                    }
                } else if (response.statusCode() == 401) {
                    DonutMoneyClient.LOGGER.error("Failed to fetch stats for {}: HTTP 401 Unauthorized (Invalid API Key)", playerName);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        long now = System.currentTimeMillis();
                        if (now - lastInvalidKeyWarnAt > 60_000) {
                            lastInvalidKeyWarnAt = now;
                            mc.execute(() -> {
                                mc.player.displayClientMessage(Component.literal("Donut Money Display: Invalid API Key. Use /moneyapikey <key> (client command) or edit config/donutmoneydisplay.json").withStyle(ChatFormatting.RED), false);
                            });
                        }
                    }
                } else {
                    DonutMoneyClient.LOGGER.warn("Failed to fetch stats for {}: HTTP {}", playerName, response.statusCode());
                }
            } catch (Exception e) {
                DonutMoneyClient.LOGGER.error("Error fetching stats for {}: {}", playerName, e.getMessage());
            } finally {
                pendingFetches.remove(playerName);
            }
        });
    }

    private static double parseMoney(JsonElement element) {
        try {
            if (element.isJsonNull()) return 0.0;
            String str = element.getAsString();
            if (str == null || str.trim().isEmpty()) return 0.0;
            // Remove commas if present
            str = str.replace(",", "");
            return Double.parseDouble(str);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static String formatMoney(double amount) {
        if (amount >= 1_000_000_000_000.0) {
            return String.format("%.1fT", amount / 1_000_000_000_000.0);
        } else if (amount >= 1_000_000_000) {
            return String.format("%.1fB", amount / 1_000_000_000.0);
        } else if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return String.format("%.1fK", amount / 1_000.0);
        } else {
            return String.format("%.0f", amount);
        }
    }
}



