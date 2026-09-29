package br.com.paragonn.dominacao.command;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Base dos subcomandos de /dominacao. */
public abstract class SubCommand {

    protected final DominacaoPlugin plugin;
    private final String name;
    private final String permission;
    private final boolean playerOnly;

    protected SubCommand(DominacaoPlugin plugin, String name, String permission, boolean playerOnly) {
        this.plugin = plugin;
        this.name = name;
        this.permission = permission;
        this.playerOnly = playerOnly;
    }

    public String name() {
        return name;
    }

    public String permission() {
        return permission;
    }

    public boolean playerOnly() {
        return playerOnly;
    }

    /** args não inclui o nome do subcomando. */
    public abstract void execute(CommandSender sender, String[] args);

    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------ helpers

    protected void send(CommandSender sender, String path, Object... placeholders) {
        plugin.messages().send(sender, path, placeholders);
    }

    protected void usage(CommandSender sender, String usage) {
        send(sender, "usage", "usage", usage);
    }

    /** Arena pelo nome ou a padrão (quando nome == null). Envia erro e retorna null se não existir. */
    protected Arena arena(CommandSender sender, String nameOrNull) {
        Arena arena = plugin.arenas().resolve(nameOrNull);
        if (arena == null) {
            send(sender, "unknown-arena", "arena", nameOrNull == null ? "?" : nameOrNull);
        }
        return arena;
    }

    protected String arg(String[] args, int index) {
        return args.length > index ? args[index] : null;
    }

    /** Número inteiro >= min, ou null (com mensagem de erro). */
    protected Long number(CommandSender sender, String value, long min) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed >= min) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // cai na mensagem abaixo
        }
        send(sender, "invalid-number", "value", value);
        return null;
    }

    protected List<String> arenaNames() {
        return new ArrayList<String>(plugin.arenas().names());
    }

    protected List<String> onlineNames() {
        List<String> names = new ArrayList<String>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    protected static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
