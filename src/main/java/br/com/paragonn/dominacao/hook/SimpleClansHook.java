package br.com.paragonn.dominacao.hook;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;

import java.util.UUID;

/**
 * Único ponto de contato com a API do SimpleClans. Usa apenas métodos estáveis da 2.x
 * (presentes tanto no build "mc1.8" quanto no moderno).
 */
public final class SimpleClansHook {

    private final SimpleClans simpleClans;

    public SimpleClansHook(SimpleClans simpleClans) {
        this.simpleClans = simpleClans;
    }

    /** Clan do jogador ou null. */
    public Clan clanOf(UUID uuid) {
        ClanPlayer clanPlayer = simpleClans.getClanManager().getClanPlayer(uuid);
        return clanPlayer == null ? null : clanPlayer.getClan();
    }

    /** Clan pela tag (sem cores) ou null. */
    public Clan clanByTag(String tag) {
        return simpleClans.getClanManager().getClan(tag);
    }
}
