package br.com.paragonn.dominacao.command.sub;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.command.SubCommand;
import org.bukkit.command.CommandSender;

/** /dominacao reload — relê config.yml e messages.yml sem perder o progresso em andamento. */
public final class ReloadCommand extends SubCommand {

    public ReloadCommand(DominacaoPlugin plugin) {
        super(plugin, "reload", "dominacao.reload", false);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.reloadAll();
        send(sender, "reloaded");
    }
}
