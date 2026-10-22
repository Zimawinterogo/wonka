package dev.whiteout.power;

import dev.whiteout.plot.PlotRegion;
import dev.whiteout.plot.SpruceStampTemplate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Restores missing branches, needles and snow of damaged spruce trees with the EXACT
 * geometries of Whiteout's existing SpruceStampTemplate (rather than a random blob).
 *
 * Called instead of the old destructive healForestEdgeImmediate, but still INSIDE
 * PlotTools' original captureUnion/runTransactional, so the existing Undo works.
 * No chunks are loaded, no non-air blocks are overwritten, no client classes used.
 */
public final class ForestStampRepair {
    private ForestStampRepair() {}

    /** Historical Move/Swap compatibility: NEVER modify forest as a side effect. */
    public static int skipAutomaticHeal(ServerWorld world, PlotRegion region, int radius){return 0;}

    private static final int EDIT_MARGIN = 8; // Must match PlotTools.expandForForest (Undo boundary)
    // Whiteout's stamps have 25 / 31 log segments. Reject arbitrary tall pillars.
    private static final int TRUNK_MIN = 23;
    private static final int TRUNK_MAX = 33;
    private static final int MAX_REPAIRS = 64;
    private static final int MAX_CHANGES = 24_000;
    // One command runs on the server thread: never scan multi-million-block areas.
    private static final long MAX_SCAN_BLOCKS = 65_536L;

    private record Stamp(int[][] logX, int[][] logY, int[][] logZ,
                         int[][] leaves, int[][] snow1, int[][] snow2) {}
    private record Tree(BlockPos base, int height) {}
    private record Change(BlockPos pos, BlockState state) {}

    // Reflection ONLY reads immutable coordinate tables already embedded in the
    // same Whiteout JAR. It never edits the original stamp or stores a second copy.
    private static final class Template {
        private static final Stamp SHAPE = load();
        private static int[][] coordinates(String field) throws ReflectiveOperationException {
            Field f = SpruceStampTemplate.class.getDeclaredField(field);
            f.setAccessible(true);
            return (int[][]) f.get(null);
        }
        private static Stamp load() {
            try {
                return new Stamp(coordinates("LOG_X"), coordinates("LOG_Y"),
                    coordinates("LOG_Z"), coordinates("LEAVES"),
                    coordinates("SNOW_1"), coordinates("SNOW_2"));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Whiteout spruce stamp geometry was not found", ex);
            }
        }
    }

    private static boolean insideUndo(PlotRegion region, ServerWorld world, BlockPos pos) {
        return pos.getX() >= region.minX() - EDIT_MARGIN
            && pos.getX() <= region.maxX() + EDIT_MARGIN
            && pos.getZ() >= region.minZ() - EDIT_MARGIN
            && pos.getZ() <= region.maxZ() + EDIT_MARGIN
            && pos.getY() >= Math.max(world.getBottomY(), region.minY() - EDIT_MARGIN)
            && pos.getY() <= Math.min((world.getTopY() - 1), region.maxY() + EDIT_MARGIN);
    }

    private static boolean loaded(ServerWorld world, BlockPos pos) {
        return world.isChunkLoaded(pos);
    }

    private static boolean isTreeTrunk(BlockState state) {
        return state.isOf(Blocks.SPRUCE_LOG) && state.get(Properties.AXIS) == Direction.Axis.Y;
    }

