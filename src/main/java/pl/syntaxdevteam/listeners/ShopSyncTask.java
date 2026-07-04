package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShopSyncTask {
    private final JDA jda;
    private final DatabaseManager db;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient client = HttpClient.newHttpClient();

    private final String apiToken = WebAPIManager.getApiToken();
    private final String baseUrl = WebAPIManager.BASE_URL;

    public ShopSyncTask(JDA jda, DatabaseManager db) { this.jda = jda; this.db = db; }

    public void startPolling() {
        scheduler.scheduleAtFixedRate(() -> {
            for (Guild guild : jda.getGuilds()) fetchAndFulfillOrders(guild);
        }, 10, 60, TimeUnit.SECONDS);
    }

    private void fetchAndFulfillOrders(Guild guild) {
        if (apiToken == null) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/econizer/shop/orders?guild_id=" + guild.getId())).header("X-Econizer-Token", apiToken).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isEmpty() && !response.body().equals("[]")) {
                processOrdersJson(guild, response.body());
            }
        } catch (Exception ignored) {}
    }

    private void processOrdersJson(Guild guild, String json) {
        Pattern pattern = Pattern.compile("\\{\"order_id\":(\\d+),\"discord_user_id\":\"(\\d+)\",\"delivery_type\":\"([^\"]+)\",\"delivery_reference\":\"([^\"]+)\"\\}");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String orderId = matcher.group(1); String userId = matcher.group(2); String type = matcher.group(3); String reference = matcher.group(4);
            boolean success = false;

            if (type.equals("discord_role")) {
                Role role = guild.getRoleById(reference);
                if (role != null) {
                    guild.retrieveMemberById(userId).queue(member -> { guild.addRoleToMember(member, role).queue(); }, err -> {});
                    success = true;
                }
            } else if (type.equals("virtual_item")) { success = true; } // Item dla bota

            if (success) confirmFulfillment(orderId);
        }
    }

    private void confirmFulfillment(String orderId) {
        if (apiToken == null) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/econizer/shop/orders/fulfill")).header("X-Econizer-Token", apiToken).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"order_id\":" + orderId + "}")).build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }
}
