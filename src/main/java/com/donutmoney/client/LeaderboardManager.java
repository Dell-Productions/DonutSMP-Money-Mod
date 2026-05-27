package com.donutmoney.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LeaderboardManager {
    public record RankData(String type, int rank, int color, String symbol) {}

    private static final Map<String, Integer> moneyRanks = new ConcurrentHashMap<>();
    private static final Map<String, Integer> shardsRanks = new ConcurrentHashMap<>();
    private static final Map<String, Integer> sellRanks = new ConcurrentHashMap<>();
    
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final Gson gson = new Gson();
    
    private static long lastUpdate = 0;
    private static final long UPDATE_INTERVAL = 1000 * 60 * 5; // 5 minutes

    public static void tick() {
        long now = System.currentTimeMillis();
        // Initial update
        if (lastUpdate == 0) {
             lastUpdate = now;
             update();
             return;
        }
        if (now - lastUpdate > UPDATE_INTERVAL) {
            lastUpdate = now;
            update();
        }
    }

    public static void forceUpdate() {
        lastUpdate = System.currentTimeMillis();
        update();
    }

    public static void update() {
        executor.submit(() -> {
            updateLeaderboard("money", moneyRanks);
            updateLeaderboard("shards", shardsRanks);
            updateLeaderboard("sell", sellRanks);
        });
    }

    private static void updateLeaderboard(String type, Map<String, Integer> targetMap) {
        Map<String, Integer> newRanks = new ConcurrentHashMap<>();
        boolean success = false;

        String defaultKey = "860676e56e9c40f7801643012386c678";
        String apiKey = "";
        try { 
            apiKey = ModConfig.get().donutApiKey; 
        } catch (Exception e) {}
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = defaultKey;
        }

        // 1. Try API with current key
        success = fetchFromApi(type, newRanks, apiKey);

        // 2. If failed and using custom key, try default key
        if (!success && !apiKey.equals(defaultKey)) {
            success = fetchFromApi(type, newRanks, defaultKey);
        }

        // 3. If still failed and it's money, try GitHub as last resort
        if (!success && type.equals("money")) {
            success = fetchFromGitHub(newRanks);
        }

        // Only update the main map if we successfully fetched data
        if (success && !newRanks.isEmpty()) {
            targetMap.clear();
            targetMap.putAll(newRanks);
        }
    }

    private static boolean fetchFromGitHub(Map<String, Integer> rankMap) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://raw.githubusercontent.com/stashya/donutsmp-leaderboard/main/top1000.json"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json.has("players") && json.get("players").isJsonArray()) {
                        JsonArray list = json.getAsJsonArray("players");
                        int rank = 1;
                        for (JsonElement el : list) {
                            if (el.isJsonObject()) {
                                JsonObject obj = el.getAsJsonObject();
                                String name = null;
                                if (obj.has("username")) name = obj.get("username").getAsString();
                                else if (obj.has("name")) name = obj.get("name").getAsString();
                                
                                if (name != null) {
                                    rankMap.put(name.toLowerCase(), rank);
                                }
                            }
                            rank++;
                        }
                        return true;
                    }
                }
            } catch (Exception e) {
                DonutMoneyClient.LOGGER.error("Error fetching leaderboard from GitHub: {}", e.getMessage());
            }
            return false;
        }
    
        private static boolean fetchFromApi(String type, Map<String, Integer> rankMap, String apiKey) {
            boolean anySuccess = false;
            int currentRank = 1;
            
        for (int page = 1; page <= 100; page++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.donutsmp.net/v1/leaderboards/" + type + "/" + page))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
    
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        
                        JsonArray list = null;
                        if (json.has("result") && json.get("result").isJsonArray()) {
                             list = json.getAsJsonArray("result");
                        } else if (json.has("players") && json.get("players").isJsonArray()) {
                             list = json.getAsJsonArray("players");
                        }
                        
                        if (list != null && list.size() > 0) {
                            anySuccess = true;
                            for (JsonElement el : list) {
                                if (currentRank > 1000) break;
                                if (el.isJsonObject()) {
                                    JsonObject obj = el.getAsJsonObject();
                                    String name = null;
                                    if (obj.has("name")) name = obj.get("name").getAsString();
                                    else if (obj.has("username")) name = obj.get("username").getAsString();
                                    else if (obj.has("display_name")) name = obj.get("display_name").getAsString();
                                    else if (obj.has("player")) name = obj.get("player").getAsString();
                                    else if (obj.has("key")) name = obj.get("key").getAsString();
                                    
                                    if (name != null) {
                                        rankMap.put(name.toLowerCase(), currentRank);
                                    }
                                }
                                currentRank++;
                            }
                            if (currentRank > 1000) break;
                        } else {
                            // Empty list means no more pages
                            break;
                        }
                    } else {
                         DonutMoneyClient.LOGGER.warn("API Error {} for {} page {}", response.statusCode(), type, page);
                         break; 
                    }
                    
                    // Be nice to the API
                    try { Thread.sleep(200); } catch (InterruptedException e) {}
                    
                } catch (Exception e) {
                    DonutMoneyClient.LOGGER.error("Error fetching leaderboard {} page {}: {}", type, page, e.getMessage());
                    break;
                }
            }
            return anySuccess;
        }

    public static List<RankData> getPlayerRanks(String playerName) {
        List<RankData> ranks = new ArrayList<>();
        
        Integer mRank = moneyRanks.get(playerName.toLowerCase());
        if (mRank != null && mRank <= 1000) {
            ranks.add(new RankData("Money", mRank, 0xFF55FF55, "$"));
        }
        
        Integer sRank = shardsRanks.get(playerName.toLowerCase());
        if (sRank != null && sRank <= 1000) {
            ranks.add(new RankData("Shards", sRank, 0xFFB44CFF, "★"));
        }
        
        Integer slRank = sellRanks.get(playerName.toLowerCase());
        if (slRank != null && slRank <= 1000) {
            ranks.add(new RankData("Sell", slRank, 0xFFFFAA00, "⛃"));
        }
        
        // Sort by rank (lowest number is highest rank)
        ranks.sort(Comparator.comparingInt(RankData::rank));
        
        return ranks;
    }

    public static String getRankText(String playerName) {
        // Deprecated, use getPlayerRanks for rendering
        return null; 
    }
}
