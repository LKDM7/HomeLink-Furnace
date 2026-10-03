package fr.lkdm.homelink.furnace.furnace;

import java.util.concurrent.atomic.AtomicLong;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Reload invalidation without searching chunks or polling every inventory slot. */
public final class FurnaceRecipeReloads {
    private static final AtomicLong GENERATION = new AtomicLong();
    private FurnaceRecipeReloads() { }
    public static long generation() { return GENERATION.get(); }
    public static void onSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) GENERATION.incrementAndGet();
    }
}
