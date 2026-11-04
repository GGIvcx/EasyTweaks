package de.easysystems.easytweaks;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class EasyTweaks extends JavaPlugin implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private String prefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        prefix = getConfig().getString("prefix", "<gold>EasyTweaks</gold> <dark_gray>┃</dark_gray> ");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new TweaksGui(this), this);
    }

    /** Sendet eine Nachricht mit Präfix; Sound nach Designsprache (Erfolg/Fehler). */
    void msg(Player p, String text, boolean success, boolean error) {
        p.sendMessage(MM.deserialize(prefix + text));
        if (success) p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        if (error) p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(MM.deserialize(prefix + "<red>Dieser Befehl ist nur für Spieler."));
            return true;
        }
        if (!p.hasPermission("easysystems.tweaks.gui")) {
            msg(p, "<red>Dafür hast du keine Berechtigung.", false, true);
            return true;
        }
        TweaksGui.openMain(this, p);
        return true;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        if (!getConfig().getBoolean("motd-enabled", true)) return;
        List<String> sayings = getConfig().getStringList("sayings");
        List<? extends Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        Component line2;
        if (!players.isEmpty() && !sayings.isEmpty()) {
            // Spielername als unparsed Platzhalter, damit er nie als MiniMessage-Tag interpretiert wird
            String name = players.get(rnd.nextInt(players.size())).getName();
            String saying = sayings.get(rnd.nextInt(sayings.size())).replace("%player%", "<player>");
            line2 = MM.deserialize(saying, Placeholder.unparsed("player", name));
        } else {
            line2 = MM.deserialize(getConfig().getString("fallback", ""));
        }

        String line1 = getConfig().getString("line1", "");
        Component motd = line1.isBlank()
                ? line2
                : MM.deserialize(line1).appendNewline().append(line2);
        event.motd(motd);
    }
}
