package fr.lkdm.homelink.furnace.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The sole conversion between local machine coordinates and world coordinates. */
public final class FurnaceLayout {
    public record Cell(int column, int layer, int row) {}
    public record Port(Cell cell, Direction face) {}
    public static final Cell MASTER = new Cell(0, 0, 0);
    private FurnaceLayout() {}

    public static List<Cell> cells(FurnaceTier tier) {
        List<Cell> cells = new ArrayList<>(tier.width() * tier.height() * tier.depth());
        for (int row = 0; row < tier.depth(); row++)
            for (int layer = 0; layer < tier.height(); layer++)
                for (int column = 0; column < tier.width(); column++) cells.add(new Cell(column, layer, row));
        return List.copyOf(cells);
    }
    public static BlockPos position(BlockPos master, Direction facing, Cell cell) {
        if (facing.getAxis().isVertical()) throw new IllegalArgumentException("Horizontal facing required");
        return master.relative(facing.getClockWise(), cell.column()).above(cell.layer())
                .relative(facing.getOpposite(), cell.row());
    }
    public static BlockPos master(BlockPos part, Direction facing, Cell cell) {
        return position(part, facing, new Cell(-cell.column(), -cell.layer(), -cell.row()));
    }
    public static Optional<Cell> locate(BlockPos master, Direction facing, FurnaceTier tier, BlockPos world) {
        return cells(tier).stream().filter(cell -> position(master, facing, cell).equals(world)).findFirst();
    }
    public static Port input(FurnaceTier tier, Direction facing) {
        return new Port(tier == FurnaceTier.III ? new Cell(0, 1, 1) : MASTER, Direction.UP);
    }
    public static Port output(FurnaceTier tier, Direction facing) {
        return new Port(tier == FurnaceTier.III ? new Cell(1, 0, 1)
                : tier == FurnaceTier.II ? new Cell(1, 0, 0) : MASTER, facing.getOpposite());
    }
    public static Port energy(FurnaceTier tier, Direction facing) {
        return new Port(tier == FurnaceTier.I ? MASTER : new Cell(1, 0, 0), facing.getClockWise());
    }
    public static boolean matches(Port port, Cell cell, Direction face) {
        return port.cell().equals(cell) && port.face() == face;
    }
}
