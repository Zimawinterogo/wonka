package dev.whiteout.power;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.WorldAccess;

public final class ThermometerBlock extends Block {
 public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
 public ThermometerBlock(Settings settings){super(settings);setDefaultState(getDefaultState().with(FACING,Direction.NORTH));}
 @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
 @Override public BlockState getPlacementState(ItemPlacementContext ctx){
  Direction facing=ctx.getSide().getAxis().isHorizontal()?ctx.getSide().getOpposite():ctx.getHorizontalPlayerFacing();
  BlockState state=getDefaultState().with(FACING,facing);
  return state.canPlaceAt(ctx.getWorld(),ctx.getBlockPos())?state:null;
 }
 @Override protected boolean canPlaceAt(BlockState state,WorldView world,BlockPos pos){
  Direction supportSide=state.get(FACING);
  BlockPos wall=pos.offset(supportSide);
  return world.getBlockState(wall).isSideSolidFullSquare(world,wall,supportSide.getOpposite());
 }
 @Override protected BlockState getStateForNeighborUpdate(BlockState state,Direction direction,BlockState neighborState,WorldAccess world,BlockPos pos,BlockPos neighborPos){
  return direction==state.get(FACING)&&!state.canPlaceAt(world,pos)?Blocks.AIR.getDefaultState():super.getStateForNeighborUpdate(state,direction,neighborState,world,pos,neighborPos);
 }
 @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
  return switch(s.get(FACING)){
   // FACING points TOWARD the supporting wall, not away from it.
   case NORTH->Block.createCuboidShape(5,3,0,11,13,2);
   case SOUTH->Block.createCuboidShape(5,3,14,11,13,16);
   case EAST->Block.createCuboidShape(14,3,5,16,13,11);
   default->Block.createCuboidShape(0,3,5,2,13,11);
  };
 }
 @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
  if(world instanceof ServerWorld sw){
   float temp=WorldAtmosphere.temperature(sw,pos);
   player.sendMessage(Text.translatable("message.whiteout.temperature",String.format(java.util.Locale.ROOT,"%.1f",temp)),true);
   world.playSound(null,pos,SoundEvents.BLOCK_LEVER_CLICK,SoundCategory.BLOCKS,.3f,1.1f);
  }
  return ActionResult.SUCCESS;
 }
}
