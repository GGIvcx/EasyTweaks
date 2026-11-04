package de.easysystems.easytweaks;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Admin-GUI (Hauptmenü + MOTD-Untermenü) samt Chat-Eingabe. */
public class TweaksGui implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int MAIN = 0, MOTD = 1, SAYINGS = 2, CONFIRM = 3;
    private static final int PER_PAGE = 45;

    private final EasyTweaks plugin;
    /**
     * Offene Chat-Eingabe: key != null -> Config-Key setzen; sonst index < 0 -> Spruch anhängen,
     * sonst Spruch ersetzen (expected = erwarteter alter Text). Zugriff auch vom Async-Chat-Thread.
     */
    private record Input(String key, int index, String expected, int page) { }

    private final Map<UUID, Input> pending = new ConcurrentHashMap<>();

    TweaksGui(EasyTweaks plugin) {
        this.plugin = plugin;
    }

    /** page/list (Anzeige-Snapshot) für Sprüche-Menü; index/expected für Löschbestätigung. */
    private record Menu(int type, int page, List<String> list, int index, String expected) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null; // wird nicht benötigt, Erkennung erfolgt über den Holder
        }
    }

    // --- Menüs ---

    static void openMain(EasyTweaks plugin, Player p) {
        Inventory inv = base(27, "<dark_gray>EasyTweaks", MAIN);
        boolean on = plugin.getConfig().getBoolean("motd-enabled", true);
        // Weiterer Tweak = ein weiterer inv.setItem(...) hier und ein case in onClick
        inv.setItem(11, item(Material.OAK_SIGN, "<gold>MOTD",
                status(on), "<gray>Zufälliger Spruch mit Spielername", "<gray>in der Serverliste.", "",
                "<dark_gray>» <yellow>Linksklick zum Umschalten", "<dark_gray>» <yellow>Rechtsklick für Einstellungen"));
        inv.setItem(22, item(Material.BARRIER, "<red>Schließen"));
        p.openInventory(inv);
    }

    static void openMotd(EasyTweaks plugin, Player p) {
        Inventory inv = base(27, "<dark_gray>MOTD", MOTD);
        boolean on = plugin.getConfig().getBoolean("motd-enabled", true);
        inv.setItem(10, item(on ? Material.LIME_DYE : Material.GRAY_DYE, on ? "<green>MOTD an" : "<red>MOTD aus",
                status(on), "", "<dark_gray>» <yellow>Klicken zum Umschalten"));
        inv.setItem(12, valueItem(Material.NAME_TAG, "Erste Zeile", plugin.getConfig().getString("line1", "")));
        inv.setItem(14, valueItem(Material.PAPER, "Fallback", plugin.getConfig().getString("fallback", "")));

        List<String> sayings = plugin.getConfig().getStringList("sayings");
        List<Component> lore = new ArrayList<>();
        lore.add(line("<gray>Anzahl: <yellow><v>", sayings.size()));
        for (String s : sayings.subList(0, Math.min(3, sayings.size()))) {
            lore.add(line("<dark_gray>» <gray><v>", s));
        }
        if (sayings.size() > 3) lore.add(line("<dark_gray>... <gray>und <yellow><v></yellow> weitere", sayings.size() - 3));
        lore.add(line(""));
        lore.add(line("<dark_gray>» <yellow>Klicken zum Bearbeiten"));
        inv.setItem(16, build(Material.BOOK, "<gold>Sprüche", lore));
        inv.setItem(22, item(Material.ARROW, "<yellow>Zurück"));
        p.openInventory(inv);
    }

    static void openSayings(EasyTweaks plugin, Player p, int page) {
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList("sayings"));
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = base(54, "<dark_gray>Sprüche <gray>(" + (page + 1) + "/" + pages + ")",
                new Menu(SAYINGS, page, list, -1, null));
        if (list.isEmpty()) inv.setItem(22, item(Material.BARRIER, "<red>Keine Sprüche vorhanden"));
        for (int i = page * PER_PAGE, slot = 0; i < list.size() && slot < PER_PAGE; i++, slot++) {
            inv.setItem(slot, build(Material.PAPER, "<gold>Spruch #" + (i + 1), List.of(
                    line("<yellow><v>", list.get(i)), line(""),
                    line("<dark_gray>» <yellow>Linksklick zum Bearbeiten"),
                    line("<dark_gray>» <yellow>Rechtsklick zum Löschen"))));
        }
        if (page > 0) inv.setItem(45, item(Material.ARROW, "<yellow>Vorherige Seite"));
        inv.setItem(47, item(Material.WRITABLE_BOOK, "<green>Neuer Spruch",
                "<gray>%player% wird durch einen zufälligen", "<gray>Online-Spieler ersetzt.",
                "<gray>MiniMessage ist erlaubt.", "", "<dark_gray>» <yellow>Klicken zum Hinzufügen"));
        inv.setItem(49, item(Material.ARROW, "<yellow>Zurück"));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, "<yellow>Nächste Seite"));
        p.openInventory(inv);
    }

    private static void openConfirm(Player p, int page, int index, String saying) {
        Inventory inv = base(27, "<dark_gray>Spruch löschen?", new Menu(CONFIRM, page, null, index, saying));
        inv.setItem(11, item(Material.LIME_WOOL, "<green>Bestätigen"));
        inv.setItem(13, build(Material.PAPER, "<gold>Spruch #" + (index + 1), List.of(line("<yellow><v>", saying))));
        inv.setItem(15, item(Material.RED_WOOL, "<red>Abbrechen"));
        p.openInventory(inv);
    }

    private static Inventory base(int size, String title, int type) {
        return base(size, title, new Menu(type, 0, null, -1, null));
    }

    private static Inventory base(int size, String title, Menu holder) {
        Inventory inv = Bukkit.createInventory(holder, size, MM.deserialize(title));
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = filler.getItemMeta();
        m.displayName(Component.empty());
        filler.setItemMeta(m);
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
        return inv;
    }

    private static String status(boolean on) {
        return on ? "<gray>Status: <green>An" : "<gray>Status: <red>Aus";
    }

    private static Component line(String mm, Object value) {
        return MM.deserialize("<!italic>" + mm, Placeholder.unparsed("v", String.valueOf(value)));
    }

    private static Component line(String mm) {
        return MM.deserialize("<!italic>" + mm);
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(line(s));
        return build(mat, name, l);
    }

    private static ItemStack build(Material mat, String name, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(line(name));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack valueItem(Material mat, String label, String value) {
        List<Component> lore = new ArrayList<>();
        lore.add(value.isBlank() ? line("<gray>Wert: <yellow>(leer)") : line("<gray>Wert: <yellow><v>", value));
        lore.add(line(""));
        lore.add(line("<dark_gray>» <yellow>Klicken zum Ändern"));
        return build(mat, "<gold>" + label, lore);
    }

    // --- Events ---

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)
                || e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        int slot = e.getSlot();
        // Leerzustand und Vorschau im Bestätigungsdialog sind keine aktiven Items
        if ((menu.type() == SAYINGS && it.getType() == Material.BARRIER) || (menu.type() == CONFIRM && slot == 13)) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        if (menu.type() == MAIN) {
            if (slot == 22) p.closeInventory();
            else if (slot == 11) {
                if (e.getClick() == ClickType.RIGHT) openMotd(plugin, p);
                else if (e.getClick() == ClickType.LEFT) { toggle(); openMain(plugin, p); }
            }
        } else if (menu.type() == SAYINGS) {
            sayingsClick(p, menu, slot, e.getClick());
        } else if (menu.type() == CONFIRM) {
            if (slot == 11) {
                List<String> list = plugin.getConfig().getStringList("sayings");
                if (staleOrSame(list, menu.index(), menu.expected())) {
                    list.remove(menu.index());
                    plugin.getConfig().set("sayings", list);
                    plugin.saveConfig();
                    plugin.msg(p, "<green>Spruch gelöscht.", true, false);
                } else stale(p);
                openSayings(plugin, p, menu.page());
            } else if (slot == 15) openSayings(plugin, p, menu.page());
        } else {
            switch (slot) {
                case 10 -> { toggle(); openMotd(plugin, p); }
                case 12 -> prompt(p, "line1", "die neue erste MOTD-Zeile");
                case 14 -> prompt(p, "fallback", "den neuen Fallback-Text");
                case 16 -> openSayings(plugin, p, 0);
                case 22 -> openMain(plugin, p);
                default -> { }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        // Menü erneut geöffnet -> offene Chat-Eingabe verwerfen
        if (e.getInventory().getHolder(false) instanceof Menu) pending.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Input in = pending.remove(p.getUniqueId());
        if (in == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (!p.hasPermission("easysystems.tweaks.gui")) {
                plugin.msg(p, "<red>Dafür hast du keine Berechtigung.", false, true);
                return;
            }
            if (text.equalsIgnoreCase("abbrechen")) {
                plugin.msg(p, "<gray>Eingabe abgebrochen.", false, false);
            } else if (in.key() != null) {
                plugin.getConfig().set(in.key(), text);
                plugin.saveConfig();
                plugin.msg(p, "<green>Wert gespeichert.", true, false);
            } else if (text.isEmpty()) {
                plugin.msg(p, "<red>Der Spruch darf nicht leer sein.", false, true);
            } else {
                List<String> list = plugin.getConfig().getStringList("sayings");
                if (in.index() < 0) list.add(text);
                else if (staleOrSame(list, in.index(), in.expected())) list.set(in.index(), text);
                else { stale(p); openSayings(plugin, p, in.page()); return; }
                plugin.getConfig().set("sayings", list);
                plugin.saveConfig();
                plugin.msg(p, "<green>Spruch gespeichert.", true, false);
            }
            if (in.key() != null) openMotd(plugin, p);
            else openSayings(plugin, p, in.index() < 0 ? Integer.MAX_VALUE : in.page());
        });
    }

    private void sayingsClick(Player p, Menu menu, int slot, ClickType click) {
        int page = menu.page();
        if (slot < PER_PAGE) {
            int i = page * PER_PAGE + slot;
            if (i >= menu.list().size()) return;
            String saying = menu.list().get(i);
            if (click == ClickType.LEFT) {
                input(p, new Input(null, i, saying, page), "den neuen Spruch");
            } else if (click == ClickType.RIGHT) {
                openConfirm(p, page, i, saying);
            }
            return;
        }
        switch (slot) {
            case 45 -> openSayings(plugin, p, page - 1);
            case 47 -> input(p, new Input(null, -1, null, page), "den neuen Spruch");
            case 49 -> openMotd(plugin, p);
            case 53 -> openSayings(plugin, p, page + 1);
            default -> { }
        }
    }

    private static boolean staleOrSame(List<String> list, int i, String expected) {
        return i >= 0 && i < list.size() && list.get(i).equals(expected);
    }

    private void stale(Player p) {
        plugin.msg(p, "<red>Der Spruch wurde inzwischen geändert.", false, true);
    }

    private void toggle() {
        plugin.getConfig().set("motd-enabled", !plugin.getConfig().getBoolean("motd-enabled", true));
        plugin.saveConfig();
    }

    private void prompt(Player p, String key, String what) {
        input(p, new Input(key, -1, null, 0), what);
    }

    private void input(Player p, Input in, String what) {
        pending.put(p.getUniqueId(), in);
        p.closeInventory();
        plugin.msg(p, "<gray>Gib " + what
                + " im Chat ein. <dark_gray>(</dark_gray><yellow>abbrechen</yellow><dark_gray>)</dark_gray>", false, false);
    }
}
