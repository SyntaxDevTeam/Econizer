package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.sql.*;

public class CompanyManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final int COMPANY_CREATION_COST = 25000;

    public CompanyManager(DatabaseManager db) {
        this.db = db;
    }

    public static void handleEmployeeWork(Connection conn, String guildId, String userId, int employeeEarnings) {
        if (conn == null) return;
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT company_id FROM bot_employees WHERE guild_id = ? AND user_id = ?")) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int companyId = rs.getInt("company_id");
                int bonus = (int) (employeeEarnings * 0.25);
                try (PreparedStatement u = conn.prepareStatement("UPDATE bot_companies SET vault = vault + ? WHERE id = ?")) {
                    u.setInt(1, bonus);
                    u.setInt(2, companyId);
                    u.executeUpdate();
                }
            }
        } catch (SQLException ignored) {}
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("firma")) return;
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (!settings.economyEnabled) {
            event.reply(LanguageManager.t(settings, "eco_disabled")).setEphemeral(true).queue();
            return;
        }

        db.logCommandUsage("firma", guildId);
        Connection conn = db.getConnection();

        try {
            String sql = "SELECT c.name, c.owner_id, c.vault, c.split_owner, c.split_emp, c.id FROM bot_employees e JOIN bot_companies c ON e.company_id = c.id WHERE e.guild_id = ? AND e.user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    String name = rs.getString("name");
                    String owner = rs.getString("owner_id");
                    int vault = rs.getInt("vault");
                    int sOwner = rs.getInt("split_owner");
                    int sEmp = rs.getInt("split_emp");
                    int cId = rs.getInt("id");
                    int emps = 0;

                    try (PreparedStatement c = conn.prepareStatement("SELECT COUNT(*) FROM bot_employees WHERE company_id = ?")) {
                        c.setInt(1, cId);
                        ResultSet crs = c.executeQuery();
                        if (crs.next()) emps = crs.getInt(1);
                    }

                    EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#34495E"))
                            .setAuthor(LanguageManager.t(settings, "comp_employee_author", name), null, event.getUser().getEffectiveAvatarUrl())
                            .setDescription(LanguageManager.t(settings, "comp_employee_desc"))
                            .addField(LanguageManager.t(settings, "comp_field_owner"), "<@" + owner + ">", true)
                            .addField(LanguageManager.t(settings, "comp_field_staff"),
                                    LanguageManager.t(settings, "comp_field_staff_value", emps), true)
                            .addField(LanguageManager.t(settings, "comp_field_vault"), "**" + vault + "** " + settings.currencyEmoji, false)
                            .addField(LanguageManager.t(settings, "comp_field_splits"),
                                    LanguageManager.t(settings, "comp_field_splits_value", sOwner, sEmp), false);

                    if (owner.equals(userId)) {
                        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                                Button.link("https://econizer.syntaxdevteam.pl/dashboard", LanguageManager.t(settings, "comp_btn_manage")),
                                Button.danger("comp_disband", LanguageManager.t(settings, "comp_btn_disband"))
                        )).queue();
                    } else {
                        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                                Button.danger("comp_leave", LanguageManager.t(settings, "comp_btn_leave"))
                        )).queue();
                    }
                } else {
                    EmbedBuilder eb = new EmbedBuilder().setColor(Color.RED)
                            .setAuthor(LanguageManager.t(settings, "comp_unemployed_author"), null, event.getUser().getEffectiveAvatarUrl())
                            .setDescription(LanguageManager.t(settings, "comp_unemployed_desc", COMPANY_CREATION_COST, settings.currencyEmoji));

                    event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                            Button.success("comp_create_cash", LanguageManager.t(settings, "comp_btn_create_cash")),
                            Button.primary("comp_create_card", LanguageManager.t(settings, "comp_btn_create_card")),
                            Button.secondary("comp_top", LanguageManager.t(settings, "comp_btn_top"))
                    )).setEphemeral(true).queue();
                }
            }
        } catch (SQLException e) {
            event.reply(LanguageManager.t(settings, "err_db")).setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];
        if (!action.startsWith("comp_")) return;

        GuildSettings settings = db.getGuildSettings(event.getGuild().getId());

        if (action.equals("comp_create_cash") || action.equals("comp_create_card")) {
            TextInput nameInput = TextInput.create("company_name", TextInputStyle.SHORT).setMinLength(3).setMaxLength(30).build();
            Modal modal = Modal.create("modal_" + action, LanguageManager.t(settings, "comp_modal_title"))
                    .addComponents(Label.of(LanguageManager.t(settings, "comp_modal_label"), nameInput))
                    .build();
            event.replyModal(modal).queue();
        } else if (action.equals("comp_top")) {
            Connection conn = db.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT name, vault FROM bot_companies WHERE guild_id = ? ORDER BY vault DESC LIMIT 10")) {
                pstmt.setString(1, event.getGuild().getId());
                ResultSet rs = pstmt.executeQuery();
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F"))
                        .setTitle(LanguageManager.t(settings, "comp_top_title"));
                int i = 1;
                while (rs.next()) {
                    eb.addField(i + ". " + rs.getString("name"),
                            LanguageManager.t(settings, "comp_top_field", rs.getInt("vault"), settings.currencyEmoji), false);
                    i++;
                }
                if (i == 1) eb.setDescription(LanguageManager.t(settings, "comp_top_empty"));
                event.replyEmbeds(eb.build()).setEphemeral(true).queue();
            } catch (SQLException e) {
                event.reply(LanguageManager.t(settings, "err_db")).setEphemeral(true).queue();
            }
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().startsWith("modal_comp_create_")) {
            String guildId = event.getGuild().getId();
            String userId = event.getUser().getId();
            GuildSettings settings = db.getGuildSettings(guildId);
            String compName = event.getValue("company_name").getAsString();

            boolean useCard = event.getModalId().equals("modal_comp_create_card");
            boolean success = useCard
                    ? db.removeBankCoins(guildId, userId, COMPANY_CREATION_COST)
                    : db.removeCoins(guildId, userId, COMPANY_CREATION_COST, "gotowka");

            if (success) {
                Connection conn = db.getConnection();
                try {
                    try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO bot_companies (guild_id, name, owner_id) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                        stmt.setString(1, guildId);
                        stmt.setString(2, compName);
                        stmt.setString(3, userId);
                        stmt.executeUpdate();
                        ResultSet rs = stmt.getGeneratedKeys();
                        if (rs.next()) {
                            try (PreparedStatement empStmt = conn.prepareStatement("INSERT INTO bot_employees (guild_id, user_id, company_id) VALUES (?, ?, ?)")) {
                                empStmt.setString(1, guildId);
                                empStmt.setString(2, userId);
                                empStmt.setInt(3, rs.getInt(1));
                                empStmt.executeUpdate();
                            }
                        }
                    }
                    String payMethod = useCard
                            ? LanguageManager.t(settings, "comp_create_pay_card")
                            : LanguageManager.t(settings, "comp_create_pay_cash");
                    event.reply(LanguageManager.t(settings, "comp_create_ok", compName, payMethod)).queue();
                } catch (Exception e) {
                    event.reply(LanguageManager.t(settings, "comp_create_fail")).setEphemeral(true).queue();
                }
            } else {
                event.reply(LanguageManager.t(settings, "comp_no_funds", COMPANY_CREATION_COST)).setEphemeral(true).queue();
            }
        }
    }
}
