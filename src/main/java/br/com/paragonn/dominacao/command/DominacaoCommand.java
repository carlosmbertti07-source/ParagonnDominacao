package br.com.paragonn.dominacao.command;

import br.com.paragonn.dominacao.DominacaoPlugin;
import br.com.paragonn.dominacao.command.sub.CreateCommand;
import br.com.paragonn.dominacao.command.sub.DeleteCommand;
import br.com.paragonn.dominacao.command.sub.HelpCommand;
import br.com.paragonn.dominacao.command.sub.HologramCommand;
import br.com.paragonn.dominacao.command.sub.NpcCommand;
import br.com.paragonn.dominacao.command.sub.PointsCommand;
import br.com.paragonn.dominacao.command.sub.PosCommand;
import br.com.paragonn.dominacao.command.sub.ReloadCommand;
import br.com.paragonn.dominacao.command.sub.ResetCommand;
import br.com.paragonn.dominacao.command.sub.SetPointsCommand;
import br.com.paragonn.dominacao.command.sub.SetTimeCommand;
import br.com.paragonn.dominacao.command.sub.StatsCommand;
import br.com.paragonn.dominacao.command.sub.StatusCommand;
import br.com.paragonn.dominacao.command.sub.TopCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dispatcher de /dominacao &lt;subcomando&gt;. */
public final class DominacaoCommand implements CommandExecutor, TabCompleter {

    private final DominacaoPlugin plugin;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<String, SubCommand>();
    private final HelpCommand help;

    public DominacaoCommand(DominacaoPlugin plugin) {
        this.plugin = plugin;
        this.help = new HelpCommand(plugin);
        register(help);
        register(new PosCommand(plugin, 1));
        register(new PosCommand(plugin, 2));
        register(new CreateCommand(plugin));
        register(new DeleteCommand(plugin));
        register(new ReloadCommand(plugin));
        register(new SetTimeCommand(plugin));
        register(new SetPointsCommand(plugin));
        register(new PointsCommand(plugin));
        register(new ResetCommand(plugin));
        register(new NpcCommand(plugin));
        register(new HologramCommand(plugin));
        register(new TopCommand(plugin));
        register(new StatsCommand(plugin));
        register(new StatusCommand(plugin));
    }

    private void register(SubCommand command) {
        subCommands.put(command.name(), command);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SubCommand sub = args.length == 0 ? help : subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            sub = help;
        }
        if (!sender.hasPermission(sub.permission())) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (sub.playerOnly() && !(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        String[] rest = args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
        sub.execute(sender, rest);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<String>();
            for (SubCommand sub : subCommands.values()) {
                if (sender.hasPermission(sub.permission())) {
                    names.add(sub.name());
                }
            }
            return StringUtil.copyPartialMatches(args[0], names, new ArrayList<String>());
        }
        SubCommand sub = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || !sender.hasPermission(sub.permission())) {
            return Collections.emptyList();
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return StringUtil.copyPartialMatches(rest[rest.length - 1], sub.tabComplete(sender, rest), new ArrayList<String>());
    }
}
