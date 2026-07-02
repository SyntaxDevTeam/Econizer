package pl.syntaxdevteam;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import pl.syntaxdevteam.listeners.*;

public class Econizer {
    public static void main(String[] args) {
        System.out.println("[System] Uruchamianie bota Econizer...");

        DatabaseManager db = new DatabaseManager();
        db.connect();

        String token = "MTUwNjM2MjA0ODg2MTMxMTI5Nw.GUzuCa.Tly0aXCl_xjmKr6LWyYvR5ibr-F0u6HRAq8oJg";

        try {
            JDABuilder builder = JDABuilder.createDefault(token);

            builder.enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS
            );

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
