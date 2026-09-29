package br.com.paragonn.dominacao.kill;

import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.arena.ArenaManager;
import br.com.paragonn.dominacao.clan.ClanManager;
import br.com.paragonn.dominacao.config.ArenaSettings;
import br.com.paragonn.dominacao.config.MessageManager;
import br.com.paragonn.dominacao.npc.NpcManager;
import br.com.paragonn.dominacao.score.ScoreManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contabiliza abates e mortes DENTRO das arenas.
 * Não conta: fora da arena, sem clan, suicídio, morte sem assassino, mesmo clan,
 * aliados (configurável) e abates repetidos dentro do cooldown anti-farm.
 */
public final class KillManager implements Listener {

    private final ArenaManager arenas;
    private final ClanManager clans;
    private final ScoreManager scores;
    private final MessageManager messages;
    private final NpcManager npcs;
    /** "arena:vitima:clanDoAssassino" -> momento do último abate que deu pontos. */
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<String, Long>();

    public KillManager(ArenaManager arenas, ClanManager clans, ScoreManager scores,
                       MessageManager messages, NpcManager npcs) {
        this.arenas = arenas;
        this.clans = clans;
        this.scores = scores;
        this.messages = messages;
        this.npcs = npcs;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Arena arena = arenas.arenaAt(victim.getLocation());
        if (arena == null) {
            return;
        }
        ArenaSettings settings = arena.settings();
        String victimClan = clans.clanKey(victim);
        if (victimClan != null) {
            scores.addDeath(arena.name(), victim.getUniqueId(), victim.getName(), victimClan);
            npcs.markDirty(arena); // mortes do campeão aparecem na hora
        }

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || victimClan == null) {
            return;
        }
        String killerClan = clans.clanKey(killer);
        if (killerClan == null || killerClan.equals(victimClan)) {
            return;
        }
        if (settings.ignoreAllies && clans.areAllies(killerClan, victimClan)) {
            return;
        }
        if (settings.requireKillerInside && !arena.contains(killer.getLocation())) {
            return;
        }
        if (settings.antiFarmEnabled && isOnCooldown(arena, victim, killerClan, settings.killCooldownMillis)) {
            messages.send(killer, "anti-farm", "player", victim.getName());
            return;
        }

        scores.addKill(arena.name(), killer.getUniqueId(), killer.getName(), killerClan, settings.killPoints);
        messages.send(killer, "kill-points",
                "points", settings.killPoints, "player", victim.getName(),
                "clan", clans.display(killerClan), "arena", arena.displayName());
        npcs.markDirty(arena); // campeão + holograma de top atualizados em tempo real
    }

    private boolean isOnCooldown(Arena arena, Player victim, String killerClan, long cooldownMillis) {
        String key = arena.name() + ':' + victim.getUniqueId() + ':' + killerClan;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < cooldownMillis) {
            return true;
        }
        cooldowns.put(key, now);
        return false;
    }

    /** Remove entradas expiradas (chamado periodicamente). */
    public void cleanupCooldowns(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() > maxAgeMillis) {
                iterator.remove();
            }
        }
    }
}
