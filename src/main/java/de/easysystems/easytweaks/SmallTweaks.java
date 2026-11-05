package de.easysystems.easytweaks;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/** Die kleinen Event-Tweaks: Sitzen, Arme, Item-Rahmen, Köpfe, Phantome, Nether-/End-Sperre, Happy Ghast. */
public class SmallTweaks implements Listener {

    static final double MIN_SPEED = 0.5, MAX_SPEED = 5.0;
    /** Vanilla-Fluggeschwindigkeit des Happy Ghasts (AttributeInstance#getDefaultValue liefert den globalen Attribut-Standard 0.4). */
    private static final double GHAST_BASE_SPEED = 0.05;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * Stand-Höhe relativ zum Blockboden. Vanilla: Marker-Stand = EntityDimensions.fixed(0, 0), Passagier-Anker
     * (Fallback 0, Höhe, 0) = Stand-y; Spieler-Füße = Anker - Avatar.DEFAULT_VEHICLE_ATTACHMENT (0.6).
     * Im Sitzmodell liegt die Oberschenkel-Unterseite ca. 0.59 über den Füßen (Hüfte 12px * 0.9375 minus Beindicke),
     * d.h. der Po sitzt genau auf dem Anker. Po soll auf die Oberkante y+0.5 -> Stand y+0.5.
     * (Früher y+1.1: Füße auf der Oberkante, Po 0.6 darüber -> "auf" statt "in" der Treppe.)
     */
    private static final double SEAT_Y = 0.5;
    /** Aufstehen: Füße (Stand-y - 0.6) auf die Oberkante (Block-y + 0.5) heben. */
    private static final double STAND_UP = 0.5 - SEAT_Y;

    private final EasyTweaks plugin;
    private final NamespacedKey seatKey;
    /** Belegte Sitzblöcke -> Sitz-Stand. */
    private final Map<Block, ArmorStand> seats = new HashMap<>();
    /** Letzte Sperr-Nachricht pro Spieler (Rate-Limit, Portal feuert das Event wiederholt). */
    private final Map<UUID, Long> lockMsg = new HashMap<>();

    SmallTweaks(EasyTweaks plugin) {
        this.plugin = plugin;
        this.seatKey = new NamespacedKey(plugin, "seat");
        // Sitze aufräumen, deren Block weg/verändert ist (Explosion, Kolben, ...) oder deren Stand nicht mehr existiert
        Bukkit.getScheduler().runTaskTimer(plugin, () -> seats.entrySet().removeIf(en -> {
            if (en.getValue().isValid() && isSeatBlock(en.getKey())) return false;
            en.getValue().remove();
            return true;
        }), 20L, 20L);
    }

    private boolean on(String key) {
        return plugin.getConfig().getBoolean(key, true);
    }

