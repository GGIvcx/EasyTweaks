package de.easysystems.easytweaks;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class EasyTweaks extends JavaPlugin implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
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
