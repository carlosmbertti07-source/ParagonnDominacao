package br.com.paragonn.dominacao.hook;

import org.bukkit.Location;

/** Usado quando o Citizens não está instalado. */
public final class NoopNpcProvider implements NpcProvider {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public void registerClickHandler(org.bukkit.plugin.Plugin plugin, ClickHandler handler) {
    }

    @Override
    public int create(String name, Location location) {
        throw new UnsupportedOperationException("Citizens nao instalado");
    }

    @Override
    public boolean exists(int id) {
        return false;
    }

    @Override
    public void moveTo(int id, Location location) {
    }

    @Override
    public Location location(int id) {
        return null;
    }

    @Override
    public void setSkin(int id, String skinName, String npcName) {
    }

    @Override
    public void remove(int id) {
    }
}
