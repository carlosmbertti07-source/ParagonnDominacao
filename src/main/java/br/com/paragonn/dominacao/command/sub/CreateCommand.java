package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Cuboid;
import br.com.paragonn.dominacao.command.SubCommand;
import br.com.paragonn.dominacao.hook.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * /dominacao create &lt;arena&gt; [regiaoWorldGuard]
 * Sem região WG usa a seleção pos1/pos2. Se a arena já existir, apenas redefine a área.
 */
public final class CreateCommand extends SubCommand {

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    public CreateCommand(DominacaoPlugin plugin) {
        super(plugin, "create", "dominacao.create", true);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "/dominacao create <arena> [regiaoWorldGuard]");
            return;
        }
        String name = lower(args[0]);
        if (!VALID_NAME.matcher(name).matches()) {
            send(sender, "invalid-arena-name", "arena", args[0]);
            return;
        }
        Player player = (Player) sender;
        Cuboid region = args.length >= 2 ? fromWorldGuard(player, args[1]) : fromSelection(player);
        if (region == null) {
            return;
        }
        plugin.arenas().create(name, region);
        plugin.selections().clear(player.getUniqueId());
        send(sender, "arena-created", "arena", name, "region", region.toString(), "blocks", region.volume());
    }

    private Cuboid fromSelection(Player player) {
        Location[] points = plugin.selections().get(player.getUniqueId());
        if (points == null) {
            send(player, "need-selection");
            return null;
        }
        if (!points[0].getWorld().equals(points[1].getWorld())) {
            send(player, "different-worlds");
            return null;
        }
        return Cuboid.of(points[0], points[1]);
    }

    private Cuboid fromWorldGuard(Player player, String regionId) {
        if (!WorldGuardHook.isAvailable()) {
            send(player, "worldguard-missing");
            return null;
        }
        try {
            Cuboid region = WorldGuardHook.regionBounds(player.getWorld(), regionId);
            if (region == null) {
                send(player, "worldguard-region-not-found", "region", regionId);
            }
            return region;
        } catch (IllegalStateException e) {
            send(player, "worldguard-missing");
            return null;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? arenaNames() : Collections.<String>emptyList();
    }
}
