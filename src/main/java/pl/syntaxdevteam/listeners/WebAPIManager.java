


      package pl.syntaxdevteam.listeners;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

                public class WebAPIManager {

                    private static final String BASE_URL = "https://econizer.syntaxdevteam.pl";
                    private static final String SECRET_TOKEN = "o$aaGoE@&srHgz2Zg9%5e*7QM@Hs9!Sa"; // ZMIEŃ TO!

                    private static final HttpClient httpClient = HttpClient.newHttpClient();

                    public static void reportGuildAction(String guildId, String guildName, String action) {
                        String url = BASE_URL + "/api/econizer/guilds";
                        String jsonPayload = String.format(
                                "{\"guild_id\":\"%s\",\"name\":\"%s\",\"action\":\"%s\"}",
                                guildId, guildName.replace("\"", "\\\""), action
                        );
                        sendPostRequest(url, jsonPayload);
                    }

                    public static void reportEconomyEvent(String guildId, String userId, String type, int amount, int exp, int level, String description) {
                        String url = BASE_URL + "/api/econizer/events";
                        String eventId = UUID.randomUUID().toString();

                        String jsonPayload = String.format(
                                "{\"event_id\":\"%s\",\"guild_id\":\"%s\",\"user_id\":\"%s\",\"type\":\"%s\",\"amount\":%d,\"experience\":%d,\"level\":%d,\"description\":\"%s\"}",
                                eventId, guildId, userId, type, amount, exp, level, description.replace("\"", "\\\"")
                        );
                        sendPostRequest(url, jsonPayload);
                    }

                    private static void sendPostRequest(String url, String jsonPayload) {
                        new Thread(() -> {
                            try {
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(url))
                                        .header("X-Econizer-Token", SECRET_TOKEN)
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                                        .build();

                                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                                // Opcjonalnie: logowanie w konsoli (możesz to wyciszyć, jeśli robi za dużo spamu)
                                // System.out.println("[WebAPI] Wysłano zapytanie do: " + url + " | Status: " + response.statusCode());

                            } catch (Exception e) {
                                System.err.println("[WebAPI Błąd] Nie udało się połączyć ze stroną WWW!");
                            }
                        }).start();
                    }
                }