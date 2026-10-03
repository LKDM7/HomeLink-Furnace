package fr.lkdm.homelink.furnace.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homelink.furnace.block.*;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.client.FurnaceScreen;
import fr.lkdm.homelink.furnace.menu.FurnaceMenu;
import fr.lkdm.homelink.furnace.furnace.FurnaceHeat;
import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/** Real integrated-client smoke, isolated in the development verification mod. */
@EventBusSubscriber(modid = FurnaceValidation.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class FurnaceSmoke {
    private static final Logger LOG = LogUtils.getLogger();
    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, waited;
    private static long deadline;
    private static boolean pending;
    private static volatile boolean done;
    private static volatile Throwable failure;
    @FunctionalInterface private interface Step { boolean run(Minecraft client); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("furnace.smoke") || index == Integer.MAX_VALUE) return;
        var client = Minecraft.getInstance();
        try {
            if (index == -1) {
                if (client.screen instanceof AccessibilityOnboardingScreen screen) { screen.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                scenes(); index = 0; deadline = System.nanoTime() + 600_000_000_000L;
                GameRules rules = new GameRules(); rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null); rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                client.createWorldOpenFlows().createFreshLevel("furnace-validation-" + System.currentTimeMillis(),
                        new LevelSettings("Furnace Validation", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(731L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            check(System.nanoTime() < deadline, "Timeout at scene " + index);
            if (failure != null) throw new IllegalStateException("Server scene failed", failure);
            if (client.player == null || client.getSingleplayerServer() == null || client.level == null) return;
            if (index >= STEPS.size()) { LOG.info("FURNACE_SMOKE_OK steps={}", STEPS.size()); index = Integer.MAX_VALUE; client.stop(); return; }
            if (STEPS.get(index).run(client)) { index++; waited = 0; }
        } catch (Throwable error) { LOG.error("FURNACE_SMOKE_FAILED step={}", index, error); index = Integer.MAX_VALUE; client.stop(); }
    }
    private static void check(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    private static Step server(Consumer<ServerPlayer> action) {
        return client -> {
            if (!pending) {
                pending = true; done = false; UUID uuid = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> { try { action.accept(client.getSingleplayerServer().getPlayerList().getPlayer(uuid)); } catch(Throwable error) { failure = error; } done = true; });
                return false;
            }
            if (!done) return false; pending = false; return true;
        };
    }
    private static Step client(Consumer<Minecraft> action) { return client -> { action.accept(client); return true; }; }
    private static Step waitTicks(int count) { return client -> ++waited >= count; }
    private static Step viewers() {
        long[] start={0};
        return client -> {
            if(start[0]==0)start[0]=System.nanoTime();
            for(String viewer:new String[]{"jei","roughlyenoughitems"}) {
                if(!net.neoforged.fml.ModList.get().isLoaded(viewer))continue;
                String helper=viewer.equals("jei")?"JeiSmokeChecks":"ReiSmokeChecks";
                try {
                    if(!(Boolean)Class.forName("fr.lkdm.homelink.furnace.verification."+helper).getMethod("ready").invoke(null)) {
                        check(System.nanoTime()-start[0]<60_000_000_000L,"Optional viewer did not register furnaces: "+viewer);return false;
                    }
                }catch(ReflectiveOperationException error){throw new IllegalStateException("Viewer verification failed: "+viewer,error);}
                LOG.info("FURNACE_VIEWER_OK viewer={} catalysts=3 information=3",viewer);
            }
            return true;
        };
    }
    private static Step hotServer() {
        boolean[] ready={false};
        Step poll=server(player->{var furnace=(IndustrialFurnaceBlockEntity)player.serverLevel().getBlockEntity(pos(FurnaceTier.III));ready[0]=furnace.heat().state()==FurnaceHeat.State.PROCESSING;});
        return client -> poll.run(client)&&ready[0];
    }
    private static Step screenshot(String name) { return client -> {
        if (client.getOverlay() != null) return false;
        if (client.screen instanceof FurnaceScreen screen) {
            for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
                check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX()+widget.getWidth() <= screen.width
                                && widget.getY()+widget.getHeight() <= screen.height, "Control outside logical viewport: " + widget.getMessage().getString());
            check(screen.children().stream().anyMatch(child -> child instanceof AbstractWidget widget && widget.active), "No active controls");
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0, 0);
            check(screen.getFocused() != null, "Keyboard tab focus absent");
            var menu=(FurnaceMenu)client.player.containerMenu;
            for(var slot:menu.slots)if(slot.isActive())for(var child:screen.children())if(child instanceof AbstractWidget widget&&widget.visible) {
                double size=16*screen.uiScale(),sx=screen.slotMouseX(slot.index)-size/2,sy=screen.slotMouseY(slot.index)-size/2;
                double wx=widget.getX()*screen.uiScale(),wy=widget.getY()*screen.uiScale(),ww=widget.getWidth()*screen.uiScale(),wh=widget.getHeight()*screen.uiScale();
                check(sx+size<=wx||wx+ww<=sx||sy+size<=wy||wy+wh<=sy,"Widget overlaps an active slot: "+widget.getMessage().getString());
            }
        }
        Screenshot.grab(client.gameDirectory, "furnace-"+name+".png", client.getMainRenderTarget(), result -> LOG.info("FURNACE_SCREENSHOT {} {}", name,result.getString())); return true;
    }; }
    private static Step tab(int tab) { return client -> {
        check(client.screen instanceof FurnaceScreen, "Furnace screen absent");
        var button = (Button) client.screen.children().get(tab);
        var screen = (FurnaceScreen)client.screen;
        double x = (button.getX()+button.getWidth()/2.0)*screen.uiScale(), y = (button.getY()+button.getHeight()/2.0)*screen.uiScale();
        check(screen.mouseClicked(x,y,0), "Tab click missed the physical hitbox"); screen.mouseReleased(x,y,0);
        var menu = (FurnaceMenu) client.player.containerMenu;
        check(menu.slots.stream().allMatch(slot -> slot.isActive() == (tab == 0)), "Invisible slots remain active");
        return true;
    }; }
    private static BlockPos pos(FurnaceTier tier) { return new BlockPos(tier.ordinal() * 6, -60, 6); }
    private static void scenes() {
        STEPS.add(viewers());
        STEPS.add(server(player -> {
            player.serverLevel().setDayTime(6000);
            player.setGameMode(GameType.SPECTATOR);
            for (var tier : FurnaceTier.values()) {
                var block = switch(tier) { case I -> FurnaceRegistries.FURNACE_I.get(); case II -> FurnaceRegistries.FURNACE_II.get(); case III -> FurnaceRegistries.FURNACE_III.get(); };
                for (var cell : FurnaceLayout.cells(tier)) player.serverLevel().setBlock(FurnaceLayout.position(pos(tier),Direction.NORTH,cell),
                        block.defaultBlockState().setValue(IndustrialFurnaceBlock.FACING,Direction.NORTH).setValue(IndustrialFurnaceBlock.COLUMN,cell.column())
                                .setValue(IndustrialFurnaceBlock.LAYER,cell.layer()).setValue(IndustrialFurnaceBlock.ROW,cell.row()),Block.UPDATE_ALL);
                var furnace = (IndustrialFurnaceBlockEntity)player.serverLevel().getBlockEntity(pos(tier));
                check(furnace != null, "Master missing"); furnace.setOwner(player.getUUID(),player.getGameProfile().getName());
                player.getInventory().setItem(tier.ordinal(),new ItemStack(block));
            }
        }));
        STEPS.add(client(client -> { client.options.guiScale().set(2); client.options.hideGui=true; client.resizeDisplay(); }));
        for (var tier : FurnaceTier.values()) {
            STEPS.add(server(player -> player.teleportTo(player.serverLevel(),pos(tier).getX()+3,-60,pos(tier).getZ()-3,28,12)));
            STEPS.add(waitTicks(12)); STEPS.add(screenshot("tier-"+tier.number()+"-cold-ports"));
            STEPS.add(server(player -> player.teleportTo(player.serverLevel(),pos(tier).getX()+3,-59,pos(tier).getZ()+4,135,23)));
            STEPS.add(waitTicks(12)); STEPS.add(screenshot("tier-"+tier.number()+"-rear-ports"));
            STEPS.add(server(player -> player.teleportTo(player.serverLevel(),
                    pos(tier).getX()+tier.width()/2.0+3,pos(tier).getY()+tier.height()+3,
                    pos(tier).getZ()+tier.depth()/2.0+3,135,48)));
            STEPS.add(waitTicks(12)); STEPS.add(screenshot("tier-"+tier.number()+"-top-input"));
        }
        STEPS.add(server(player -> {
            var furnace = (IndustrialFurnaceBlockEntity)player.serverLevel().getBlockEntity(pos(FurnaceTier.III));
            furnace.energyPort().insert(furnace.energyCapacity(),false);
            for(int i=0;i<8;i++) furnace.input().setStackInSlot(i,new ItemStack(Items.RAW_IRON,64));
        }));
        STEPS.add(server(player -> player.teleportTo(player.serverLevel(),15,-60,3,28,12)));
        STEPS.add(hotServer());
        STEPS.add(client -> client.level.getBlockEntity(pos(FurnaceTier.III)) instanceof IndustrialFurnaceBlockEntity furnace && furnace.visualHeat()==FurnaceHeat.State.PROCESSING);
        STEPS.add(waitTicks(12)); STEPS.add(screenshot("tier-3-hot-fans"));
        STEPS.add(client(client -> {client.options.hideGui=false; client.getLanguageManager().setSelected("fr_fr");client.reloadResourcePacks();}));
        STEPS.add(waitTicks(30));
        STEPS.add(server(player -> {
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(player.serverLevel(),12.5,-60,3.5,0,12);
            player.gameMode.useItemOn(player,player.serverLevel(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos(FurnaceTier.III)),Direction.NORTH,pos(FurnaceTier.III),false));
        }));
        STEPS.add(client -> client.screen instanceof FurnaceScreen);
        STEPS.add(waitTicks(10)); STEPS.add(screenshot("gui-production-fr"));
        STEPS.add(tab(1)); STEPS.add(waitTicks(5)); STEPS.add(screenshot("gui-energy-fr"));
        STEPS.add(tab(2)); STEPS.add(waitTicks(5)); STEPS.add(screenshot("gui-settings-fr"));
        STEPS.add(client(client -> ((Button)client.screen.children().get(3)).onPress()));
        STEPS.add(waitTicks(5)); STEPS.add(screenshot("manual-fr"));
        STEPS.add(client(client -> client.screen.onClose()));
        STEPS.add(tab(0));
        STEPS.add(client(client -> {client.options.guiScale().set(2);org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(),640,480);client.resizeDisplay();}));
        STEPS.add(waitTicks(8)); STEPS.add(screenshot("gui-small-fr"));
        STEPS.add(server(player -> player.getInventory().setItem(9,new ItemStack(Items.RAW_GOLD,8))));
        STEPS.add(waitTicks(8));
        STEPS.add(client(client -> {
            var screen=(FurnaceScreen)client.screen;var menu=(FurnaceMenu)client.player.containerMenu;
            int source=menu.tier().inputSlots()+menu.tier().outputSlots();
            check(menu.slots.get(source).getItem().is(Items.RAW_GOLD),"Player slot failed to sync");
            screen.mouseClicked(screen.slotMouseX(source),screen.slotMouseY(source),0);screen.mouseReleased(screen.slotMouseX(source),screen.slotMouseY(source),0);
        }));
        STEPS.add(waitTicks(5));
        STEPS.add(client(client -> {var screen=(FurnaceScreen)client.screen;screen.mouseClicked(screen.slotMouseX(8),screen.slotMouseY(8),0);screen.mouseReleased(screen.slotMouseX(8),screen.slotMouseY(8),0);}));
        STEPS.add(waitTicks(5));
        STEPS.add(server(player -> {var furnace=(IndustrialFurnaceBlockEntity)player.serverLevel().getBlockEntity(pos(FurnaceTier.III));
            check(player.getInventory().getItem(9).isEmpty()&&player.containerMenu.getCarried().isEmpty(),"Small-screen slot clicks did not transfer source");
            int gold=0;for(int i=0;i<furnace.input().getSlots();i++)if(furnace.input().getStackInSlot(i).is(Items.RAW_GOLD))gold+=furnace.input().getStackInSlot(i).getCount();
            for(var job:furnace.jobs())if(job!=null&&job.reservedInput.is(Items.RAW_GOLD))gold+=job.reservedInput.getCount();
            check(gold==8,"Small-screen slot transfer lost gold input");LOG.info("FURNACE_SMALL_SLOT_CLICK_OK");}));
        STEPS.add(tab(1)); STEPS.add(waitTicks(5)); STEPS.add(screenshot("gui-small-energy-fr"));
        STEPS.add(client(client -> {client.options.guiScale().set(3);org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(),1280,720);client.resizeDisplay();client.getLanguageManager().setSelected("en_us");client.reloadResourcePacks();}));
        STEPS.add(waitTicks(30)); STEPS.add(tab(0)); STEPS.add(screenshot("gui-production-en-scale3"));
        STEPS.add(tab(2)); STEPS.add(waitTicks(5)); STEPS.add(screenshot("gui-settings-en-scale3"));
        STEPS.add(client(client -> ((Button)client.screen.children().get(3)).onPress()));
        STEPS.add(waitTicks(5)); STEPS.add(screenshot("manual-en-scale3"));
        STEPS.add(client(client -> client.screen.onClose()));
    }
}
