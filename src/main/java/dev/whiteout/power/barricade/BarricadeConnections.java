package dev.whiteout.power.barricade;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

/** Shared, unsaved connection rule for rendering and physics. Four immediate reads only. */
final class BarricadeConnections {
 private BarricadeConnections(){}
 static int mask(BlockView world,BlockState state,BlockPos pos){
  Direction right=state.get(BarricadeBlock.FACING).rotateYCounterclockwise();
  return (joins(world,state,pos.offset(right.getOpposite()))?1:0)
       | (joins(world,state,pos.offset(right))?2:0)
       | (joins(world,state,pos.down())?4:0)
       | (joins(world,state,pos.up())?8:0);
 }
 private static boolean joins(BlockView world,BlockState self,BlockPos pos){
  BlockState other=world.getBlockState(pos);
  return other.isOf(self.getBlock())&&other.get(BarricadeBlock.FACING)==self.get(BarricadeBlock.FACING);
 }
}
