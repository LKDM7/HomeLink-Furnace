package fr.lkdm.homelink.furnace.furnace;

import java.util.Optional;
import java.util.UUID;
import fr.lkdm.homecore.api.production.ProductionStart;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Owned reservation and immutable recipe snapshot. Only the server mutates a job. */
public final class FurnaceJob {
    public final UUID transactionId;
    public final ResourceLocation recipeId;
    public final ItemStack reservedInput, expectedResult;
    public final UUID producer;
    public final ProductionStart start;
    public final long startedTick;
    public final int duration, cookingTime;
    public final long totalEnergy;
    public final float xp;
    public int progress;
    public long paidEnergy;
    public FurnaceJobState state = FurnaceJobState.PROCESSING;
    public ItemStack pending = ItemStack.EMPTY;
    public boolean receiptPublished;
    public long completedTick;

    public FurnaceJob(UUID transactionId, ResourceLocation recipeId, ItemStack reservedInput,
                      ItemStack expectedResult, UUID producer, ProductionStart start, long startedTick,
                      int cookingTime, int duration, long totalEnergy, float xp) {
        if (transactionId == null || recipeId == null || reservedInput.isEmpty() || expectedResult.isEmpty()
                || cookingTime < 1 || cookingTime > FurnaceEnergyCost.MAX_COOKING_TICKS || duration < 1
                || duration > 100_000_000 || totalEnergy < 1 || totalEnergy > FurnaceEnergyCost.MAX_OPERATION_ENERGY
                || !Float.isFinite(xp) || xp < 0 || xp > 1_000_000) throw new IllegalArgumentException("Invalid furnace job");
        this.transactionId = transactionId; this.recipeId = recipeId;
        this.reservedInput = reservedInput.copy(); this.expectedResult = expectedResult.copy();
        this.producer = producer; this.start = start; this.startedTick = Math.max(0, startedTick);
        this.cookingTime = cookingTime; this.duration = duration; this.totalEnergy = totalEnergy; this.xp = xp;
    }
    public double progressRatio() { return (double) progress / duration; }
    public long nextEnergy() { return FurnaceEnergyCost.nextCost(totalEnergy, duration, progress); }
    public long energyRemainder() { return FurnaceEnergyCost.remainder(totalEnergy, duration, progress); }
    public boolean completed() { return progress >= duration; }
    /** Returns paid HE; a zero return may mean a fractional tick. Inspect progress/state. */
    public long advance(long availableEnergy) {
        if (completed() || state == FurnaceJobState.FINISHED_WAITING_OUTPUT) return 0;
        long due = nextEnergy();
        // Positive stored energy is required even when this fractional tick owes no whole HE.
        if (availableEnergy <= 0 || availableEnergy < due) { state = FurnaceJobState.PAUSED_NO_POWER; return 0; }
        paidEnergy += due; progress++; state = FurnaceJobState.PROCESSING;
        return due;
    }
    /** Materializes the result once, after every HE is paid; receipts/XP occur in the BE. */
    public boolean finish() {
        if (!completed() || paidEnergy != totalEnergy || !pending.isEmpty()
                || state == FurnaceJobState.FINISHED_WAITING_OUTPUT) return false;
        pending = expectedResult.copy(); state = FurnaceJobState.FINISHED_WAITING_OUTPUT; return true;
    }
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1); tag.putUUID("transaction", transactionId); tag.putString("recipe", recipeId.toString());
        tag.put("reserved", reservedInput.save(registries)); tag.put("result", expectedResult.save(registries));
        if (producer != null) tag.putUUID("producer", producer);
        if (start != null) { tag.putUUID("epoch", start.epoch()); tag.putLong("ordinal", start.ordinal()); }
        tag.putLong("started", startedTick); tag.putInt("cooking", cookingTime); tag.putInt("duration", duration);
        tag.putLong("totalEnergy", totalEnergy); tag.putInt("progress", progress); tag.putLong("paid", paidEnergy);
        tag.putLong("fraction", energyRemainder()); tag.putFloat("xp", xp); tag.putString("state", state.name());
        if (!pending.isEmpty()) tag.put("pending", pending.save(registries));
        tag.putBoolean("receiptPublished", receiptPublished); tag.putLong("completedTick", completedTick); return tag;
    }
    /** Invalid records are rejected, never silently converted to a different recipe or transaction. */
    public static Optional<FurnaceJob> load(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            if (!tag.hasUUID("transaction")) return Optional.empty();
            ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("recipe"));
            ItemStack reserved = ItemStack.parseOptional(registries, tag.getCompound("reserved"));
            ItemStack result = ItemStack.parseOptional(registries, tag.getCompound("result"));
            ProductionStart start = tag.hasUUID("epoch") && tag.getLong("ordinal") >= 0
                    ? new ProductionStart(tag.getUUID("epoch"), tag.getLong("ordinal")) : null;
            FurnaceJob job = new FurnaceJob(tag.getUUID("transaction"), recipe, reserved, result,
                    tag.hasUUID("producer") ? tag.getUUID("producer") : null, start, tag.getLong("started"),
                    tag.getInt("cooking"), tag.getInt("duration"), tag.getLong("totalEnergy"), tag.getFloat("xp"));
            job.progress = Math.max(0, Math.min(job.duration, tag.getInt("progress")));
            long expectedPaid = FurnaceEnergyCost.cumulativeCost(job.totalEnergy, job.duration, job.progress);
            // Corrupt accounting cannot yield free finished outputs.
            if (tag.getLong("paid") != expectedPaid) return Optional.empty();
            job.paidEnergy = expectedPaid; job.state = FurnaceJobState.parse(tag.getString("state"));
            job.pending = ItemStack.parseOptional(registries, tag.getCompound("pending"));
            if (!job.pending.isEmpty()) {
                if (!job.completed() || !ItemStack.isSameItemSameComponents(job.pending, job.expectedResult)
                        || job.pending.getCount() > job.expectedResult.getCount()) return Optional.empty();
                job.state = FurnaceJobState.FINISHED_WAITING_OUTPUT;
            } else if (job.state == FurnaceJobState.FINISHED_WAITING_OUTPUT) return Optional.empty();
            job.receiptPublished = tag.getBoolean("receiptPublished");
            job.completedTick = Math.max(job.startedTick, tag.getLong("completedTick")); return Optional.of(job);
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
}
