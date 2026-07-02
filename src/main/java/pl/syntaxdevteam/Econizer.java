package pl.syntaxdevteam;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import pl.syntaxdevteam.listeners.*;

public class Econizer {
    public static void main(String[] args) {
        System.out.println("[System] Uruchamianie bota Econizer...");

        DatabaseManager db = new DatabaseManager();
        db.connect();

        String token = loadDiscordToken();
        if (token == null) {
            System.err.println("[System Błąd] Brak tokenu Discord!");
            System.err.println("Utwórz plik .env (na podstawie .env.example) i ustaw DISCORD_TOKEN");
            System.exit(1);
        }

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

    private static String loadDiscordToken() {
        String token = System.getenv("DISCORD_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        token = dotenv.get("DISCORD_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        return null;
    }
}
