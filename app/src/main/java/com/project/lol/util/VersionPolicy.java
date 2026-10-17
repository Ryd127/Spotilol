package com.project.lol.util;

/** Compare numeric upstream versions without treating a local build suffix as zero. */
public final class VersionPolicy {
    private VersionPolicy() {}

    public static boolean isNewer(String latest, String current) {
        long[] next = numericCore(latest), installed = numericCore(current);
        if (next == null || installed == null) return false;
        for (int i = 0; i < 4; i++) {
            if (next[i] != installed[i]) return next[i] > installed[i];
        }
        return false;
    }

    private static long[] numericCore(String version) {
        if (version == null) return null;
        String core = version.trim().replaceFirst("^[vV]", "").split("[-+]", 2)[0];
        if (!core.matches("[0-9]+(?:\\.[0-9]+){0,3}")) return null;
        String[] parts = core.split("\\.");
        long[] result = new long[4];
        try {
            for (int i = 0; i < parts.length; i++) result[i] = Long.parseLong(parts[i]);
            return result;
        } catch (NumberFormatException ignored) { return null; }
    }
}
