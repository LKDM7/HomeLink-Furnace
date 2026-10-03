package fr.lkdm.homelink.furnace.menu;

import fr.lkdm.homelink.furnace.block.FurnaceTier;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.furnace.FurnaceStatus;
import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import fr.lkdm.homelink.furnace.network.FurnacePayloads;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/** No fuel slot. Tabs disable slots on both ends, including malicious stale slot requests. */
public final class FurnaceMenu extends AbstractContainerMenu {
    public static final int POWER = 0, XP = 1, REDSTONE = 2, TAB_PRODUCTION = 10, TAB_ENERGY = 11, TAB_SETTINGS = 12;
    private final BlockPos pos;
    private final IndustrialFurnaceBlockEntity furnace;
    private final FurnaceTier tier;
    private final ContainerData data;
    private int tab;
    private final Player viewer;
    private int[] previousState;
    private long lastSync = Long.MIN_VALUE;
    public FurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), FurnaceTier.values()[Math.max(0, Math.min(2, buffer.readVarInt()))], null);
    }
    public FurnaceMenu(int id, Inventory inventory, IndustrialFurnaceBlockEntity furnace) {
        this(id, inventory, furnace.getBlockPos(), furnace.tier(), furnace);
    }
    private FurnaceMenu(int id, Inventory inventory, BlockPos pos, FurnaceTier tier, IndustrialFurnaceBlockEntity furnace) {
        super(FurnaceRegistries.MENU.get(), id);
        this.pos = pos; this.tier = tier; this.furnace = furnace;
        this.viewer = inventory.player;
        this.data = furnace == null ? new SimpleContainerData(FurnaceMenuData.COUNT) : new FurnaceMenuData(furnace);
        ItemStackHandler input = furnace == null ? new ItemStackHandler(tier.inputSlots()) : furnace.input();
        ItemStackHandler output = furnace == null ? new ItemStackHandler(tier.outputSlots()) : furnace.output();
        for (int i = 0; i < input.getSlots(); i++) addSlot(new MachineSlot(input, i, false));
        for (int i = 0; i < output.getSlots(); i++) addSlot(new MachineSlot(output, i, true));
        for (int i = 0; i < 36; i++) addSlot(new Slot(inventory, i < 27 ? i + 9 : i - 27, 0, 0) {
            @Override public boolean isActive() { return tab == 0; }
            @Override public boolean mayPickup(Player player) { return tab == 0 && authorized(player); }
            @Override public boolean mayPlace(ItemStack stack) { return tab == 0; }
        });
        layoutSlots(520, 340);
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (furnace == null || !(viewer instanceof ServerPlayer player) || furnace.getLevel() == null) return;
        long tick = furnace.getLevel().getGameTime();
        if (previousState != null && tick - lastSync < 5) return;
        lastSync = tick;
        int[] state = new int[FurnaceMenuData.COUNT];
        for (int i = 0; i < state.length; i++) state[i] = data.get(i);
        if (!Arrays.equals(previousState, state)) {
            previousState = state;
            PacketDistributor.sendToPlayer(player, new FurnacePayloads.MenuState(containerId, pos, state));
        }
    }
    public void updateState(int[] state) {
        if (furnace != null || state.length != FurnaceMenuData.COUNT) return;
        for (int i = 0; i < state.length; i++) data.set(i, state[i]);
    }
    private boolean authorized(Player player) { return furnace == null || furnace.mayControl(player); }
    private final class MachineSlot extends SlotItemHandler {
        private final boolean output;
        MachineSlot(ItemStackHandler handler, int index, boolean output) { super(handler, index, 0, 0); this.output = output; }
        @Override public boolean isActive() { return tab == 0; }
        @Override public boolean mayPlace(ItemStack stack) { return tab == 0 && !output && super.mayPlace(stack); }
        @Override public boolean mayPickup(Player player) { return tab == 0 && authorized(player) && super.mayPickup(player); }
    }
    public void layoutSlots(int width, int height) {
        int count = tier.inputSlots();
        int machineY = 143;
        for (int i = 0; i < count; i++) { slots.get(i).x = 14 + (i % 9) * 18; slots.get(i).y = machineY + (i / 9) * 18; }
        for (int i = 0; i < count; i++) { slots.get(count + i).x = width - 176 + (i % 9) * 18; slots.get(count + i).y = machineY + (i / 9) * 18; }
        int playerX = (width - 162) / 2, playerY = height - 104;
        for (int i = 0; i < 36; i++) { Slot slot = slots.get(count * 2 + i); slot.x = playerX + (i % 9) * 18; slot.y = playerY + (i < 27 ? (i / 9) * 18 : 58); }
    }
    @Override public boolean stillValid(Player player) {
        return furnace == null || player.level() == furnace.getLevel() && !furnace.isRemoved()
                && player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) == furnace
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64 && furnace.mayView(player);
    }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (!stillValid(player)) return false;
        if (action >= TAB_PRODUCTION && action <= TAB_SETTINGS) { tab = action - TAB_PRODUCTION; return true; }
        if (furnace == null || !furnace.mayControl(player)) return false;
        switch (action) {
            case POWER -> furnace.setEnabled(!furnace.enabled());
            case XP -> { if (player instanceof ServerPlayer serverPlayer) furnace.collectXp(serverPlayer); }
            case REDSTONE -> { if (!furnace.mayConfigure(player)) return false; furnace.setRedstoneMode(furnace.redstoneMode().next()); }
            default -> { return false; }
        }
        return true;
    }
    public void setClientTab(int tab) { this.tab = Math.max(0, Math.min(2, tab)); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (tab != 0 || !stillValid(player) || !authorized(player)) return;
        super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (tab != 0 || index < 0 || index >= slots.size() || !stillValid(player) || !authorized(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        int machine = tier.inputSlots() + tier.outputSlots();
        if (index < machine) { if (!moveItemStackTo(stack, machine, slots.size(), true)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, 0, tier.inputSlots(), false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
    public BlockPos position() { return pos; }
    public FurnaceTier tier() { return tier; }
    public int value(int index) { return data.get(index); }
    public long wide(int low) { return (value(low) & 0xFFFFFFFFL) | ((value(low + 1) & 0xFFFFFFFFL) << 32); }
    public FurnaceStatus status() { return FurnaceStatus.values()[Math.max(0, Math.min(FurnaceStatus.values().length - 1, value(1)))]; }
}