    private static boolean naturalGround(BlockState state) {
        return state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.PODZOL)
            || state.isOf(Blocks.DIRT) || state.isOf(Blocks.COARSE_DIRT)
            || state.isOf(Blocks.ROOTED_DIRT) || state.isOf(Blocks.MOSS_BLOCK)
            || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.MYCELIUM);
    }

    private static BlockPos translate(BlockPos origin, int[] relative, int delta) {
        int y = relative[1] < 5 ? relative[1] : relative[1] + delta;
        return origin.add(relative[0], y, relative[2]);
    }

    private static boolean canRepair(ServerWorld world, PlotRegion region, BlockPos base, int delta) {
        // Check the entire stamp footprint first: avoid half-grown trees caused
        // by unloaded chunks or by incomplete Undo coverage at a plot boundary.
        Stamp stamp = Template.SHAPE;
        for (int[][] layer : new int[][][]{stamp.logX, stamp.logY, stamp.logZ,
                                           stamp.leaves, stamp.snow1, stamp.snow2}) {
            for (int[] cell : layer) {
                BlockPos p = translate(base, cell, delta);
                if (!insideUndo(region, world, p) || !loaded(world, p)) return false;
            }
        }
        return true;
    }

    private static void addIfAir(ServerWorld world, BlockPos p, BlockState state,
                                 List<Change> changes, Set<BlockPos> used) {
        if (used.add(p) && world.getBlockState(p).isAir()) changes.add(new Change(p, state));
    }

    private static int repairTree(ServerWorld world, PlotRegion region, Tree tree, int remainingBudget) {
        int delta = Math.abs(tree.height - 25) <= Math.abs(tree.height - 31) ? 0 : 6;
        BlockPos base = tree.base;
        if (!canRepair(world, region, base, delta)) return 0;
        Stamp s = Template.SHAPE;
        // A solitary log pillar is indistinguishable from a player build.
        // Match intact lateral branches in two layers (or branches plus needles)
        // before repairing. A completely stripped trunk is deliberately skipped.
        int branchCount = 0;
        Set<Integer> branchLevels = new HashSet<>();
        for(int[] p:s.logX){
            BlockState st=world.getBlockState(translate(base,p,delta));
            if(st.isOf(Blocks.SPRUCE_LOG) && st.get(Properties.AXIS)==Direction.Axis.X){branchCount++;branchLevels.add(p[1]);}
        }
        for(int[] p:s.logZ){
            BlockState st=world.getBlockState(translate(base,p,delta));
            if(st.isOf(Blocks.SPRUCE_LOG) && st.get(Properties.AXIS)==Direction.Axis.Z){branchCount++;branchLevels.add(p[1]);}
        }
        int existingLeaves = 0, blockedLeaves = 0, emptyLeaves = 0;
        for (int[] entry : s.leaves) {
            BlockState state = world.getBlockState(translate(base, entry, delta));
            if (state.isOf(Blocks.SPRUCE_LEAVES)) existingLeaves++;
            else if (state.isAir()) emptyLeaves++;
            else blockedLeaves++;
        }
        int total = s.leaves.length;
        // Require recognizable stamp evidence, not a naturally grown vanilla spruce.
        if(!ForestCandidatePolicy.stampEvidence(branchCount,branchLevels.size(),existingLeaves))return 0;
        // Already healthy, or obscured by buildings/other non-tree blocks:
        // do not stamp a random spruce over existing construction.
        if (emptyLeaves < 100 || emptyLeaves * 10 < total * 3
                || existingLeaves * 10 >= total * 7 || blockedLeaves * 10 > total) return 0;

        List<Change> logs = new ArrayList<>();
        List<Change> foliage = new ArrayList<>();
        List<Change> snow = new ArrayList<>();
        Set<BlockPos> placed = new HashSet<>();
        BlockState yLog = Blocks.SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Y);
        BlockState xLog = Blocks.SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X);
        BlockState zLog = Blocks.SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Z);
        for (int[] p : s.logY) addIfAir(world, translate(base, p, delta), yLog, logs, placed);
        if (delta > 0) {
            for (int y = 5; y < 5 + delta; y++)
                addIfAir(world, base.up(y), yLog, logs, placed);
        }
        for (int[] p : s.logX) addIfAir(world, translate(base, p, delta), xLog, logs, placed);
        for (int[] p : s.logZ) addIfAir(world, translate(base, p, delta), zLog, logs, placed);
        BlockState leaves = Blocks.SPRUCE_LEAVES.getDefaultState()
            .with(Properties.PERSISTENT, true).with(Properties.DISTANCE_1_7, 1);
        for (int[] p : s.leaves) addIfAir(world, translate(base, p, delta), leaves, foliage, placed);
        for (int[] p : s.snow1) addIfAir(world, translate(base, p, delta),
            Blocks.SNOW.getDefaultState().with(Properties.LAYERS, 1), snow, placed);
        for (int[] p : s.snow2) addIfAir(world, translate(base, p, delta),
            Blocks.SNOW.getDefaultState().with(Properties.LAYERS, 2), snow, placed);

        // Avoid partially reconstructed trees when a large operation hits its cap.
        if (logs.size() + foliage.size() + snow.size() > remainingBudget)
            throw new IllegalArgumentException("Forest repair exceeds block budget; select a smaller region (transaction rolled back)");
        int count = 0;
        for (Change c : logs) if (world.getBlockState(c.pos).isAir()
                && world.setBlockState(c.pos, c.state, 50)) count++;
        for (Change c : foliage) if (world.getBlockState(c.pos).isAir()
                && world.setBlockState(c.pos, c.state, 50)) count++;
        // Snow is last; its supporting spruce leaves have just been placed.
        for (Change c : snow) if (world.getBlockState(c.pos).isAir()
                && c.state.canPlaceAt(world, c.pos)
                && world.getBlockState(c.pos.down()).isOf(Blocks.SPRUCE_LEAVES)
                && world.setBlockState(c.pos, c.state, 50)) count++;
        return count;
    }

    /** Exact signature of PlotTools.healForestEdgeImmediate (intermediary on disk). */
    public static int heal(ServerWorld world, PlotRegion region, int ignoredOldRadius) {
        Stamp stamp = Template.SHAPE; // Fail before any world modifications if template missing.
        if (stamp.leaves.length < 200) throw new IllegalStateException("Invalid spruce stamp");
        int bottom = Math.max(world.getBottomY(), region.minY() - EDIT_MARGIN);
        int top = Math.min((world.getTopY() - 1) - 1, region.maxY() + EDIT_MARGIN);
        long width=(long)region.maxX()-region.minX()+1;
        long depth=(long)region.maxZ()-region.minZ()+1;
        long scanHeight=Math.max(0,top-bottom);
        if(width<=0||depth<=0||scanHeight<=0||width>MAX_SCAN_BLOCKS||depth>MAX_SCAN_BLOCKS
          ||width*depth>MAX_SCAN_BLOCKS||width*depth*scanHeight>MAX_SCAN_BLOCKS){
            throw new IllegalArgumentException("Heal Forest Edge: selection exceeds "+MAX_SCAN_BLOCKS+" scan blocks; use a smaller selection.");
        }
        List<Tree> trunks = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                // Never load new chunks just to inspect a forest.
                if (!loaded(world, new BlockPos(x, bottom, z))) continue;
                for (int y = bottom + 1; y < top - TRUNK_MIN; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isTreeTrunk(world.getBlockState(pos))) continue;
                    if (isTreeTrunk(world.getBlockState(pos.down()))) continue;
                    if (!naturalGround(world.getBlockState(pos.down()))) continue;
                    int height = 0;
                    while (height < TRUNK_MAX + 1 && y + height <= top
                            && isTreeTrunk(world.getBlockState(pos.up(height)))) height++;
                    if (height < TRUNK_MIN || height > TRUNK_MAX
                            || !ForestCandidatePolicy.expectedHeight(height)) continue;
                    // A 2x2 trunk and player-built wooden pillars are not spruce stamps.
                    boolean otherTrunk = false;
                    for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        BlockPos neighbor = pos.add(d[0], 1, d[1]);
                        if (isTreeTrunk(world.getBlockState(neighbor))
                            && isTreeTrunk(world.getBlockState(neighbor.up(4)))) otherTrunk = true;
                    }
                    if (otherTrunk) continue;
                    long key = BlockPos.asLong(x, y, z);
                    if (seen.add(key)) trunks.add(new Tree(pos, height));
                    y += height - 1;
                }
            }
        }
        int changed = 0, repaired = 0;
        for (Tree t : trunks) {
            if (repaired >= MAX_REPAIRS || changed >= MAX_CHANGES)
                throw new IllegalArgumentException("Forest repair limit reached; select a smaller region (transaction rolled back)");
            int n = repairTree(world, region, t, MAX_CHANGES - changed);
            if (n > 0) { changed += n; repaired++; }
        }
        return changed;
    }
}
