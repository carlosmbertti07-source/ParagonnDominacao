package br.com.paragonn.dominacao.hook;

import br.com.paragonn.dominacao.arena.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Method;

/**
 * Importa os limites de uma região do WorldGuard 6 (versão da 1.8) para um cuboide.
 * Totalmente por reflexão: o WorldGuard é opcional e não é dependência de compilação.
 */
public final class WorldGuardHook {

    private WorldGuardHook() {
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    /** Retorna null se a região não existir. Lança IllegalStateException se a API não for compatível. */
    public static Cuboid regionBounds(World world, String regionId) {
        try {
            Class<?> wgBukkit = Class.forName("com.sk89q.worldguard.bukkit.WGBukkit");
            Object manager = wgBukkit.getMethod("getRegionManager", World.class).invoke(null, world);
            if (manager == null) {
                return null;
            }
            Object region = manager.getClass().getMethod("getRegion", String.class).invoke(manager, regionId);
            if (region == null) {
                return null;
            }
            Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);
            return new Cuboid(world.getName(), coord(min, "X"), coord(min, "Y"), coord(min, "Z"),
                    coord(max, "X"), coord(max, "Y"), coord(max, "Z"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Versao do WorldGuard nao suportada (use WorldGuard 6.x na 1.8).", e);
        }
    }

    private static int coord(Object vector, String axis) throws ReflectiveOperationException {
        Method method = vector.getClass().getMethod("getBlock" + axis);
        return ((Number) method.invoke(vector)).intValue();
    }
}
