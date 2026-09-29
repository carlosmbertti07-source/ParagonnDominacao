package br.com.paragonn.dominacao.hook;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Integração com Citizens usando apenas a CitizensAPI estável (builds 1.8.8 inclusive).
 * Skin: o Citizens usa o NOME do NPC como skin por padrão; além disso tenta o
 * SkinTrait (builds novos, via reflexão) e a metadata "player-skin-name" (builds antigos).
 */
public final class CitizensHook implements NpcProvider {

    private static final String SKIN_METADATA = "player-skin-name";
    private static final String SKIN_LATEST_METADATA = "player-skin-use-latest";
    private static final String NAMEPLATE_METADATA = "nameplate-visible";

    private final Logger logger;

    public CitizensHook(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void registerClickHandler(Plugin plugin, final ClickHandler handler) {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onRightClick(NPCRightClickEvent event) {
                handler.onClick(event.getClicker(), event.getNPC().getId());
            }

            @EventHandler
            public void onLeftClick(NPCLeftClickEvent event) {
                handler.onClick(event.getClicker(), event.getNPC().getId());
            }
        }, plugin);
    }

    @Override
    public int create(String name, Location location) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.setProtected(true);
        npc.data().setPersistent(NAMEPLATE_METADATA, false);
        npc.spawn(location);
        return npc.getId();
    }

    @Override
    public boolean exists(int id) {
        return npc(id) != null;
    }

    @Override
    public void moveTo(int id, Location location) {
        NPC npc = npc(id);
        if (npc == null) {
            return;
        }
        if (npc.isSpawned()) {
            npc.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } else {
            npc.spawn(location);
        }
    }

    @Override
    public Location location(int id) {
        NPC npc = npc(id);
        if (npc == null) {
            return null;
        }
        if (npc.isSpawned() && npc.getEntity() != null) {
            return npc.getEntity().getLocation();
        }
        return npc.getStoredLocation();
    }

    @Override
    public void setSkin(int id, String skinName, String npcName) {
        NPC npc = npc(id);
        if (npc == null || skinName == null) {
            return;
        }
        Location location = location(id);
        if (npcName != null && !npcName.equals(npc.getName())) {
            npc.setName(npcName); // nome fixo, nunca o do jogador (evita herdar a tag do time dele)
        }
        npc.data().setPersistent(SKIN_METADATA, skinName);
        npc.data().setPersistent(SKIN_LATEST_METADATA, true);
        if (!applySkinTrait(npc, skinName) && npc.isSpawned()) {
            // Citizens sem SkinTrait: respawn para aplicar a skin da metadata.
            Location current = npc.getEntity().getLocation();
            npc.despawn();
            npc.spawn(current);
        }
        if (location != null && !npc.isSpawned()) {
            npc.spawn(location);
        }
    }

    @Override
    public void remove(int id) {
        NPC npc = npc(id);
        if (npc != null) {
            npc.destroy();
        }
    }

    private NPC npc(int id) {
        try {
            return CitizensAPI.getNPCRegistry().getById(id);
        } catch (IllegalStateException citizensNotReady) {
            return null;
        }
    }

    /** SkinTrait existe apenas em builds mais novos do Citizens (fica em citizens-main, não na API). */
    /** Retorna true se a skin foi aplicada pelo SkinTrait (que já atualiza o NPC sozinho). */
    private boolean applySkinTrait(NPC npc, String playerName) {
        try {
            Class<?> traitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
            Method getTrait;
            try {
                getTrait = NPC.class.getMethod("getOrAddTrait", Class.class);
            } catch (NoSuchMethodException legacy) {
                getTrait = NPC.class.getMethod("getTrait", Class.class);
            }
            Object trait = getTrait.invoke(npc, traitClass);
            try {
                traitClass.getMethod("setSkinName", String.class, boolean.class).invoke(trait, playerName, true);
            } catch (NoSuchMethodException legacy) {
                traitClass.getMethod("setSkinName", String.class).invoke(trait, playerName);
            }
            return true;
        } catch (ClassNotFoundException oldCitizens) {
            return false; // Build antigo sem SkinTrait: usa a metadata.
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.fine("SkinTrait indisponivel: " + e);
            return false;
        }
    }
}
