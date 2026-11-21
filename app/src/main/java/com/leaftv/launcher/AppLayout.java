package com.leaftv.launcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/** Pure order transformations: no Android dependencies; tested with javac/java. */
public final class AppLayout {
    private AppLayout() {}
    public static ArrayList<String> seed(Collection<String> packages) {
        return unique(packages);
    }
    private static ArrayList<String> unique(Collection<String> packages) {
        ArrayList<String> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (String p : packages) if (p != null && !p.isEmpty() && seen.add(p)) result.add(p);
        return result;
    }
    public static ArrayList<String> filter(List<String> savedOrder, Collection<String> installed) {
        HashSet<String> allowed = new HashSet<>(installed);
        ArrayList<String> out = new ArrayList<>();
        for (String p : unique(savedOrder)) if (allowed.contains(p)) out.add(p);
        return out;
    }
    public static ArrayList<String> merge(List<String> customOrder, Collection<String> alphabetic) {
        ArrayList<String> out = filter(customOrder, alphabetic);
        for (String p : unique(alphabetic)) if (!out.contains(p)) out.add(p);
        return out;
    }
    public static ArrayList<String> toggle(List<String> previous, String pkg) {
        ArrayList<String> out = unique(previous);
        if (!out.remove(pkg)) out.add(pkg);
        return out;
    }
    public static ArrayList<String> move(List<String> previous, String pkg, int delta) {
        ArrayList<String> out = unique(previous);
        int from = out.indexOf(pkg);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= out.size()) return out;
        out.remove(from);
        out.add(to, pkg);
        return out;
    }
}
