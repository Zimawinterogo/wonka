package dev.whiteout.power.barricade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

/**
 * Read-only integration surface for shelter / wind / AI systems. Works on server and client,
 * allocates nothing unless a barricade is actually present, and never mutates the world.
 * Cheapest check without any class dependency: block tag {@code #whiteout:barricades}.
 */
public final class BarricadeApi {
 private BarricadeApi(){}
 /** Share of a cell's cross-section the boards must cover to count as a wind barrier.
  * Exact coverage of the least-covered variant (single/variant 1) is 21/32 = 0.65625, so every
  * variant is windproof; the threshold sits just below it so the table can stay honest. */
 public static final double WINDPROOF_MIN_COVERAGE=0.65;

 /** @param plateSide cell face the boards are nailed to; air passes through the cell along its axis.
  *  @param coverage  share (0..1) of the cell cross-section blocked by wood (gaps between boards excluded).
  *  @param plateBox  world-space box of the plate slab inside the cell (3/16 thick, full width and height). */
 public record Info(BlockPos pos,Direction plateSide,BarricadeBlock.Part part,int variant,double coverage,Box plateBox){
  public Direction.Axis airAxis(){return plateSide.getAxis();}
  public boolean windproof(){return coverage>=WINDPROOF_MIN_COVERAGE;}
 }

 public static boolean isBarricade(BlockState s){return s.getBlock() instanceof BarricadeBlock;}

 public static Optional<Info> at(BlockView world,BlockPos pos){
  BlockState s=world.getBlockState(pos);
  if(!(s.getBlock() instanceof BarricadeBlock))return Optional.empty();
  Direction f=s.get(BarricadeBlock.FACING);BarricadeBlock.Part part=s.get(BarricadeBlock.PART);int v=s.get(BarricadeBlock.VARIANT);
  double t=3.0/16.0;
  double x0=pos.getX(),y0=pos.getY(),z0=pos.getZ(),x1=x0+1,z1=z0+1;
  switch(f){
   case SOUTH->z0=z1-t;
   case NORTH->z1=z0+t;
   case EAST->x0=x1-t;
   default->x1=x0+t;
  }
  return Optional.of(new Info(pos.toImmutable(),f,part,v,BarricadeLayouts.COVERAGE[part.ordinal()][v],new Box(x0,y0,z0,x1,y0+1,z1)));
 }
 public static boolean isBarricaded(BlockView world,BlockPos pos){return isBarricade(world.getBlockState(pos));}
 /** 0 when the cell has no barricade. */
 public static double coverage(BlockView world,BlockPos pos){return at(world,pos).map(Info::coverage).orElse(0.0);}
 public static boolean isWindproof(BlockView world,BlockPos pos){return at(world,pos).map(Info::windproof).orElse(false);}
 /** True if air moving in {@code movement} through this cell is stopped by the boards. */
 public static boolean blocksAirFlow(BlockView world,BlockPos pos,Direction movement){
  Optional<Info> i=at(world,pos);return i.isPresent()&&i.get().windproof()&&i.get().airAxis()==movement.getAxis();
 }
 /** All barricade cells of the same opening (vertical stack in the same plane), bottom to top. */
 public static List<BlockPos> openingCells(BlockView world,BlockPos pos){
  Optional<Info> self=at(world,pos);List<BlockPos> out=new ArrayList<>();
  if(self.isEmpty())return out;
  Direction side=self.get().plateSide();BlockPos low=pos;
  while(at(world,low.down()).filter(i->i.plateSide()==side).isPresent())low=low.down();
  for(BlockPos p=low;at(world,p).filter(i->i.plateSide()==side).isPresent();p=p.up())out.add(p);
  return out;
 }
 /** Average coverage of the whole opening (sum over cells / cell count). */
 public static double openingCoverage(BlockView world,BlockPos pos){
  List<BlockPos> cells=openingCells(world,pos);if(cells.isEmpty())return 0;
  double sum=0;for(BlockPos p:cells)sum+=coverage(world,p);return sum/cells.size();
 }
}
