package br.com.paragonn.dominacao.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * ActionBar compatível com 1.8.8 (não existe na API Bukkit dessa versão).
 * 1) Tenta a API do Spigot moderno (ChatMessageType.ACTION_BAR) via reflexão.
 * 2) Senão usa NMS: PacketPlayOutChat(IChatBaseComponent, byte 2) — padrão da 1.8.
 * Todos os handles de reflexão são resolvidos uma única vez.
 */
public final class ActionBar {

    private interface Sender {
        void send(Player player, String message) throws Exception;
    }

    private final Sender sender;
    private final Logger logger;
    private boolean failureLogged;

    private ActionBar(Sender sender, Logger logger) {
        this.sender = sender;
        this.logger = logger;
    }

    public static ActionBar create(Logger logger) {
        Sender sender = trySpigotApi();
        if (sender == null) {
            sender = tryNms(logger);
        }
        return new ActionBar(sender, logger);
    }

    public void send(Player player, String message) {
        if (sender == null) {
            return;
        }
        try {
            sender.send(player, message);
        } catch (Exception e) {
            if (!failureLogged) {
                failureLogged = true;
                logger.warning("Falha ao enviar ActionBar: " + e);
            }
        }
    }

    /** Spigot 1.10+: player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg)). */
    private static Sender trySpigotApi() {
        try {
            Class<?> typeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            final Object actionBar = typeClass.getField("ACTION_BAR").get(null);
            Class<?> componentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> componentArray = java.lang.reflect.Array.newInstance(componentClass, 0).getClass();
            final Method spigotMethod = Player.class.getMethod("spigot");
            final Method sendMessage = Class.forName("org.bukkit.entity.Player$Spigot")
                    .getMethod("sendMessage", typeClass, componentArray);
            final Method fromLegacy = Class.forName("net.md_5.bungee.api.chat.TextComponent")
                    .getMethod("fromLegacyText", String.class);
            return new Sender() {
                @Override
                public void send(Player player, String message) throws Exception {
                    Object components = fromLegacy.invoke(null, message);
                    sendMessage.invoke(spigotMethod.invoke(player), actionBar, components);
                }
            };
        } catch (Exception | LinkageError unsupported) {
            return null;
        }
    }

    private static Sender tryNms(Logger logger) {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            String nms = "net.minecraft.server." + version + ".";
            Class<?> componentClass = Class.forName(nms + "IChatBaseComponent");
            Class<?> serializer = findSerializer(nms);
            final Method fromJson = serializer.getMethod("a", String.class);
            final Constructor<?> packetConstructor =
                    Class.forName(nms + "PacketPlayOutChat").getConstructor(componentClass, byte.class);
            final Class<?> packetClass = Class.forName(nms + "Packet");
            final Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            final Method getHandle = craftPlayer.getMethod("getHandle");
            final Field connectionField = getHandle.getReturnType().getField("playerConnection");
            final Method sendPacket = connectionField.getType().getMethod("sendPacket", packetClass);
            return new Sender() {
                @Override
                public void send(Player player, String message) throws Exception {
                    Object component = fromJson.invoke(null, "{\"text\":\"" + escapeJson(message) + "\"}");
                    Object packet = packetConstructor.newInstance(component, (byte) 2);
                    Object connection = connectionField.get(getHandle.invoke(player));
                    sendPacket.invoke(connection, packet);
                }
            };
        } catch (Exception | LinkageError e) {
            logger.warning("ActionBar indisponivel nesta versao do servidor: " + e);
            return null;
        }
    }

    private static Class<?> findSerializer(String nms) throws ClassNotFoundException {
        try {
            return Class.forName(nms + "IChatBaseComponent$ChatSerializer"); // 1.8.3+
        } catch (ClassNotFoundException e) {
            return Class.forName(nms + "ChatSerializer"); // 1.8.0
        }
    }

    private static String escapeJson(String text) {
        StringBuilder builder = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
            }
        }
        return builder.toString();
    }
}
