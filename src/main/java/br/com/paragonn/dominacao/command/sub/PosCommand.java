package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.command.SubCommand;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /dominacao pos1 | pos2 — marca o bloco onde o jogador está. */
public final class PosCommand extends SubCommand {

    private final int index;

    public PosCommand(DominacaoPlugin plugin, int position) {
        super(plugin, "pos" + position, "dominacao.create", true);
        this.index = position - 1;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        Location location = player.getLocation();
        plugin.selections().set(player.getUniqueId(), index, location);
        send(sender, "pos-set", "pos", index + 1,
                "x", location.getBlockX(), "y", location.getBlockY(), "z", location.getBlockZ());
    }
}
