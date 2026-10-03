package fr.lkdm.homelink.furnace.homelink;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Loaded masters register devices through HomeCore's provider; no world or chunk scans. */
public final class FurnaceHomeCore {
    private FurnaceHomeCore() { }

    public static void registerProviders() {
        DashboardAPI.registerDeviceProvider(FurnaceRegistries.MASTER.get(), furnace -> new FurnaceDevice(furnace, event -> {
            if (furnace.getLevel() instanceof ServerLevel level) DashboardAPI.events(level.getServer()).publish(event);
        }));
    }

    public static Optional<FurnaceDevice> register(ServerLevel level, IndustrialFurnaceBlockEntity furnace) {
        var registry = DashboardAPI.devices(level.getServer());
        var existing = registry.get(furnace.deviceId());
        if (existing.isPresent() && existing.get() instanceof FurnaceDevice device && device.matches(furnace))
            return Optional.of(device);
        if (existing.isPresent()) furnace.resetIdentityAfterCollision();
        var discovered = DashboardAPI.providers().discover(furnace);
        if (discovered.isPresent() && discovered.get() instanceof FurnaceDevice device) {
            registry.register(device);
            return Optional.of(device);
        }
        return Optional.empty();
    }

    public static void unregister(ServerLevel level, FurnaceDevice device) {
        var registry = DashboardAPI.devices(level.getServer());
        registry.get(device.id()).filter(current -> current == device).ifPresent(current -> registry.unregister(current.id()));
    }

    public static NetworkMember.BindResult bind(ServerPlayer player, IndustrialFurnaceBlockEntity furnace, Optional<UUID> network) {
        return DashboardAPI.devices(player.server).get(furnace.deviceId())
                .or(() -> DashboardAPI.providers().discover(furnace))
                .map(device -> DashboardAPI.bindDevice(player, device, network)).orElse(NetworkMember.BindResult.DENIED);
    }

    public static void forgetOnRemoval(ServerLevel level, IndustrialFurnaceBlockEntity furnace) {
        var networks = DashboardAPI.networks(level.getServer());
        furnace.homeNetworkId().filter(id -> networks.getNetwork(id).isPresent())
                .ifPresent(id -> networks.removeDevice(id, furnace.deviceId()));
    }
}
