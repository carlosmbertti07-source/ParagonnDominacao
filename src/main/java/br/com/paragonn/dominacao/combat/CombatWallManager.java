package br.com.paragonn.dominacao.combat;

import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.arena.ArenaManager;
import br.com.paragonn.dominacao.arena.Cuboid;
import br.com.paragonn.dominacao.config.ArenaSettings;
import br.com.paragonn.dominacao.config.ConfigManager;
import br.com.paragonn.dominacao.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parede de combate: jogador que entra em PvP dentro da arena não pode sair enquanto estiver em combate.
 *
 * - A parede é feita de blocos FALSOS (sendBlockChange): só o jogador em combate os vê e colide com eles.
 *   Para todos os outros jogadores a borda continua livre, então quem está fora entra normalmente.
 * - Só são mostrados os blocos da borda próximos ao jogador (raio configurável) e apenas onde o bloco real
 *   não é sólido, para não sobrescrever paredes de verdade.
 * - Reforço no servidor: mesmo com cliente modificado, o movimento/teleporte para fora é cancelado.
 */
public final class CombatWallManager implements Listener {

    /** Reenvia a parede inteira a cada N atualizações (corrige blocos "apagados" por cliques no cliente). */
    private static final int FULL_RESEND_EVERY = 4;
    private static final long BLOCKED_MESSAGE_COOLDOWN = 1500L;

    private static final class CombatState {
        Arena arena;
        long expiresAt;
        Set<Long> shown = new HashSet<Long>();
        int updates;
        long lastBlockedMessage;
        /** Bloco onde o jogador estava na última atualização (evita recalcular a parede parado). */
        long lastPosition = Long.MIN_VALUE;
    }

    private final Plugin plugin;
    private final ConfigManager config;
    private final ArenaManager arenas;
    private final MessageManager messages;
    private final Map<UUID, CombatState> states = new HashMap<UUID, CombatState>();
    private BukkitTask task;

    public CombatWallManager(Plugin plugin, ConfigManager config, ArenaManager arenas, MessageManager messages) {
        this.plugin = plugin;
        this.config = config;
        this.arenas = arenas;
        this.messages = messages;
    }

    public void start() {
        stop();
        int period = config.settings().combatWallRefreshTicks;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Desligamento: devolve a visão real dos blocos a todos. */
    public void shutdown() {
        stop();
        for (Map.Entry<UUID, CombatState> entry : states.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                restoreAll(player, entry.getValue());
            }
        }
        states.clear();
    }

