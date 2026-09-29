package br.com.paragonn.dominacao.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Utilitários de texto: cores, placeholders e formatação numérica. */
public final class Text {

    private static final Locale PT_BR = new Locale("pt", "BR");

    private Text() {
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> color(List<String> lines) {
        List<String> result = new ArrayList<String>(lines.size());
        for (String line : lines) {
            result.add(color(line));
        }
        return result;
    }

    /** Substitui pares chave/valor: replace(texto, "clan", "ABC", "arena", "Mina") -> %clan%, %arena%. */
    public static String replace(String text, Object... pairs) {
        String result = text;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("%" + pairs[i] + "%", String.valueOf(pairs[i + 1]));
        }
        return result;
    }

    /** 1250 -> "1.250". */
    public static String number(long value) {
        return String.format(PT_BR, "%,d", value);
    }

    /** Milissegundos -> "mm:ss" (minutos podem passar de 59). */
    public static String time(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /** Segundos -> "1h 20m" / "35m 10s". */
    public static String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + (seconds % 60) + "s";
    }

    public static String decimal(double value) {
        return String.format(PT_BR, "%.2f", value);
    }
}
