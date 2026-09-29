package br.com.paragonn.dominacao.npc;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Holograma simples com ArmorStands (sem dependências, funciona na 1.8.8).
 * As entidades são removidas quando o chunk descarrega, para nunca serem salvas
 * no mundo (evita hologramas duplicados após restart ou crash).
 */
public final class Hologram {

    private final List<ArmorStand> stands = new ArrayList<ArmorStand>();
    private final List<String> lines = new ArrayList<String>();
    private Location top;

    /** Atualiza o texto; recria as entidades apenas se o número de linhas ou a posição mudar. */
    public void update(Location newTop, List<String> newLines, double spacing) {
        if (newTop == null || newTop.getWorld() == null || !isLoaded(newTop)) {
            remove();
            return;
        }
        boolean rebuild = top == null
                || !sameSpot(top, newTop)
                || stands.size() != newLines.size()
                || !allValid();
        if (rebuild) {
            remove();
            spawn(newTop, newLines, spacing);
            return;
        }
        for (int i = 0; i < newLines.size(); i++) {
            if (!newLines.get(i).equals(lines.get(i))) {
                applyText(stands.get(i), newLines.get(i));
                lines.set(i, newLines.get(i));
            }
        }
    }

    public void remove() {
        for (ArmorStand stand : stands) {
            stand.remove();
        }
        stands.clear();
        lines.clear();
        top = null;
    }

    public boolean isIn(Chunk chunk) {
        return top != null
                && top.getWorld().equals(chunk.getWorld())
                && (top.getBlockX() >> 4) == chunk.getX()
                && (top.getBlockZ() >> 4) == chunk.getZ();
    }

    private void spawn(Location newTop, List<String> newLines, double spacing) {
        removeLeftovers(newTop, newLines.size() * spacing + 1.0);
        World world = newTop.getWorld();
        for (int i = 0; i < newLines.size(); i++) {
            Location location = newTop.clone().subtract(0, i * spacing, 0);
            ArmorStand stand = world.spawn(location, ArmorStand.class);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setMarker(true);
            applyText(stand, newLines.get(i));
            stands.add(stand);
        }
        lines.addAll(newLines);
        top = newTop.clone();
    }

    /** Remove ArmorStands invisíveis e sem gravidade que tenham sobrado nessa coluna (crash, reload). */
    private void removeLeftovers(Location column, double height) {
        for (Entity entity : column.getChunk().getEntities()) {
            if (!(entity instanceof ArmorStand)) {
                continue;
            }
            ArmorStand stand = (ArmorStand) entity;
            Location location = stand.getLocation();
            if (!stand.isVisible() && !stand.hasGravity()
                    && Math.abs(location.getX() - column.getX()) < 0.3
                    && Math.abs(location.getZ() - column.getZ()) < 0.3
                    && location.getY() <= column.getY() + 0.3
                    && location.getY() >= column.getY() - height) {
                stand.remove();
            }
        }
    }

    private static void applyText(ArmorStand stand, String text) {
        boolean visible = text != null && !text.trim().isEmpty();
        stand.setCustomName(visible ? text : " ");
        stand.setCustomNameVisible(visible);
    }

    private boolean allValid() {
        for (ArmorStand stand : stands) {
            if (!stand.isValid()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLoaded(Location location) {
        return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private static boolean sameSpot(Location a, Location b) {
        return a.getWorld().equals(b.getWorld()) && a.distanceSquared(b) < 0.01;
    }
}
