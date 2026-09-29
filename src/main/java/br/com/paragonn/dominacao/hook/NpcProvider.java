package br.com.paragonn.dominacao.hook;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Abstração de NPCs. Só usa tipos do Bukkit, então o plugin funciona
 * (sem NPCs) mesmo que o Citizens não esteja instalado.
 */
public interface NpcProvider {

    /** Chamado quando um jogador clica em um NPC. */
    interface ClickHandler {
        void onClick(Player player, int npcId);
    }

    boolean available();

    /** Registra o tratamento de cliques (direito e esquerdo) nos NPCs. */
    void registerClickHandler(Plugin plugin, ClickHandler handler);

    /** Cria um NPC jogador e retorna o id. */
    int create(String name, Location location);

    boolean exists(int id);

    void moveTo(int id, Location location);

    /** Localização atual/armazenada ou null. */
    Location location(int id);

    /**
     * Aplica a skin do jogador (por nome) mantendo o NPC com o nome fixo npcName.
     * O NPC nunca deve ter o nome de um jogador real: times do scoreboard (tags de nametag)
     * são associados por nome e acabariam aplicados também ao NPC.
     */
    void setSkin(int id, String skinName, String npcName);

    void remove(int id);
}
