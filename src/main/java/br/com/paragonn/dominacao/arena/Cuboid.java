package br.com.paragonn.dominacao.arena;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/** Região cuboide definida por dois cantos (inclusivos). */
public final class Cuboid {

    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Cuboid of(Location a, Location b) {
        return new Cuboid(a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public static Cuboid read(ConfigurationSection s) {
        return new Cuboid(s.getString("world", "world"),
                s.getInt("pos1.x"), s.getInt("pos1.y"), s.getInt("pos1.z"),
                s.getInt("pos2.x"), s.getInt("pos2.y"), s.getInt("pos2.z"));
    }

    public void write(ConfigurationSection s) {
        s.set("world", world);
        s.set("pos1.x", minX);
        s.set("pos1.y", minY);
        s.set("pos1.z", minZ);
        s.set("pos2.x", maxX);
        s.set("pos2.y", maxY);
        s.set("pos2.z", maxZ);
    }

    public boolean contains(Location location) {
        return location != null
                && location.getWorld() != null
                && location.getWorld().getName().equals(world)
                && contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public String world() {
        return world;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    @Override
    public String toString() {
        return world + " (" + minX + ", " + minY + ", " + minZ + ") -> (" + maxX + ", " + maxY + ", " + maxZ + ")";
    }
}
