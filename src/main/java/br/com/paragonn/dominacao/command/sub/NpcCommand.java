package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.arena.Arena;
import br.com.paragonn.dominacao.command.SubCommand;
import br.com.paragonn.dominacao.npc.NpcManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** /dominacao npc &lt;top|campeao&gt; [arena] — cria (ou move) o NPC para onde o admin está. */
public final class NpcCommand extends SubCommand {

    public NpcCommand(DominacaoPlugin plugin) {
        super(plugin, "npc", "dominacao.npc", true);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "/dominacao npc <top|campeao> [arena]");
            return;
        }
        NpcManager.Type type;
        String typeArg = lower(args[0]);
        if ("top".equals(typeArg)) {
            type = NpcManager.Type.TOP;
        } else if ("campeao".equals(typeArg) || "champion".equals(typeArg)) {
            type = NpcManager.Type.CHAMPION;
        } else {
            usage(sender, "/dominacao npc <top|campeao> [arena]");
            return;
        }
        if (!plugin.npcs().isAvailable()) {
            send(sender, "citizens-missing");
            return;
        }
        Arena arena = arena(sender, arg(args, 1));
        if (arena == null) {
            return;
        }
        plugin.npcs().place(type, arena, ((Player) sender).getLocation());
        send(sender, "npc-placed", "type", type.id, "arena", arena.name());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("top", "campeao");
        }
        return args.length == 2 ? arenaNames() : Collections.<String>emptyList();
    }
}
