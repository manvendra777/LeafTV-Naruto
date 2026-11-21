package com.leaftv.launcher;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Title-level wallpaper bundles, independent of Android for unit testing. */
public final class WallpaperBundles {
    private WallpaperBundles() {}
    public static String titleForAsset(String assetId) {
        if (assetId == null || !assetId.matches("[0-9]{2}_[0-9]{2}")) return "";
        return assetId.substring(0, 2);
    }
    /** Select all images of every selected title. Never just a single selected photo. */
    public static Set<String> assetsForBundles(Set<String> selectedTitles, Collection<String> available) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (selectedTitles == null || available == null) return out;
        for (String image : available) if (selectedTitles.contains(titleForAsset(image))) out.add(image);
        return out;
    }
    /** Preserve existing wallpaper picks while switching from image tiles to title tiles. */
    public static Set<String> migrate(Set<String> previousPictures, Set<String> previousTitles,
                                      Collection<String> available, int titleCount) {
        HashSet<String> present = new HashSet<>();
        for (String id : available) {
            String prefix = titleForAsset(id);
            if (!prefix.isEmpty()) present.add(prefix);
        }
        HashSet<String> result = new HashSet<>();
        if (previousPictures != null) {
            for (String id : previousPictures) {
                String prefix = titleForAsset(id);
                if (present.contains(prefix)) result.add(prefix);
            }
        } else if (previousTitles != null) {
            for (String id : previousTitles) {
                try {
                    int title = Integer.parseInt(id);
                    if (title >= 0 && title < titleCount) {
                        String formatted = String.format(java.util.Locale.US, "%02d", title);
                        if (present.contains(formatted)) result.add(formatted);
                    }
                } catch (NumberFormatException ignored) { }
            }
        } else {
            result.addAll(present); // Fresh install: all available title bundles enabled.
        }
        if (result.isEmpty() && !present.isEmpty()) {
            for (String id : available) {
                String prefix = titleForAsset(id);
                if (!prefix.isEmpty()) { result.add(prefix); break; }
            }
        }
        return result;
    }
}