    public boolean isInCombat(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ entrada em combate

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        tag(victim);
        tag(attacker);
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
            return (Player) ((Projectile) damager).getShooter();
        }
        return null;
    }

    /** Marca o jogador em combate se ele estiver dentro de uma arena com a parede habilitada. */
    private void tag(Player player) {
        Arena arena = arenas.arenaAt(player.getLocation());
        if (arena == null || !arena.settings().combatWallEnabled) {
            return;
        }
        CombatState state = states.get(player.getUniqueId());
        boolean isNew = state == null || state.arena != arena;
        if (isNew) {
            if (state != null) {
                restoreAll(player, state);
            }
            state = new CombatState();
            state.arena = arena;
            states.put(player.getUniqueId(), state);
        }
        state.expiresAt = System.currentTimeMillis() + arena.settings().combatDurationMillis;
        if (isNew) {
            messages.send(player, "combat.tagged",
                    "seconds", arena.settings().combatDurationMillis / 1000, "arena", arena.displayName());
            updateWall(player, state);
        }
    }

    // ------------------------------------------------------------------ atualização periódica

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, CombatState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CombatState> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            CombatState state = entry.getValue();
            if (player == null) {
                iterator.remove();
                continue;
            }
            Arena current = arenas.get(state.arena.name());
            boolean valid = current == state.arena && state.arena.isActive() && state.arena.settings().combatWallEnabled;
            if (!valid || now >= state.expiresAt || !state.arena.contains(player.getLocation())) {
                restoreAll(player, state);
                iterator.remove();
                if (valid && now >= state.expiresAt) {
                    messages.send(player, "combat.expired", "arena", state.arena.displayName());
                }
                continue;
            }
            updateWall(player, state);
        }
    }

    /** Envia os blocos da parede próximos ao jogador e restaura os que saíram do raio. */
    private void updateWall(Player player, CombatState state) {
        boolean fullResend = ++state.updates % FULL_RESEND_EVERY == 0;
        Location location = player.getLocation();
        long position = pack(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (!fullResend && position == state.lastPosition) {
            return; // parado no mesmo bloco: nada muda
        }
        state.lastPosition = position;
        Set<Long> desired = computeWall(player, state.arena);
        ArenaSettings settings = state.arena.settings();
        World world = player.getWorld();

        for (Long key : state.shown) {
            if (!desired.contains(key)) {
                restore(player, world, key);
            }
        }
        for (Long key : desired) {
            if (fullResend || !state.shown.contains(key)) {
                sendFake(player, world, key, settings);
            }
        }
        state.shown = desired;
    }

    /** Posições da borda (logo fora da região) dentro do raio, onde o bloco real não é sólido. */
    private static Set<Long> computeWall(Player player, Arena arena) {
        Set<Long> wall = new HashSet<Long>();
        Cuboid region = arena.region();
        World world = player.getWorld();
        if (region == null || !world.getName().equals(region.world())) {
            return wall;
        }
        int radius = arena.settings().wallRadius;
        Location location = player.getLocation();
        int px = location.getBlockX();
        int py = location.getBlockY();
        int pz = location.getBlockZ();

        int yMin = Math.max(region.minY(), py - radius);
        int yMax = Math.min(region.maxY() + 1, py + radius);
        int westX = region.minX() - 1;
        int eastX = region.maxX() + 1;
        int northZ = region.minZ() - 1;
        int southZ = region.maxZ() + 1;

        int zFrom = Math.max(northZ, pz - radius);
        int zTo = Math.min(southZ, pz + radius);
        int xFrom = Math.max(westX, px - radius);
        int xTo = Math.min(eastX, px + radius);

        for (int y = yMin; y <= yMax; y++) {
            if (Math.abs(px - westX) <= radius) {
                addColumn(wall, world, westX, y, zFrom, zTo, true);
            }
            if (Math.abs(px - eastX) <= radius) {
                addColumn(wall, world, eastX, y, zFrom, zTo, true);
            }
            if (Math.abs(pz - northZ) <= radius) {
                addColumn(wall, world, northZ, y, xFrom, xTo, false);
            }
            if (Math.abs(pz - southZ) <= radius) {
                addColumn(wall, world, southZ, y, xFrom, xTo, false);
            }
        }
        return wall;
    }

    /** fixedIsX: a coordenada fixa é X (percorre Z); senão a fixa é Z (percorre X). */
    private static void addColumn(Set<Long> wall, World world, int fixed, int y, int from, int to, boolean fixedIsX) {
        for (int v = from; v <= to; v++) {
            int x = fixedIsX ? fixed : v;
            int z = fixedIsX ? v : fixed;
            if (!world.getBlockAt(x, y, z).getType().isSolid()) {
                wall.add(pack(x, y, z));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void sendFake(Player player, World world, long key, ArenaSettings settings) {
        player.sendBlockChange(new Location(world, unpackX(key), unpackY(key), unpackZ(key)),
                settings.wallMaterial, settings.wallData);
    }

    @SuppressWarnings("deprecation")
    private static void restore(Player player, World world, long key) {
        Block block = world.getBlockAt(unpackX(key), unpackY(key), unpackZ(key));
        player.sendBlockChange(block.getLocation(), block.getType(), block.getData());
    }

    private static void restoreAll(Player player, CombatState state) {
        World world = player.getWorld();
        for (Long key : state.shown) {
            restore(player, world, key);
        }
        state.shown = new HashSet<Long>();
        state.lastPosition = Long.MIN_VALUE;
    }

    // ------------------------------------------------------------------ reforço no servidor

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        CombatState state = states.get(event.getPlayer().getUniqueId());
        if (state == null || !state.arena.contains(from) || state.arena.contains(to)) {
            return;
        }
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        event.setTo(back);
        notifyBlocked(event.getPlayer(), state, "combat.blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        CombatState state = states.get(event.getPlayer().getUniqueId());
        if (state == null || event.getTo() == null
                || !state.arena.settings().blockedTeleportCauses.contains(event.getCause())
                || !state.arena.contains(event.getFrom()) || state.arena.contains(event.getTo())) {
            return;
        }
        event.setCancelled(true);
        notifyBlocked(event.getPlayer(), state, "combat.teleport-blocked");
    }

    private void notifyBlocked(Player player, CombatState state, String path) {
        long now = System.currentTimeMillis();
        if (now - state.lastBlockedMessage >= BLOCKED_MESSAGE_COOLDOWN) {
            state.lastBlockedMessage = now;
            long seconds = Math.max(1, (state.expiresAt - now + 999) / 1000);
            messages.send(player, path, "seconds", seconds, "arena", state.arena.displayName());
        }
    }

    // ------------------------------------------------------------------ saída do combate

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        CombatState state = states.remove(event.getEntity().getUniqueId());
        if (state != null) {
            restoreAll(event.getEntity(), state);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ posição compactada em long

    private static long pack(int x, int y, int z) {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 38);
    }

    private static int unpackY(long key) {
        return (int) (key << 52 >> 52);
    }

    private static int unpackZ(long key) {
        return (int) (key << 26 >> 38);
    }
}
