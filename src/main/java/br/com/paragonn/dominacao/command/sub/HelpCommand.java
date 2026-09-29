package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.command.SubCommand;
import org.bukkit.command.CommandSender;

/** /dominacao [help] */
public final class HelpCommand extends SubCommand {

    public HelpCommand(DominacaoPlugin plugin) {
        super(plugin, "help", "dominacao.use", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.messages().sendList(sender, "help.player");
        if (sender.hasPermission("dominacao.create") || sender.hasPermission("dominacao.admin")
                || sender.hasPermission("dominacao.points") || sender.hasPermission("dominacao.npc")) {
            plugin.messages().sendList(sender, "help.admin");
        }
    }
}
