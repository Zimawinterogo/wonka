package dev.whiteout.power.barricade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Decides WHAT to board for a click. Pure server-validated planning, no world mutation.
 * Add a new opening size by appending an {@link OpeningShape} to {@link #SHAPES} (first match wins).
 */
public final class BarricadePlanner {
 private BarricadePlanner(){}

 /** Cells to fill, ordered bottom to top, with the plate side and shared visual variant. */
 public record Plan(List<BlockPos> cells,Direction side,int variant){}
 public record Result(Plan plan,String errorKey){
  static Result ok(Plan p){return new Result(p,null);}
  static Result fail(String k){return new Result(null,k);}
 }
 /** Strategy: given a free, anchored origin cell, return the full list of cells it covers (or null). */
 public interface OpeningShape {
  List<BlockPos> match(World world,BlockPos origin,Direction side,PlayerEntity player,int itemsAvailable);
 }
 /** Doorway / tall window: the origin plus one free anchored cell directly below or above. */
 static final OpeningShape VERTICAL_PAIR=(w,o,side,p,items)->{
  if(items<2)return null;
  if(cellFree(w,o.down(),side,p))return List.of(o.down(),o);
  if(cellFree(w,o.up(),side,p))return List.of(o,o.up());
  return null;
 };
 static final OpeningShape SINGLE=(w,o,side,p,items)->List.of(o);
 static final List<OpeningShape> SHAPES=List.of(VERTICAL_PAIR,SINGLE);

 /** A cell can take boards: replaceable, no fluid, inside the world, anchored, editable and not occupied by an entity. */
 static boolean cellFree(World w,BlockPos pos,Direction side,PlayerEntity p){
  if(!w.isInBuildLimit(pos)||!w.canPlayerModifyAt(p,pos))return false;
  BlockState cur=w.getBlockState(pos);
  if(!(cur.isAir()||cur.isReplaceable())||!cur.getFluidState().isEmpty())return false;
  if(!BarricadeBlock.anchored(w,pos,side))return false;
  BlockState probe=Barricades.BARRICADE.getDefaultState().with(BarricadeBlock.FACING,side);
  return w.canPlace(probe,pos,ShapeContext.of(p));
 }

 public static Result plan(World w,BlockPos origin,PlayerEntity p,boolean forceSingle,int itemsAvailable){
  BlockState cur=w.getBlockState(origin);
  if(!(cur.isAir()||cur.isReplaceable())||!cur.getFluidState().isEmpty())return Result.fail("message.whiteout.barricade_blocked");
  Direction side=chooseSide(w,origin,p);
  if(side==null)return Result.fail("message.whiteout.barricade_no_anchor");
  if(!cellFree(w,origin,side,p))return Result.fail("message.whiteout.barricade_blocked");
  List<BlockPos> cells=null;
  for(OpeningShape shape:SHAPES){
   if(forceSingle&&shape!=SINGLE)continue;
   cells=shape.match(w,origin,side,p,itemsAvailable);
   if(cells!=null)break;
  }
  if(cells==null)return Result.fail("message.whiteout.barricade_blocked");
  BlockPos first=cells.get(0);
  int variant=(int)Math.floorMod((first.asLong()*0x9E3779B97F4A7C15L)>>>33,3L);
  return Result.ok(new Plan(new ArrayList<>(cells),side,variant));
 }

 /**
  * When extending existing boards, inherit their exact facing, even if installed
  * from the reverse side. This avoids rotated or backwards panels in a continuous wall.
  */
 static Direction chooseSide(World w,BlockPos cell,PlayerEntity p){
  // Only neighbours within the WALL plane can act as support (not boards in front/behind).
  for(Direction adjacent:Direction.values()){
   BlockState neighbour=w.getBlockState(cell.offset(adjacent));
   if(!(neighbour.getBlock() instanceof BarricadeBlock))continue;
   Direction facing=neighbour.get(BarricadeBlock.FACING);
   if(adjacent.getAxis()==facing.getAxis())continue;
   if(BarricadeBlock.anchored(w,cell,facing))return facing;
  }
  // Original behaviour for the first board nailed into an ordinary framed opening.
  boolean x=BarricadeBlock.anchored(w,cell,Direction.SOUTH);   // wall runs along X, normal Z
  boolean z=BarricadeBlock.anchored(w,cell,Direction.EAST);    // wall runs along Z, normal X
  if(!x&&!z)return null;
  Direction.Axis normal=x&&z?p.getHorizontalFacing().getAxis():(x?Direction.Axis.Z:Direction.Axis.X);
  Vec3d eye=p.getEyePos();
  boolean positive=normal==Direction.Axis.X?eye.x>=cell.getX()+.5:eye.z>=cell.getZ()+.5;
  return Direction.from(normal,positive?Direction.AxisDirection.POSITIVE:Direction.AxisDirection.NEGATIVE);
 }
}
