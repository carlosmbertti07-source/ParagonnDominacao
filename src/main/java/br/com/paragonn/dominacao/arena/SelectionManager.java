package br.com.paragonn.dominacao.arena;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Guarda pos1/pos2 de cada admin até o /dominacao create. */
public final class SelectionManager {

    private final Map<UUID, Location[]> selections = new HashMap<UUID, Location[]>();

    public void set(UUID player, int index, Location location) {
        Location[] points = selections.get(player);
        if (points == null) {
            points = new Location[2];
            selections.put(player, points);
        }
        points[index] = location.clone();
    }

    /** Retorna os dois pontos ou null se a seleção estiver incompleta. */
    public Location[] get(UUID player) {
        Location[] points = selections.get(player);
        return points == null || points[0] == null || points[1] == null ? null : points;
    }

    public void clear(UUID player) {
        selections.remove(player);
    }
}
