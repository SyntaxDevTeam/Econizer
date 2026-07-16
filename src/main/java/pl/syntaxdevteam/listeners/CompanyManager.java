package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class CompanyManager extends ListenerAdapter {
    private final DatabaseManager db;

    private final int DEFAULT_COMPANY_CREATION_COST = 25000;

    public CompanyManager(DatabaseManager db) {
        this.db = db;
    }

    // ===============================================
    // SYSTEM ZABEZPIECZEŃ PRZED BRAKIEM TŁUMACZEŃ (TEXT_ERR)
    // ===============================================
    private String getLang(GuildSettings settings, String key, String fallback, Object... args) {
        String result = LanguageManager.t(settings, key, args);
        if (result.startsWith("TEXT_ERR") || result.equals("LANG_ERR")) {
            for (int i = 0; i < args.length; i++) {
                fallback = fallback.replace("{" + i + "}", String.valueOf(args[i]));
            }
            return fallback.replace("\\n", "\n");
        }
        return result;
    }

    // ===============================================
    // DYNAMICZNE USTAWIENIA Z PANELU WWW (MySQL)
    // ===============================================
    private int getCompanyCost(Connection conn, String guildId) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT comp_cost FROM bot_guild_settings WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("comp_cost");
        } catch (SQLException ignored) {}
        return DEFAULT_COMPANY_CREATION_COST;
    }

    private boolean isFeatureEnabled(Connection conn, String guildId, String columnName) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + columnName + " FROM bot_guild_settings WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(columnName) == 1;
        } catch (SQLException ignored) {}
        return true;
    }

    // ===============================================
    // RENDERING TEKSTÓW Z CIENIEM
    // ===============================================
    private static void drawTextWithShadow(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(Color.BLACK);
        g.drawString(text, x + 2, y + 2);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    // ===============================================
    // DYNAMICZNY GENERATOR GRAFIKI Z TŁEM Z VPS'a
    // ===============================================
    public byte[] generateCompanyBanner(String ownerUrl, String ownerNick, List<String> employeeUrls, String companyName, String tag, GuildSettings settings) {
        try {
            int width = 800;
            int height = employeeUrls.isEmpty() ? 280 : 380;
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setClip(new RoundRectangle2D.Float(0, 0, width, height, 20, 20));

            File bgFile = new File("company_bg.png");
            if (bgFile.exists()) {
                BufferedImage bgImage = ImageIO.read(bgFile);
                g.drawImage(bgImage, 0, 0, width, height, null);

                g.setColor(new Color(0, 0, 0, 160));
                g.fillRect(0, 0, width, height);
            } else {
                g.setColor(Color.decode("#2B2D31"));
                g.fillRect(0, 0, width, height);
            }
            g.setClip(null);

            int avSize = 120;
            int avX = (width - avSize) / 2;
            int avY = 25;

            String fetchUrl = ownerUrl.replace(".webp", ".png").replace(".gif", ".png") + "?size=256";
            BufferedImage avatar = ImageIO.read(new URL(fetchUrl));

            if (avatar != null) {
                g.setClip(new Ellipse2D.Float(avX, avY, avSize, avSize));
                g.drawImage(avatar, avX, avY, avSize, avSize, null);
                g.setClip(null);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(5));
                g.drawOval(avX, avY, avSize, avSize);
            }

            g.setFont(new Font("SansSerif", Font.BOLD, 32));
            String title = "[" + tag + "] " + companyName;
            int titleW = g.getFontMetrics().stringWidth(title);
            drawTextWithShadow(g, title, (width - titleW) / 2, avY + avSize + 45, Color.WHITE);

            g.setFont(new Font("SansSerif", Font.PLAIN, 22));
            String sub = getLang(settings, "comp_banner_owner", "Właściciel: {0}", ownerNick);
            int subW = g.getFontMetrics().stringWidth(sub);
            drawTextWithShadow(g, sub, (width - subW) / 2, avY + avSize + 75, Color.LIGHT_GRAY);

            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            String empTitle = getLang(settings, "comp_banner_employees", "Pracownicy ({0}/10)", employeeUrls.size());
            int empTitleW = g.getFontMetrics().stringWidth(empTitle);
            drawTextWithShadow(g, empTitle, (width - empTitleW) / 2, avY + avSize + 115, Color.LIGHT_GRAY);

            if (!employeeUrls.isEmpty()) {
                int empSize = 50;
                int maxEmps = Math.min(employeeUrls.size(), 10);
                int spacing = 15;
                int totalWidth = (maxEmps * empSize) + ((maxEmps - 1) * spacing);
                int startX = (width - totalWidth) / 2;
                int startY = avY + avSize + 130;

                for (int i = 0; i < maxEmps; i++) {
                    String empUrl = employeeUrls.get(i).replace(".webp", ".png").replace(".gif", ".png") + "?size=64";
                    try {
                        BufferedImage empAvatar = ImageIO.read(new URL(empUrl));
                        int x = startX + i * (empSize + spacing);
                        g.setClip(new Ellipse2D.Float(x, startY, empSize, empSize));
                        g.drawImage(empAvatar, x, startY, empSize, empSize, null);
                        g.setClip(null);
                        g.setColor(Color.WHITE);
                        g.setStroke(new BasicStroke(3));
                        g.drawOval(x, startY, empSize, empSize);
                    } catch (Exception ignored) {}
                }
            }

            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    public static void addCompanyRevenue(DatabaseManager db, String guildId, String userId, int baseEarnings) {
        Connection conn = db.getConnection();
        if (conn == null) return;
        try {
            int companyId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT company_id FROM bot_employees WHERE guild_id = ? AND user_id = ?")) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) companyId = rs.getInt("company_id");
            }
            if (companyId != -1) {
                int bonus = (int) (baseEarnings * 0.15);
                if (bonus > 0) {
                    try (PreparedStatement u = conn.prepareStatement("UPDATE bot_companies SET vault = vault + ?, total_earned = total_earned + ? WHERE id = ?")) {
                        u.setInt(1, bonus); u.setInt(2, bonus); u.setInt(3, companyId); u.executeUpdate();
                    }
                }
            }
        } catch (SQLException ignored) {}
    }

    public static double getEmployeeBonusMultiplier(DatabaseManager db, String guildId, String userId) {
        Connection conn = db.getConnection();
        if (conn == null) return 1.0;
        try {
            int cId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT company_id FROM bot_employees WHERE guild_id = ? AND user_id = ?")) {
                pstmt.setString(1, guildId); pstmt.setString(2, userId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) cId = rs.getInt("company_id");
            }
            if (cId != -1) {
                try (PreparedStatement c = conn.prepareStatement("SELECT COUNT(*) FROM bot_employees WHERE company_id = ?")) {
                    c.setInt(1, cId); ResultSet rs = c.executeQuery();
                    if (rs.next()) {
                        double bonus = 1.0 + (rs.getInt(1) * 0.05);
                        return Math.min(bonus, 1.50);
                    }
                }
            }
        } catch (SQLException ignored) {}
        return 1.0;
    }

    private int applyCompanyTag(Member member, String tag, Role compRole) {
        if (member == null) return 2;
        Guild guild = member.getGuild();
        boolean roleSuccess = true;
        boolean nickSuccess = true;

        try {
            if (compRole != null && !member.getRoles().contains(compRole)) {
                guild.addRoleToMember(member, compRole).complete();
            }
        } catch (Exception e) { roleSuccess = false; }

        try {
            String currentName = member.getEffectiveName();
            if (!currentName.contains("[" + tag + "]")) {
                String newName = "[" + tag + "] " + currentName;
                if (newName.length() > 32) newName = newName.substring(0, 32);
                member.modifyNickname(newName).complete();
            }
        } catch (Exception e) { nickSuccess = false; }

        if (roleSuccess && nickSuccess) return 0;
        if (roleSuccess && !nickSuccess) return 1;
        return 2;
    }

    private void removeCompanyTag(Member member, String tag, Role compRole) {
        if (member == null) return;
        Guild guild = member.getGuild();
        try {
            if (compRole != null && member.getRoles().contains(compRole)) {
                guild.removeRoleFromMember(member, compRole).complete();
            }
        } catch (Exception ignored) {}

        try {
            String currentName = member.getEffectiveName();
            String tagStr = "[" + tag + "]";
            if (currentName.contains(tagStr)) {
                String newName = currentName.replace(tagStr, "").replaceAll("\\s+", " ").trim();
                member.modifyNickname(newName).complete();
            }
        } catch (Exception ignored) {}
    }

    private void showPanel(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event, String guildId, String userId, boolean isEdit) {
        new Thread(() -> {
            GuildSettings settings = db.getGuildSettings(guildId);
            Connection conn = db.getConnection();
            int customCost = getCompanyCost(conn, guildId);

            String name = null, owner = null, tag = null, roleId = null;
            int vault = 0, totalEarned = 0, sOwner = 0, sEmp = 0, cId = -1, emps = 0;

            try {
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT c.name, c.owner_id, c.vault, c.total_earned, c.split_owner, c.split_emp, c.tag, c.role_id, c.id FROM bot_employees e JOIN bot_companies c ON e.company_id = c.id WHERE e.guild_id = ? AND e.user_id = ?")) {
                    pstmt.setString(1, guildId); pstmt.setString(2, userId);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        name = rs.getString("name"); owner = rs.getString("owner_id"); tag = rs.getString("tag");
                        roleId = rs.getString("role_id"); vault = rs.getInt("vault"); totalEarned = rs.getInt("total_earned");
                        sOwner = rs.getInt("split_owner"); sEmp = rs.getInt("split_emp"); cId = rs.getInt("id");
                    }
                }

                if (cId != -1) {
                    try (PreparedStatement c = conn.prepareStatement("SELECT COUNT(*) FROM bot_employees WHERE company_id = ?")) {
                        c.setInt(1, cId); ResultSet crs = c.executeQuery();
                        if (crs.next()) emps = crs.getInt(1);
                    }

                    int bonusPercent = Math.min(emps * 5, 50);

                    Member ownerMember = event.getGuild().getMemberById(owner);
                    String ownerNick = (ownerMember != null) ? ownerMember.getEffectiveName() : "Właściciel";

                    Role compRole = (roleId != null) ? event.getGuild().getRoleById(roleId) : null;
                    Color compColor = (compRole != null && compRole.getColor() != null) ? compRole.getColor() : Color.decode("#34495E");

                    List<String> employeeUrls = new ArrayList<>();
                    try (PreparedStatement empPstmt = conn.prepareStatement("SELECT user_id FROM bot_employees WHERE company_id = ? AND user_id != ? LIMIT 10")) {
                        empPstmt.setInt(1, cId); empPstmt.setString(2, owner);
                        ResultSet empRs = empPstmt.executeQuery();
                        while (empRs.next()) {
                            String empId = empRs.getString("user_id");
                            User empUser = event.getJDA().getUserById(empId);
                            if (empUser == null) {
                                try { empUser = event.getJDA().retrieveUserById(empId).complete(); } catch (Exception ignored) {}
                            }
                            if (empUser != null) {
                                employeeUrls.add(empUser.getEffectiveAvatarUrl());
                            }
                        }
                    }

                    EmbedBuilder eb = new EmbedBuilder().setColor(compColor)
                            .setAuthor(getLang(settings, "comp_panel_author", "🏢 Panel Zarządzania Firmą: [{0}] {1}", tag, name), null, event.getUser().getEffectiveAvatarUrl())
                            .setDescription(getLang(settings, "comp_panel_desc", "Zarządzaj swoją korporacją, rozbudowuj struktury, wypłacaj dywidendy lub przeglądaj statystyki."))
                            .addField(getLang(settings, "comp_panel_field_owner", "👑 Główny Właściciel"), "<@" + owner + ">", true)
                            .addField(getLang(settings, "comp_panel_field_vault", "💰 Skarbiec Firmy"), "**" + vault + "** " + settings.currencyEmoji, true)
                            .addField(getLang(settings, "comp_panel_field_profits", "📈 Całkowite Zyski Spółki"), "**" + totalEarned + "** " + settings.currencyEmoji, true)
                            .addField(getLang(settings, "comp_panel_field_bonus", "✨ Aktywna Premia Firmowa"), getLang(settings, "comp_panel_field_bonus_val", "**+{0}%** (dodatkowy profit do komend `/work` i `/daily`)", bonusPercent), false)
                            .addField(getLang(settings, "comp_panel_field_splits", "📊 Podział Udziałów Skarbca"), getLang(settings, "comp_panel_field_splits_val", "Właściciel: **{0}%** | Pracownicy (łącznie): **{1}%**", sOwner, sEmp), false);

                    byte[] bannerBytes = null;
                    if (ownerMember != null) {
                        bannerBytes = generateCompanyBanner(ownerMember.getUser().getEffectiveAvatarUrl(), ownerNick, employeeUrls, name, tag, settings);
                    }
                    String fileName = "banner_" + System.currentTimeMillis() + ".png";
                    if (bannerBytes != null) eb.setImage("attachment://" + fileName);

                    List<ActionRow> rows = new ArrayList<>();
                    if (owner.equals(userId)) {
                        rows.add(ActionRow.of(
                                Button.link("https://econizer.syntaxdevteam.pl/dashboard", getLang(settings, "comp_btn_manage", "Zarządzaj (WWW)")),
                                Button.secondary("comp_list:" + cId, getLang(settings, "comp_btn_list", "📋 Lista pracowników")),
                                Button.primary("comp_invite:" + userId, getLang(settings, "comp_btn_invite", "📩 Zatrudnij"))
                        ));
                        rows.add(ActionRow.of(
                                Button.secondary("comp_top", getLang(settings, "comp_btn_top", "🏆 Top 10 Firm")),
                                Button.primary("comp_color:" + userId, getLang(settings, "comp_btn_color", "🎨 Kolor firmy")),
                                Button.success("comp_payout:" + userId, getLang(settings, "comp_btn_payout", "💸 Wypłać")),
                                Button.danger("comp_disband:" + userId, getLang(settings, "comp_btn_disband", "⚠️ Rozwiąż"))
                        ));
                    } else {
                        rows.add(ActionRow.of(
                                Button.secondary("comp_list:" + cId, getLang(settings, "comp_btn_list", "📋 Lista pracowników")),
                                Button.secondary("comp_top", getLang(settings, "comp_btn_top", "🏆 Top 10 Firm")),
                                Button.danger("comp_leave:" + userId, getLang(settings, "comp_btn_leave", "🚪 Opuść"))
                        ));
                    }

                    if (isEdit) event.getHook().editOriginalEmbeds(eb.build()).setFiles(bannerBytes != null ? FileUpload.fromData(bannerBytes, fileName) : null).setComponents(rows).queue();
                    else event.getHook().sendMessageEmbeds(eb.build()).addFiles(bannerBytes != null ? FileUpload.fromData(bannerBytes, fileName) : null).addComponents(rows).queue();
                } else {
                    EmbedBuilder eb = new EmbedBuilder().setColor(Color.RED).setDescription(getLang(settings, "comp_unemployed_desc", "Nie jesteś zatrudniony. Otwarcie działalności kosztuje **{0} {1}**.", customCost, settings.currencyEmoji));
                    if (isEdit) event.getHook().editOriginalEmbeds(eb.build()).setComponents(ActionRow.of(Button.success("comp_create_cash:" + userId, getLang(settings, "comp_btn_create_cash", "💼 Załóż (Gotówka)")), Button.primary("comp_create_card:" + userId, getLang(settings, "comp_btn_create_card", "💳 Załóż (Karta)")))).queue();
                    else event.getHook().sendMessageEmbeds(eb.build()).addComponents(ActionRow.of(Button.success("comp_create_cash:" + userId, getLang(settings, "comp_btn_create_cash", "💼 Załóż (Gotówka)")), Button.primary("comp_create_card:" + userId, getLang(settings, "comp_btn_create_card", "💳 Załóż (Karta)")))).queue();
                }
            } catch (SQLException e) { event.getHook().sendMessage(getLang(settings, "err_db", "Błąd Bazy Danych!")).setEphemeral(true).queue(); }
        }).start();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("company")) return;
        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (!settings.economyEnabled) {
            event.reply(getLang(settings, "eco_disabled", "❌ Ekonomia wyłączona.")).setEphemeral(true).queue();
            return;
        }

        db.logCommandUsage("company", guildId);

        boolean hasCompany = false;
        try (Connection conn = db.getConnection();
             PreparedStatement chk = conn.prepareStatement("SELECT company_id FROM bot_employees WHERE guild_id = ? AND user_id = ?")) {
            chk.setString(1, guildId); chk.setString(2, event.getUser().getId());
            ResultSet rs = chk.executeQuery();
            if (rs.next()) hasCompany = true;
        } catch (SQLException ignored) {}

        event.deferReply(!hasCompany).queue();
        showPanel(event, guildId, event.getUser().getId(), false);
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];
        if (!action.startsWith("comp_")) return;

        String guildId = event.getGuild().getId();
        String clickerId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        Connection conn = db.getConnection();

        if (action.equals("comp_create_cash") || action.equals("comp_create_card") || action.equals("comp_invite") ||
                action.equals("comp_color") || action.equals("comp_payout") || action.equals("comp_disband") ||
                action.equals("comp_leave")) {
            if (parts.length > 1 && !parts[1].equals(clickerId)) {
                event.reply(getLang(settings, "comp_err_not_yours", "❌ Ten przycisk nie należy do Twojego panelu!")).setEphemeral(true).queue();
                return;
            }
        }

        if (action.equals("comp_create_cash") || action.equals("comp_create_card")) {
            TextInput nameInput = TextInput.create("company_name", TextInputStyle.SHORT)
                    .setMinLength(3).setMaxLength(30).build();
            TextInput tagInput = TextInput.create("company_tag", TextInputStyle.SHORT)
                    .setMinLength(2).setMaxLength(5).build();

            Modal modal = Modal.create("modal_" + action, getLang(settings, "comp_modal_title", "Rejestracja firmy"))
                    .addComponents(Label.of(getLang(settings, "comp_modal_label", "Nazwa Przedsiębiorstwa"), nameInput), Label.of("Tag (np. CDPR)", tagInput))
                    .build();
            event.replyModal(modal).queue();
        }
        else if (action.equals("comp_color")) {
            StringSelectMenu colorMenu = StringSelectMenu.create("comp_color_select:" + clickerId)
                    .setPlaceholder(getLang(settings, "comp_color_placeholder", "Wybierz nowy kolor..."))
                    .addOption(getLang(settings, "comp_color_red", "Czerwony"), "#E74C3C", Emoji.fromUnicode("🔴"))
                    .addOption(getLang(settings, "comp_color_green", "Zielony"), "#2ECC71", Emoji.fromUnicode("🟢"))
                    .addOption(getLang(settings, "comp_color_blue", "Niebieski"), "#3498DB", Emoji.fromUnicode("🔵"))
                    .addOption(getLang(settings, "comp_color_yellow", "Żółty"), "#F1C40F", Emoji.fromUnicode("🟡"))
                    .addOption(getLang(settings, "comp_color_purple", "Fioletowy"), "#9B59B6", Emoji.fromUnicode("🟣"))
                    .addOption(getLang(settings, "comp_color_orange", "Pomarańczowy"), "#E67E22", Emoji.fromUnicode("🟠"))
                    .addOption(getLang(settings, "comp_color_pink", "Różowy"), "#FF69B4", Emoji.fromUnicode("🌸"))
                    .addOption(getLang(settings, "comp_color_white", "Biały"), "#FFFFFF", Emoji.fromUnicode("⚪"))
                    .addOption(getLang(settings, "comp_color_darkgray", "Ciemnoszary"), "#2F3136", Emoji.fromUnicode("⚫"))
                    .addOption(getLang(settings, "comp_color_custom", "Wpisz własny HEX..."), "custom_hex", Emoji.fromUnicode("✏️"))
                    .build();

            event.reply(getLang(settings, "comp_color_prompt", "🎨 **Wybierz kolor dla roli Twojej firmy!**"))
                    .addComponents(ActionRow.of(colorMenu)).setEphemeral(true).queue();
        }
        else if (action.equals("comp_top")) {
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT name, tag, vault, total_earned FROM bot_companies WHERE guild_id = ? ORDER BY vault DESC LIMIT 10")) {
                pstmt.setString(1, guildId);
                ResultSet rs = pstmt.executeQuery();
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F"))
                        .setTitle(getLang(settings, "comp_top_title", "🏆 Top 10 Firm"));
                int i = 1;
                while (rs.next()) {
                    eb.addField(i + ". [" + rs.getString("tag") + "] " + rs.getString("name"),
                            getLang(settings, "comp_top_field", "Skarbiec: **{0}** {1} | Zyski: {2}", rs.getInt("vault"), settings.currencyEmoji, rs.getInt("total_earned")), false);
                    i++;
                }
                if (i == 1) eb.setDescription(getLang(settings, "comp_top_empty", "Brak zarejestrowanych firm na tym serwerze."));
                event.replyEmbeds(eb.build()).setEphemeral(true).queue();
            } catch (SQLException e) { event.reply(getLang(settings, "err_db", "Błąd")).setEphemeral(true).queue(); }
        }
        else if (action.equals("comp_payout")) {
            try {
                int vault = 0, cId = -1, sOwner = 0, sEmp = 0;
                try (PreparedStatement get = conn.prepareStatement("SELECT id, vault, split_owner, split_emp FROM bot_companies WHERE owner_id = ? AND guild_id = ?")) {
                    get.setString(1, clickerId); get.setString(2, guildId);
                    ResultSet rs = get.executeQuery();
                    if (rs.next()) {
                        cId = rs.getInt("id"); vault = rs.getInt("vault");
                        sOwner = rs.getInt("split_owner"); sEmp = rs.getInt("split_emp");
                    }
                }

                if (cId == -1) { event.reply(getLang(settings, "comp_err_not_owner", "❌ Tylko właściciel może wypłacać!")).setEphemeral(true).queue(); return; }
                if (vault < 100) { event.reply(getLang(settings, "comp_err_payout_min", "❌ W skarbcu musi być co najmniej 100 {0}!", settings.currencyEmoji)).setEphemeral(true).queue(); return; }

                List<String> employees = new ArrayList<>();
                try (PreparedStatement getEmp = conn.prepareStatement("SELECT user_id FROM bot_employees WHERE company_id = ? AND user_id != ?")) {
                    getEmp.setInt(1, cId); getEmp.setString(2, clickerId);
                    ResultSet rs = getEmp.executeQuery();
                    while (rs.next()) employees.add(rs.getString("user_id"));
                }

                int ownerCut = (int) (vault * (sOwner / 100.0));
                int empCutTotal = vault - ownerCut;

                db.addCoins(guildId, clickerId, ownerCut, "gotowka");
                StringBuilder sb = new StringBuilder();
                sb.append(getLang(settings, "comp_payout_ok", "💸 **Dywidendy zostały pomyślnie wypłacone!**\n\n👑 Właściciel (<@{0}>) otrzymał: **{1}** {2}\n", clickerId, ownerCut, settings.currencyEmoji));

                if (!employees.isEmpty()) {
                    int perEmp = empCutTotal / employees.size();
                    sb.append(getLang(settings, "comp_payout_emp_line", "👥 Pracownicy otrzymali po: **{0}** {1} \n", perEmp, settings.currencyEmoji));
                    for (String emp : employees) db.addCoins(guildId, emp, perEmp, "gotowka");
                }

                try (PreparedStatement zero = conn.prepareStatement("UPDATE bot_companies SET vault = 0 WHERE id = ?")) {
                    zero.setInt(1, cId); zero.executeUpdate();
                }

                event.replyEmbeds(new EmbedBuilder().setColor(Color.GREEN).setDescription(sb.toString()).build()).queue();
            } catch (SQLException e) { event.reply(getLang(settings, "err_db", "Błąd bazy danych")).setEphemeral(true).queue(); }
        }
        else if (action.equals("comp_list")) {
            int companyId = Integer.parseInt(parts[1]);
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT user_id FROM bot_employees WHERE company_id = ?")) {
                pstmt.setInt(1, companyId);
                ResultSet rs = pstmt.executeQuery();
                StringBuilder sb = new StringBuilder();
                int count = 1;
                while (rs.next()) {
                    sb.append(count).append(". <@").append(rs.getString("user_id")).append(">\n");
                    count++;
                }
                if (sb.length() == 0) sb.append(getLang(settings, "comp_list_empty", "Brak danych."));

                event.replyEmbeds(new EmbedBuilder().setColor(Color.BLUE).setTitle(getLang(settings, "comp_list_title", "📋 Lista pracowników")).setDescription(sb.toString()).build()).setEphemeral(true).queue();
            } catch (SQLException e) { event.reply(getLang(settings, "err_db", "Błąd bazy")).setEphemeral(true).queue(); }
        }
        else if (action.equals("comp_invite")) {
            EntitySelectMenu menu = EntitySelectMenu.create("comp_inv_select:" + clickerId, EntitySelectMenu.SelectTarget.USER)
                    .setPlaceholder(getLang(settings, "comp_inv_placeholder", "Wybierz użytkownika...")).setMinValues(1).setMaxValues(1).build();
            event.reply(getLang(settings, "comp_inv_prompt", "Kogo chcesz zatrudnić?")).addComponents(ActionRow.of(menu)).setEphemeral(true).queue();
        }
        else if (action.equals("comp_acc")) {
            int companyId = Integer.parseInt(parts[1]);
            String targetId = parts[2];
            if (!clickerId.equals(targetId)) { event.reply(getLang(settings, "comp_inv_not_for_you", "❌ Zaproszenie nie dla Ciebie!")).setEphemeral(true).queue(); return; }

            try {
                try (PreparedStatement check = conn.prepareStatement("SELECT company_id FROM bot_employees WHERE user_id = ? AND guild_id = ?")) {
                    check.setString(1, clickerId); check.setString(2, guildId);
                    if (check.executeQuery().next()) { event.reply(getLang(settings, "comp_inv_already_emp", "❌ Pracujesz już w innej firmie! Odejmij z niej.")).setEphemeral(true).queue(); return; }
                }

                String compName = "", tag = "", roleId = null;
                try (PreparedStatement checkC = conn.prepareStatement("SELECT name, tag, role_id FROM bot_companies WHERE id = ?")) {
                    checkC.setInt(1, companyId);
                    ResultSet rs = checkC.executeQuery();
                    if (!rs.next()) { event.reply(getLang(settings, "comp_inv_no_company", "❌ Firma już nie istnieje.")).setEphemeral(true).queue(); return; }
                    compName = rs.getString("name"); tag = rs.getString("tag"); roleId = rs.getString("role_id");
                }

                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO bot_employees (guild_id, user_id, company_id) VALUES (?, ?, ?)")) {
                    ins.setString(1, guildId); ins.setString(2, clickerId); ins.setInt(3, companyId); ins.executeUpdate();
                }

                Role cRole = roleId != null ? event.getGuild().getRoleById(roleId) : null;
                int tagStatus = applyCompanyTag(event.getMember(), tag, cRole);

                String msg = getLang(settings, "comp_acc_ok", "✅ Zatrudniono w firmie **[{0}] {1}**!", tag, compName);
                if (tagStatus == 1) msg += getLang(settings, "comp_acc_tag_err_owner", "\n*(Bot nie zmienił nicku - blokada Discord)*");

                event.reply(msg).setEphemeral(true).queue();
                event.getChannel().editMessageComponentsById(event.getMessageId(), Collections.emptyList()).queue();
            } catch (SQLException e) { event.reply(getLang(settings, "err_db", "Błąd bazy")).setEphemeral(true).queue(); }
        }
        else if (action.equals("comp_leave")) {
            try {
                String tag = "", roleId = null;
                try (PreparedStatement get = conn.prepareStatement("SELECT c.tag, c.role_id FROM bot_companies c JOIN bot_employees e ON c.id = e.company_id WHERE e.user_id = ? AND e.guild_id = ?")) {
                    get.setString(1, clickerId); get.setString(2, guildId);
                    ResultSet rs = get.executeQuery();
                    if (rs.next()) { tag = rs.getString("tag"); roleId = rs.getString("role_id"); }
                }

                try (PreparedStatement del = conn.prepareStatement("DELETE FROM bot_employees WHERE user_id = ? AND guild_id = ?")) {
                    del.setString(1, clickerId); del.setString(2, guildId);
                    if (del.executeUpdate() > 0) {
                        Role cRole = roleId != null ? event.getGuild().getRoleById(roleId) : null;
                        removeCompanyTag(event.getMember(), tag, cRole);
                        event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN).setDescription(getLang(settings, "comp_leave_ok", "✅ Opuściłeś firmę.")).build()).setComponents().queue();
                    } else { event.reply(getLang(settings, "comp_err_not_in_company", "❌ Nie należysz do żadnej firmy.")).setEphemeral(true).queue(); }
                }
            } catch (SQLException e) { event.reply(getLang(settings, "err_db", "Błąd bazy")).setEphemeral(true).queue(); }
        }
        else if (action.equals("comp_disband")) {
            try {
                int cId = -1; String tag = "", roleId = null;
                try (PreparedStatement get = conn.prepareStatement("SELECT id, tag, role_id FROM bot_companies WHERE owner_id = ? AND guild_id = ?")) {
                    get.setString(1, clickerId); get.setString(2, guildId);
                    ResultSet rs = get.executeQuery();
                    if (rs.next()) { cId = rs.getInt("id"); tag = rs.getString("tag"); roleId = rs.getString("role_id"); }
                }

                if (cId != -1) {
                    final String fTag = tag;
                    try (PreparedStatement getEmp = conn.prepareStatement("SELECT user_id FROM bot_employees WHERE company_id = ?")) {
                        getEmp.setInt(1, cId); ResultSet rsEmp = getEmp.executeQuery();
                        while (rsEmp.next()) {
                            event.getGuild().retrieveMemberById(rsEmp.getString("user_id")).queue(m -> removeCompanyTag(m, fTag, null), e -> {});
                        }
                    }

                    if (roleId != null) {
                        Role r = event.getGuild().getRoleById(roleId);
                        if (r != null) r.delete().queue(null, e -> {});
                    }

                    try {
                        String channelName = "💬-" + tag.toLowerCase() + "-company-chat";
                        List<TextChannel> channels = event.getGuild().getTextChannelsByName(channelName, true);
                        for (TextChannel ch : channels) ch.delete().queue(null, e -> {});
                    } catch (Exception ignored) {}

                    try (PreparedStatement delEmp = conn.prepareStatement("DELETE FROM bot_employees WHERE company_id = ?")) { delEmp.setInt(1, cId); delEmp.executeUpdate(); }
                    try (PreparedStatement delComp = conn.prepareStatement("DELETE FROM bot_companies WHERE id = ?")) { delComp.setInt(1, cId); delComp.executeUpdate(); }

                    event.editMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription(getLang(settings, "comp_disband_ok", "⚠️ **Firma rozwiązana, pracownicy zwolnieni.**")).build()).setComponents().queue();
                } else { event.reply(getLang(settings, "comp_err_no_company", "❌ Nie masz firmy!")).setEphemeral(true).queue(); }
            } catch (SQLException e) { event.reply(getLang(settings, "err_db", "Błąd bazy")).setEphemeral(true).queue(); }
        }
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (!parts[0].equals("comp_inv_select")) return;

        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (parts.length > 1 && !parts[1].equals(event.getUser().getId())) {
            event.reply(getLang(settings, "comp_err_not_yours", "❌ Brak dostępu do tego panelu!")).setEphemeral(true).queue(); return;
        }

        User target = event.getMentions().getUsers().get(0);
        String ownerId = event.getUser().getId();

        if (target.isBot() || target.getId().equals(ownerId)) {
            event.reply(getLang(settings, "comp_inv_self_bot", "❌ Nie zapraszaj bota/siebie!")).setEphemeral(true).queue(); return;
        }

        Connection conn = db.getConnection();
        try {
            try (PreparedStatement checkEmp = conn.prepareStatement("SELECT company_id FROM bot_employees WHERE guild_id = ? AND user_id = ?")) {
                checkEmp.setString(1, guildId);
                checkEmp.setString(2, target.getId());
                ResultSet rsEmp = checkEmp.executeQuery();
                if (rsEmp.next()) {
                    int existingCompanyId = rsEmp.getInt("company_id");

                    int ownerCompanyId = -1;
                    try (PreparedStatement getOwnerComp = conn.prepareStatement("SELECT id FROM bot_companies WHERE owner_id = ? AND guild_id = ?")) {
                        getOwnerComp.setString(1, ownerId); getOwnerComp.setString(2, guildId);
                        ResultSet rsOwnerComp = getOwnerComp.executeQuery();
                        if (rsOwnerComp.next()) ownerCompanyId = rsOwnerComp.getInt("id");
                    }

                    if (existingCompanyId == ownerCompanyId) {
                        event.reply(getLang(settings, "comp_inv_already_in_your", "❌ Ten gracz pracuje już u Ciebie!")).setEphemeral(true).queue();
                    } else {
                        event.reply(getLang(settings, "comp_inv_already_in_other", "❌ Ten gracz pracuje w innej firmie.")).setEphemeral(true).queue();
                    }
                    return;
                }
            }

            int companyId = -1; String companyName = "", tag = "";
            try (PreparedStatement get = conn.prepareStatement("SELECT id, name, tag FROM bot_companies WHERE owner_id = ? AND guild_id = ?")) {
                get.setString(1, ownerId); get.setString(2, guildId);
                ResultSet rs = get.executeQuery();
                if (rs.next()) { companyId = rs.getInt("id"); companyName = rs.getString("name"); tag = rs.getString("tag"); }
            }

            if (companyId != -1) {
                event.reply(getLang(settings, "comp_inv_sent", "✅ Zaproszenie wysłane!")).setEphemeral(true).queue();
                event.getChannel().sendMessage(getLang(settings, "comp_inv_msg", "<@{0}>, zaproszenie od <@{3}> do **[{1}] {2}**!", target.getId(), tag, companyName, ownerId))
                        .addComponents(ActionRow.of(Button.success("comp_acc:" + companyId + ":" + target.getId(), getLang(settings, "comp_btn_acc", "✅ Akceptuj"))))
                        .queue();
            } else { event.reply(getLang(settings, "comp_err_no_company", "❌ Brak firmy!")).setEphemeral(true).queue(); }
        } catch (SQLException e) { event.reply(getLang(settings, "err_db", "Błąd bazy danych")).setEphemeral(true).queue(); }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (!parts[0].equals("comp_color_select")) return;

        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (parts.length > 1 && !parts[1].equals(event.getUser().getId())) {
            event.reply(getLang(settings, "comp_err_not_yours", "❌ Brak dostępu!")).setEphemeral(true).queue(); return;
        }

        String hex = event.getValues().get(0);
        if (hex.equals("custom_hex")) {
            TextInput colorInput = TextInput.create("hex_color", TextInputStyle.SHORT)
                    .setMinLength(7).setMaxLength(7).setPlaceholder("#FF0000").build();
            Modal modal = Modal.create("modal_comp_color", getLang(settings, "comp_color_modal_title", "Zmień kolor"))
                    .addComponents(Label.of(getLang(settings, "comp_color_modal_label", "Kod HEX"), colorInput)).build();
            event.replyModal(modal).queue();
            return;
        }

        event.deferReply(true).queue();
        new Thread(() -> {
            handleColorChange(event.getGuild(), event.getUser(), hex, msg -> event.getHook().sendMessage(msg).setEphemeral(true).queue(), settings);
        }).start();
    }

    private void handleColorChange(Guild guild, User user, String hex, Consumer<String> replyCallback, GuildSettings settings) {
        String guildId = guild.getId();
        String userId = user.getId();
        Connection conn = db.getConnection();
        try {
            Color color = Color.decode(hex);
            String roleId = null;
            try (PreparedStatement get = conn.prepareStatement("SELECT role_id FROM bot_companies WHERE owner_id = ? AND guild_id = ?")) {
                get.setString(1, userId); get.setString(2, guildId);
                ResultSet rs = get.executeQuery();
                if (rs.next()) roleId = rs.getString("role_id");
            }
            if (roleId != null) {
                Role r = guild.getRoleById(roleId);
                if (r != null) {
                    r.getManager().setColor(color).queue(
                            s -> replyCallback.accept(getLang(settings, "comp_color_ok", "✅ Zmieniono kolor na {0}!", hex)),
                            e -> replyCallback.accept(getLang(settings, "comp_color_err_perms", "❌ Brak uprawnień do ról."))
                    );
                } else replyCallback.accept(getLang(settings, "comp_color_err_norole", "❌ Rola nie istnieje."));
            } else replyCallback.accept(getLang(settings, "comp_color_err_nocomprole", "❌ Firma nie ma roli."));
        } catch (Exception e) { replyCallback.accept(getLang(settings, "comp_color_err_hex", "❌ Zły format HEX!")); }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        Connection conn = db.getConnection();

        if (event.getModalId().equals("modal_comp_color")) {
            event.deferReply(true).queue();
            String hexColor = event.getValue("hex_color").getAsString();
            new Thread(() -> {
                handleColorChange(event.getGuild(), event.getUser(), hexColor, msg -> event.getHook().sendMessage(msg).setEphemeral(true).queue(), settings);
            }).start();
            return;
        }

        if (event.getModalId().startsWith("modal_comp_create_")) {
            event.deferReply(true).queue();

            new Thread(() -> {
                String compName = event.getValue("company_name").getAsString();
                String compTag = event.getValue("company_tag").getAsString().toUpperCase().replace(" ", "");

                int customCost = getCompanyCost(conn, guildId);

                boolean useCard = event.getModalId().equals("modal_comp_create_card");
                boolean success = useCard
                        ? db.removeBankCoins(guildId, userId, customCost)
                        : db.removeCoins(guildId, userId, customCost, "gotowka");

                if (success) {
                    try {
                        Role role = null;

                        boolean rolesEnabled = isFeatureEnabled(conn, guildId, "company_roles_enabled");
                        boolean tagsEnabled = isFeatureEnabled(conn, guildId, "company_tags_enabled");
                        boolean channelsEnabled = isFeatureEnabled(conn, guildId, "company_chat_enabled");

                        if (rolesEnabled) {
                            role = event.getGuild().createRole().setName(compTag).complete();
                        }

                        String rId = role != null ? role.getId() : null;

                        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO bot_companies (guild_id, name, owner_id, tag, role_id) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setString(1, guildId); stmt.setString(2, compName); stmt.setString(3, userId); stmt.setString(4, compTag); stmt.setString(5, rId);
                            stmt.executeUpdate();
                            ResultSet rs = stmt.getGeneratedKeys();
                            if (rs.next()) {
                                try (PreparedStatement empStmt = conn.prepareStatement("INSERT INTO bot_employees (guild_id, user_id, company_id) VALUES (?, ?, ?)")) {
                                    empStmt.setString(1, guildId); empStmt.setString(2, userId); empStmt.setInt(3, rs.getInt(1)); empStmt.executeUpdate();
                                }
                            }
                        }

                        int tagStatus = -1;
                        if (tagsEnabled) {
                            tagStatus = applyCompanyTag(event.getMember(), compTag, role);
                        }

                        String payMethod = useCard ? getLang(settings, "comp_create_pay_card", "Kartą") : getLang(settings, "comp_create_pay_cash", "Gotówką");
                        String msg = getLang(settings, "comp_create_ok", "✅ Założono firmę **{0}** za {1}.", "[" + compTag + "] " + compName, payMethod);

                        if (channelsEnabled && role != null) {
                            try {
                                List<Category> categories = event.getGuild().getCategoriesByName("🏢 BIURA FIRM", true);
                                Category cat = categories.isEmpty() ? event.getGuild().createCategory("🏢 BIURA FIRM").complete() : categories.get(0);

                                String channelName = "💬-" + compTag.toLowerCase() + "-company-chat";
                                event.getGuild().createTextChannel(channelName, cat)
                                        .addPermissionOverride(event.getGuild().getPublicRole(), Collections.emptyList(), Collections.singletonList(Permission.VIEW_CHANNEL))
                                        .addPermissionOverride(role, Collections.singletonList(Permission.VIEW_CHANNEL), Collections.emptyList())
                                        .queue(ch -> {
                                            ch.sendMessage(getLang(settings, "comp_chat_welcome", "👋 Witaj w biurze firmy **[{0}] {1}**, <@{2}>!", compTag, compName, userId)).queue();
                                        });
                            } catch (Exception e) {
                                msg += getLang(settings, "comp_create_err_channels", "\n⚠️ Błąd tworzenia kanału.");
                            }
                        }

                        if (rolesEnabled && role == null) {
                            msg += getLang(settings, "comp_create_err_roles", "\n⚠️ Błąd tworzenia roli.", compTag);
                        }

                        if (tagsEnabled) {
                            if (tagStatus == 1) {
                                msg += getLang(settings, "comp_acc_tag_err_owner", "\n*(Brak zmiany nicku)*");
                            } else if (tagStatus == 2) {
                                msg += getLang(settings, "comp_create_err_tag", "\n⚠️ Błąd zmiany nicku.");
                            }
                        }

                        event.getHook().sendMessage(msg).setEphemeral(true).queue();

                        // NIEZAWODNE SORTOWANIE (METODA +1)
                        if (rolesEnabled && role != null) {
                            final String targetRoleId = role.getId();
                            final Guild guild = event.getGuild();

                            new Thread(() -> {
                                try {
                                    Thread.sleep(2000);

                                    List<Role> allRoles = guild.getRoles();
                                    Role toMove = guild.getRoleById(targetRoleId);

                                    int anchorIndex = -1;
                                    for (int i = 0; i < allRoles.size(); i++) {
                                        if (allRoles.get(i).getName().toUpperCase().contains("ECONIZER_ANCHOR")) {
                                            anchorIndex = i;
                                            break;
                                        }
                                    }

                                    if (anchorIndex != -1 && toMove != null) {
                                        // anchorIndex + 1 oznacza pozycję dokładnie POD znalezioną kotwicą na liście.
                                        int targetIndex = Math.min(allRoles.size() - 1, anchorIndex + 1);
                                        guild.modifyRolePositions().selectPosition(toMove).moveTo(targetIndex).complete();
                                    }
                                } catch (Exception ex) {
                                    // Błędy ról lecą cicho
                                }
                            }).start();
                        }

                    } catch (Exception e) {
                        event.getHook().sendMessage(getLang(settings, "comp_create_fail", "❌ Błąd bazy danych przy zakładaniu!")).setEphemeral(true).queue();
                    }
                } else {
                    event.getHook().sendMessage(getLang(settings, "comp_no_funds", "❌ Za mało środków!", customCost)).setEphemeral(true).queue();
                }
            }).start();
        }
    }
}