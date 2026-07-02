
package pl.syntaxdevteam;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import pl.syntaxdevteam.listeners.*;

                public class Main {
                    public static void main(String[] args) {
                        System.out.println("[System] Uruchamianie bota Econizer...");

                        // 1. Inicjalizacja bazy danych i utworzenie tabel
                        DatabaseManager db = new DatabaseManager();
                        db.connect();

                        // 2. Token Twojego Bota Discord
                        // Upewnij się, że nie ma tu żadnych spacji ani polskich znaków
                        String token = "MTUwNjM2MjA0ODg2MTMxMTI5Nw.GUzuCa.Tly0aXCl_xjmKr6LWyYvR5ibr-F0u6HRAq8oJg";

                        try {
                            JDABuilder builder = JDABuilder.createDefault(token);

                            // Wymagane intencje do czytania wiadomości i nadawania ról
                            builder.enableIntents(
                                    GatewayIntent.GUILD_MESSAGES,
                                    GatewayIntent.MESSAGE_CONTENT,
                                    GatewayIntent.GUILD_MEMBERS
                            );

                            // Rejestracja wszystkich naszych modułów
                            builder.addEventListeners(
                                    new CoreManager(db),
                                    new EconomyManager(db),
                                    new CrimeManager(db),
                                    new MarketManager(db),
                                    new PetManager(db),
                                    new CompanyManager(db),
                                    new LevelingManager(db),
                                    new ModerationManager(db)
                            );

                            builder.build().awaitReady();
                            System.out.println("[System] Bot jest online i gotowy do działania!");

                        } catch (InterruptedException e) {
                            System.err.println("[System Błąd] Przerwano uruchamianie bota!");
                            e.printStackTrace();
                        }
                    }
                }