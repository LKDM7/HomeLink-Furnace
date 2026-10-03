package fr.lkdm.homelink.furnace.network;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.StandardActions;
import fr.lkdm.homecore.api.device.Renamable;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.furnace.HomeLinkFurnace;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.menu.FurnaceMenu;
import fr.lkdm.homelink.furnace.menu.FurnaceMenuData;
import io.netty.buffer.ByteBuf;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Authenticated requests are tied to the currently open menu, dimension and loaded master. */
public final class FurnacePayloads {
    private FurnacePayloads() { }
    public record Rename(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(HomeLinkFurnace.id("rename"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rename> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Rename::pos, ByteBufCodecs.stringUtf8(Renamable.MAX_LENGTH * 4), Rename::name, Rename::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Bind(BlockPos pos, Optional<UUID> network) implements CustomPacketPayload {
        public static final Type<Bind> TYPE = new Type<>(HomeLinkFurnace.id("bind"));
        public static final StreamCodec<ByteBuf, Bind> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Bind::pos,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Bind::network, Bind::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Choice(UUID id, String name) {
        static final StreamCodec<ByteBuf, Choice> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Choice::id,
                ByteBufCodecs.stringUtf8(128), Choice::name, Choice::new);
    }
    public record Choices(BlockPos pos, List<Choice> networks) implements CustomPacketPayload {
        public static final Type<Choices> TYPE = new Type<>(HomeLinkFurnace.id("networks"));
        public static final StreamCodec<ByteBuf, Choices> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Choices::pos,
                Choice.CODEC.apply(ByteBufCodecs.list(32)), Choices::networks, Choices::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record MenuState(int menuId, BlockPos pos, int[] values) implements CustomPacketPayload {
        public static final Type<MenuState> TYPE = new Type<>(HomeLinkFurnace.id("menu_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MenuState> CODEC = new StreamCodec<>() {
            @Override public MenuState decode(RegistryFriendlyByteBuf buffer) {
                int id = buffer.readVarInt(); BlockPos pos = buffer.readBlockPos();
                int[] values = new int[FurnaceMenuData.COUNT];
                for (int i = 0; i < values.length; i++) values[i] = buffer.readVarInt();
                return new MenuState(id, pos, values);
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, MenuState state) {
                buffer.writeVarInt(state.menuId()); buffer.writeBlockPos(state.pos());
                if (state.values().length != FurnaceMenuData.COUNT) throw new IllegalArgumentException("Invalid furnace sync size");
                for (int value : state.values()) buffer.writeVarInt(value);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Rename.TYPE, Rename.CODEC, FurnacePayloads::rename);
        registrar.playToServer(Bind.TYPE, Bind.CODEC, FurnacePayloads::bind);
        registrar.playToClient(MenuState.TYPE, MenuState.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof FurnaceMenu menu && menu.containerId == payload.menuId()
                    && menu.position().equals(payload.pos())) menu.updateState(payload.values());
        });
        // Handler uses common data only; client classes never link on a dedicated server.
        registrar.playToClient(Choices.TYPE, Choices.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof FurnaceMenu menu && menu.position().equals(payload.pos()))
                ClientChoices.set(payload.pos(), payload.networks());
        });
    }
    public static void sendNetworkChoices(ServerPlayer player, IndustrialFurnaceBlockEntity furnace) {
        var choices = DashboardAPI.networks(player.server).getNetworksForPlayer(player.getUUID()).stream()
                .filter(network -> DashboardAPI.hasPermission(player, network.id(), Permission.MANAGE_NETWORK))
                .limit(32).map(network -> new Choice(network.id(), network.name())).toList();
        PacketDistributor.sendToPlayer(player, new Choices(furnace.getBlockPos(), choices));
    }
    private static Optional<IndustrialFurnaceBlockEntity> open(ServerPlayer player, BlockPos pos) {
        if (!(player.containerMenu instanceof FurnaceMenu menu) || !menu.position().equals(pos)
                || !menu.stillValid(player) || !player.serverLevel().hasChunkAt(pos)) return Optional.empty();
        return player.serverLevel().getBlockEntity(pos) instanceof IndustrialFurnaceBlockEntity furnace
                ? Optional.of(furnace) : Optional.empty();
    }
    private static void rename(Rename payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        open(player, payload.pos()).filter(furnace -> furnace.mayConfigure(player)).ifPresent(furnace -> {
            if (StandardActions.validName(payload.name()) && furnace.device() != null) furnace.device().rename(payload.name().strip());
        });
    }
    private static void bind(Bind payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        open(player, payload.pos()).ifPresent(furnace -> {
            if (furnace.device() == null) return;
            var result = DashboardAPI.bindDevice(player, furnace.device(), payload.network());
            player.displayClientMessage(Component.translatable("message.homelink_furnace.binding." + result.name().toLowerCase(Locale.ROOT)), true);
        });
    }
    public static final class ClientChoices {
        private static BlockPos pos = BlockPos.ZERO;
        private static List<Choice> choices = List.of();
        private ClientChoices() { }
        public static void set(BlockPos position, List<Choice> values) { pos = position; choices = List.copyOf(values); }
        public static List<Choice> get(BlockPos position) { return pos.equals(position) ? choices : List.of(); }
        public static void clear() { choices = List.of(); pos = BlockPos.ZERO; }
    }
}
