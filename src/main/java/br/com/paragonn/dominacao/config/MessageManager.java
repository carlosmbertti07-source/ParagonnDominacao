package br.com.paragonn.dominacao.config;

import br.com.paragonn.dominacao.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * messages.yml. Lido explicitamente em UTF-8: no Windows o Bukkit 1.8 usa o charset
 * do sistema e quebraria os acentos.
 */
public final class MessageManager {

    private static final String FILE = "messages.yml";

    private final JavaPlugin plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();
    private String prefix = "";

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        messages = readUtf8(file);
        defaults = readDefaults();
        prefix = Text.color(raw("prefix"));
    }

    /** Mensagem colorida com placeholders, sem prefixo. */
    public String get(String path, Object... placeholders) {
        return Text.color(Text.replace(raw(path), placeholders));
    }

    public List<String> getList(String path, Object... placeholders) {
        YamlConfiguration source = messages.contains(path) ? messages : defaults;
        List<String> lines;
        if (source.isList(path)) {
            lines = source.getStringList(path);
        } else {
            String single = source.getString(path, "");
            lines = single.isEmpty() ? Collections.<String>emptyList() : Collections.singletonList(single);
        }
        List<String> result = new ArrayList<String>(lines.size());
        for (String line : lines) {
            result.add(Text.color(Text.replace(line, placeholders)));
        }
        return result;
    }

    /** Envia com prefixo. Mensagens vazias no messages.yml não são enviadas. */
    public void send(CommandSender sender, String path, Object... placeholders) {
        String message = get(path, placeholders);
        if (!message.isEmpty()) {
            sender.sendMessage(prefix + message);
        }
    }

    public void sendList(CommandSender sender, String path, Object... placeholders) {
        for (String line : getList(path, placeholders)) {
            sender.sendMessage(line);
        }
    }

    private String raw(String path) {
        String value = messages.getString(path);
        if (value == null) {
            value = defaults.getString(path, "");
        }
        return value;
    }

    private YamlConfiguration readUtf8(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao ler " + FILE + ", usando mensagens padrao.", e);
        }
        return yaml;
    }

    private YamlConfiguration readDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        InputStream stream = plugin.getResource(FILE);
        if (stream == null) {
            return yaml;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            yaml.loadFromString(builder.toString());
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Erro ao ler " + FILE + " interno.", e);
        }
        return yaml;
    }
}
