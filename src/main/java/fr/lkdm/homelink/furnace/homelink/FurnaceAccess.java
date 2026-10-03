package fr.lkdm.homelink.furnace.homelink;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import net.minecraft.server.level.ServerPlayer;

/** Uses the physical owner and HomeCore's network permissions; no Furnace permission database. */
public final class FurnaceAccess {
    private FurnaceAccess() { }

    public static boolean allowed(ServerPlayer player, IndustrialFurnaceBlockEntity furnace, Permission permission) {
        if (player.hasPermissions(2) || furnace.owner().filter(player.getUUID()::equals).isPresent()) return true;
        return furnace.homeNetworkId().filter(id -> DashboardAPI.hasPermission(player, id, permission)).isPresent();
    }

    public static boolean canView(ServerPlayer player, IndustrialFurnaceBlockEntity furnace) {
        return allowed(player, furnace, Permission.VIEW);
    }

    public static boolean canControl(ServerPlayer player, IndustrialFurnaceBlockEntity furnace) {
        return allowed(player, furnace, Permission.CONTROL);
    }

    public static boolean canConfigure(ServerPlayer player, IndustrialFurnaceBlockEntity furnace) {
        return allowed(player, furnace, Permission.CONFIGURE);
    }
}
