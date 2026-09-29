package br.com.paragonn.dominacao.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cabeças personalizadas por textura (valor base64 de sites como minecraft-heads.com).
 * A API 1.8 não permite definir textura: usa reflexão no GameProfile do authlib (presente no servidor).
 * Se algo falhar, retorna a cabeça padrão em vez de quebrar o menu.
 */
public final class Heads {

    private static final Map<String, ItemStack> CACHE = new HashMap<String, ItemStack>();

    private Heads() {
    }

    public static ItemStack fromTexture(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        }
        ItemStack cached = CACHE.get(base64);
        if (cached == null) {
            cached = build(base64.trim());
            CACHE.put(base64, cached);
        }
        return cached.clone();
    }

    private static ItemStack build(String base64) {
        ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            // UUID derivado da textura: o cliente reaproveita o cache da skin entre aberturas do menu.
            UUID id = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
            Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(id, "dominacao");
            Object properties = profileClass.getMethod("getProperties").invoke(profile);
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", base64);
            properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", property);

            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // mantém a cabeça padrão
        }
        item.setItemMeta(meta);
        return item;
    }
}
