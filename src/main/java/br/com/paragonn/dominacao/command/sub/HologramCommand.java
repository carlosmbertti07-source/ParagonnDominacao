package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /dominacao holograma set [arena]    — coloca o holograma de top de clans onde o admin está.
 * /dominacao holograma remove [arena] — remove o holograma.
 */
public final class HologramCommand extends SubCommand {

    private static final String USAGE = "/dominacao holograma <set|remove> [arena]";

    public HologramCommand(DominacaoPlugin plugin) {
        super(plugin, "holograma", "dominacao.npc", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String action = lower(arg(args, 0));
        if (!"set".equals(action) && !"remove".equals(action)) {
            usage(sender, USAGE);
            return;
        }
        Arena arena = arena(sender, arg(args, 1));
        if (arena == null) {
            return;
        }
        if ("remove".equals(action)) {
            plugin.npcs().removeBoard(arena);
            send(sender, "hologram-removed", "arena", arena.name());
            return;
        }
        if (!(sender instanceof Player)) {
            send(sender, "player-only");
            return;
        }
        plugin.npcs().placeBoard(arena, ((Player) sender).getLocation());
        send(sender, "hologram-placed", "arena", arena.name());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "remove");
        }
        return args.length == 2 ? arenaNames() : Collections.<String>emptyList();
    }
}