    static double ghastSpeed(EasyTweaks plugin) {
        double v = plugin.getConfig().getDouble("happy-ghast-speed", 1.5);
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, v));
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    // --- Sitzen ---

    private boolean isSeat(Entity en) {
        return en instanceof ArmorStand && en.getPersistentDataContainer().has(seatKey, PersistentDataType.BYTE);
    }

    private static boolean isSeatBlock(Block b) {
        BlockData d = b.getBlockData();
        return ((d instanceof Stairs s && s.getHalf() == Bisected.Half.BOTTOM)
                || (d instanceof Slab sl && sl.getType() == Slab.Type.BOTTOM))
                && b.getRelative(BlockFace.UP).isPassable();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSit(PlayerInteractEvent e) {
        if (!on("sit-enabled") || e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK
                || e.getBlockFace() != BlockFace.UP) return;
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null || p.isSneaking() || !p.getInventory().getItemInMainHand().isEmpty()
                || p.isInsideVehicle() || p.isFlying() || p.getGameMode() == GameMode.SPECTATOR) return;
        if (!isSeatBlock(b)) return;
        e.setCancelled(true);
        ArmorStand old = seats.get(b);
        if (old != null && old.isValid()) return;
        Location loc = b.getLocation().add(0.5, SEAT_Y, 0.5);
        // facing zeigt zur Lehne (hohe Seite) -> Blick in die Gegenrichtung
        loc.setYaw(b.getBlockData() instanceof Stairs st
                ? loc.clone().setDirection(st.getFacing().getOppositeFace().getDirection()).getYaw() : p.getLocation().getYaw());
        ArmorStand stand = b.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setVisible(false);
            a.setMarker(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setPersistent(false);
            a.getPersistentDataContainer().set(seatKey, PersistentDataType.BYTE, (byte) 1);
        });
        seats.put(b, stand);
        // Stand-Yaw dreht den Blick des Passagiers nicht -> Spieler vor dem Aufsitzen selbst ausrichten
        Location view = p.getLocation();
        view.setYaw(loc.getYaw());
        p.teleport(view);
        stand.addPassenger(p);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!isSeat(e.getDismounted())) return;
        ArmorStand stand = (ArmorStand) e.getDismounted();
        Entity who = e.getEntity();
        double topY = stand.getLocation().getY() + STAND_UP;
        if (!plugin.isEnabled()) return; // onDisable: removeAllSeats räumt auf, Tasks sind dann nicht mehr erlaubt
        Bukkit.getScheduler().runTask(plugin, () -> {
            removeSeat(stand);
            // Nur anheben, wenn dort Platz ist (kein Clippen in Decken/Blöcke)
            if (who instanceof Player p && p.isOnline() && !p.isDead()) {
                double dy = topY - p.getLocation().getY();
                if (dy > 0 && !p.getWorld().hasCollisionsIn(p.getBoundingBox().shift(0, dy, 0)))
                    p.teleport(p.getLocation().add(0, dy, 0));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lockMsg.remove(e.getPlayer().getUniqueId());
        if (isSeat(e.getPlayer().getVehicle())) removeSeat((ArmorStand) e.getPlayer().getVehicle());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (isSeat(victim.getVehicle())) removeSeat((ArmorStand) victim.getVehicle());

        // Spielerköpfe
        Player killer = victim.getKiller();
        if (!on("player-heads-enabled") || killer == null || killer.equals(victim)) return;
        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) head.getItemMeta();
        m.setOwningPlayer(victim);
        m.displayName(MM.deserialize("<!italic><gold>Kopf von <name>", Placeholder.unparsed("name", victim.getName())));
        m.lore(List.of(
                MM.deserialize("<!italic><gray>Getötet von <yellow><killer>", Placeholder.unparsed("killer", killer.getName())),
                MM.deserialize("<!italic><gray>Am <yellow><date>", Placeholder.unparsed("date",
                        LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))))));
        head.setItemMeta(m);
        e.getDrops().add(head);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        ArmorStand stand = seats.get(e.getBlock());
        if (stand != null) removeSeat(stand);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (isSeat(en)) en.remove(); // Reste nach einem Crash
    }

    private void removeSeat(ArmorStand stand) {
        seats.values().remove(stand);
        stand.remove();
    }

    void removeAllSeats() {
        for (ArmorStand s : new ArrayList<>(seats.values())) removeSeat(s);
    }

    // --- Rüstungsständer mit Armen ---

    @EventHandler(ignoreCancelled = true)
    public void onPlaceStand(EntityPlaceEvent e) {
        if (on("armor-stand-arms-enabled") && e.getPlayer() != null
                && e.getEntity() instanceof ArmorStand a && !isSeat(a)) a.setArms(true);
    }

    // --- Unsichtbare Item-Rahmen ---

    @EventHandler(ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!on("invisible-frames-enabled") || !(e.getRightClicked() instanceof ItemFrame f)
                || !p.isSneaking() || !p.getInventory().getItemInMainHand().isEmpty()) return;
        e.setCancelled(true); // auch OFF_HAND canceln, sonst landet ein Item aus der Nebenhand im Rahmen
        if (e.getHand() != EquipmentSlot.HAND) return;
        f.setVisible(!f.isVisible());
        p.sendActionBar(MM.deserialize(f.isVisible()
                ? "<gray>Item-Rahmen ist jetzt <yellow>sichtbar" : "<gray>Item-Rahmen ist jetzt <yellow>unsichtbar"));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    // --- Keine Phantome ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() == EntityType.PHANTOM && e.getSpawnReason() == SpawnReason.NATURAL
                && on("no-phantoms-enabled")) e.setCancelled(true);
    }

    // --- Nether-/End-Sperre ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        if (denied(e.getPlayer(), e.getFrom().getWorld(), e.getTo().getWorld())) e.setCancelled(true);
    }

    /** Betreten von to aus einer anderen Dimension gesperrt? (End-Gateways End -> End bleiben nutzbar) */
    private boolean denied(Player p, World from, World to) {
        if (to == null || from != null && from.getEnvironment() == to.getEnvironment()) return false;
        return blocked(p, to.getEnvironment());
    }

    /** Prüft Sperre + Bypass und sendet (rate-limitiert) die Meldung. p == null (Feuer, Dispenser) = kein Bypass. */
    private boolean blocked(Player p, Environment env) {
        if (!locked(env) || p != null && plugin.bypass(p)) return false;
        if (p != null) notifyLocked(p, env);
        return true;
    }

    private boolean locked(Environment env) {
        return env == Environment.NETHER ? plugin.getConfig().getBoolean("nether-lock-enabled", false)
                : env == Environment.THE_END && plugin.getConfig().getBoolean("end-lock-enabled", false);
    }

    private void notifyLocked(Player p, Environment env) {
        long now = System.currentTimeMillis();
        if (now - lockMsg.getOrDefault(p.getUniqueId(), 0L) < 3000) return;
        lockMsg.put(p.getUniqueId(), now);
        plugin.msg(p, env == Environment.NETHER ? "<red>Der Nether ist derzeit gesperrt." : "<red>Das End ist derzeit gesperrt.", false, true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        if (anyBypass(e.getEntity())) return;
        if (denyPassengers(e.getEntity(), e.getFrom().getWorld(), e.getTo().getWorld())) e.setCancelled(true);
    }

    /** Hat irgendein (auch indirekter) Spieler-Passagier einen wirksamen Bypass? */
    private boolean anyBypass(Entity en) {
        for (Entity pass : en.getPassengers()) {
            if (pass instanceof Player p ? plugin.bypass(p) : anyBypass(pass)) return true;
        }
        return false;
    }

    private boolean denyPassengers(Entity en, World from, World to) {
        boolean deny = false;
        for (Entity pass : en.getPassengers()) {
            if (pass instanceof Player p ? denied(p, from, to) : denyPassengers(pass, from, to)) deny = true;
        }
        return deny;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearl(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL || e.getTo() == null) return;
        if (denied(e.getPlayer(), e.getFrom().getWorld(), e.getTo().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        if (e.getReason() == PortalCreateEvent.CreateReason.FIRE && e.getWorld().getEnvironment() != Environment.NETHER
                && blocked(e.getEntity() instanceof Player p ? p : null, Environment.NETHER)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEyePlace(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || b == null || b.getType() != Material.END_PORTAL_FRAME
                || e.getItem() == null || e.getItem().getType() != Material.ENDER_EYE
                || ((EndPortalFrame) b.getBlockData()).hasEye()) return;
        if (blocked(e.getPlayer(), Environment.THE_END)) e.setCancelled(true);
    }

    // --- Happy Ghast ---

    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent e) {
        if (e.getMount() instanceof HappyGhast g && e.getEntity() instanceof Player) applySpeed(g);
    }

    private void applySpeed(HappyGhast g) {
        AttributeInstance a = g.getAttribute(Attribute.FLYING_SPEED);
        if (a != null) a.setBaseValue(GHAST_BASE_SPEED * (on("happy-ghast-enabled") ? ghastSpeed(plugin) : 1.0));
    }

    /** Nach Änderung im Menü: alle Happy Ghasts mit Spieler-Passagier aktualisieren. */
    void updateRiddenGhasts() {
        for (var w : Bukkit.getWorlds())
            for (HappyGhast g : w.getEntitiesByClass(HappyGhast.class))
                if (g.getPassengers().stream().anyMatch(Player.class::isInstance)) applySpeed(g);
    }
}
