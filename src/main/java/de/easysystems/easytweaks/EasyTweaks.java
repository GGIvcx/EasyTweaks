package de.easysystems.easytweaks;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class EasyTweaks extends JavaPlugin implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private String prefix;
    private SmallTweaks small;
    /** Spieler, denen WIR das Leuchten gesetzt haben. */
    private final Set<UUID> glowing = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        prefix = getConfig().getString("prefix", "<gold>EasyTweaks</gold> <dark_gray>┃</dark_gray> ");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new TweaksGui(this), this);
        small = new SmallTweaks(this);
        getServer().getPluginManager().registerEvents(small, this);
        getServer().getScheduler().runTaskTimer(this, this::tickDragonEgg, 10L, 10L);
    }

    @Override
    public void onDisable() {
        small.removeAllSeats();
        for (UUID id : glowing) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setGlowing(false);
        }
        glowing.clear();
    }

    SmallTweaks small() {
        return small;
    }

    /** Drachenei-Tweak: Leuchten + Ender-Partikel für Spieler mit Drachenei im Inventar. */
    private void tickDragonEgg() {
        boolean enabled = getConfig().getBoolean("dragon-egg-enabled", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean active = enabled && p.getGameMode() != GameMode.SPECTATOR && !isVanished(p)
                    && (p.getInventory().contains(Material.DRAGON_EGG) // nur Hauptinventar (Storage)
                        || p.getInventory().getItemInOffHand().getType() == Material.DRAGON_EGG
                        || p.getItemOnCursor().getType() == Material.DRAGON_EGG);
            if (active) {
                // Fremdes Leuchten (andere Quelle) nicht übernehmen, sonst würden wir es später entfernen
                if (!p.isGlowing()) {
                    glowing.add(p.getUniqueId());
                    p.setGlowing(true);
                }
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.5);
            } else if (glowing.remove(p.getUniqueId())) {
                p.setGlowing(false);
            }
        }
    }

    /** Vanish-Erkennung über die Standard-"vanished"-Metadata (z.B. von EasyVanish gesetzt). */
    private static boolean isVanished(Player p) {
        return p.getMetadata("vanished").stream().anyMatch(m -> m.asBoolean());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (glowing.remove(e.getPlayer().getUniqueId())) e.getPlayer().setGlowing(false);
    }

    /** Sendet eine Nachricht mit Präfix; Sound nach Designsprache (Erfolg/Fehler). */
    void msg(Player p, String text, boolean success, boolean error) {
        p.sendMessage(MM.deserialize(prefix + text));
        if (success) p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        if (error) p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    static final String BYPASS = "easysystems.tweaks.bypass.dimensions";

    /** Wirksamer Dimensions-Bypass: Berechtigung UND persönlicher Schalter nicht ausgeschaltet. */
    boolean bypass(Player p) {
        return p.hasPermission(BYPASS)
                && !p.getPersistentDataContainer().has(new NamespacedKey(this, "bypass-off"), PersistentDataType.BYTE);
    }

    /** Schaltet den persönlichen Bypass um; true = jetzt aktiv. */
    boolean toggleBypass(Player p) {
        NamespacedKey k = new NamespacedKey(this, "bypass-off");
        if (p.getPersistentDataContainer().has(k, PersistentDataType.BYTE)) {
            p.getPersistentDataContainer().remove(k);
            return true;
        }
        p.getPersistentDataContainer().set(k, PersistentDataType.BYTE, (byte) 1);
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(MM.deserialize(prefix + "<red>Dieser Befehl ist nur für Spieler."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("bypass")) {
            if (!p.hasPermission(BYPASS)) {
                msg(p, "<red>Dafür hast du keine Berechtigung.", false, true);
                return true;
            }
            msg(p, toggleBypass(p) ? "<green>Dein Dimensions-Bypass wurde aktiviert." : "<green>Dein Dimensions-Bypass wurde deaktiviert.", true, false);
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
