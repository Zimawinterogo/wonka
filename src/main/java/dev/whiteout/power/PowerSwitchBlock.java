package dev.whiteout.power;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;

/** Wall-mounted circuit switch. Power is always decided by the server. */
public final class PowerSwitchBlock extends Block {
 public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
 public static final BooleanProperty ON=BooleanProperty.of("on");
 public PowerSwitchBlock(Settings settings){super(settings);setDefaultState(getDefaultState().with(FACING,Direction.NORTH).with(ON,true));}
 @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACING,ON);}
 @Override public BlockState getPlacementState(ItemPlacementContext context){
  // FACING points at the supporting wall: the lever protrudes AWAY from it.
  Direction towardWall=context.getSide().getAxis().isHorizontal()
   ?context.getSide().getOpposite():context.getHorizontalPlayerFacing();
  return getDefaultState().with(FACING,towardWall);
 }
 @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
  return switch(s.get(FACING)){
   case NORTH->Block.createCuboidShape(3,4,0,13,14,5);
   case SOUTH->Block.createCuboidShape(3,4,11,13,14,16);
   case WEST->Block.createCuboidShape(0,4,3,5,14,13);
   default->Block.createCuboidShape(11,4,3,16,14,13);
  };
 }
 @Override protected VoxelShape getCollisionShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
  return getOutlineShape(s,w,p,c); // no invisible full-cube collision
 }
 @Override protected VoxelShape getCullingShape(BlockState s,BlockView w,BlockPos p){return net.minecraft.util.shape.VoxelShapes.empty();}
 @Override protected boolean canPlaceAt(BlockState state,WorldView world,BlockPos pos){
  Direction wall=state.get(FACING);BlockPos support=pos.offset(wall);
  return world.getBlockState(support).isSideSolidFullSquare(world,support,wall.getOpposite());
 }
 @Override protected BlockState getStateForNeighborUpdate(BlockState state,Direction direction,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos){
  return direction==state.get(FACING)&&!state.canPlaceAt(world,pos)?Blocks.AIR.getDefaultState():super.getStateForNeighborUpdate(state,direction,neighbor,world,pos,neighborPos);
 }
 private ActionResult use(World world,BlockPos pos,PlayerEntity player){
  if(world.isClient)return ActionResult.SUCCESS;
  if(player.isSpectator())return ActionResult.PASS;
  if(player.isCreative()&&player.isSneaking())PowerSwitches.openEditor((net.minecraft.server.network.ServerPlayerEntity)player,pos);
  else PowerSwitches.toggle((net.minecraft.server.network.ServerPlayerEntity)player,pos);
  return ActionResult.SUCCESS;
 }
 @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){return use(world,pos,player);}
 @Override protected ItemActionResult onUseWithItem(ItemStack stack,BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
  ActionResult result=use(world,pos,player);return result==ActionResult.PASS?ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION:ItemActionResult.SUCCESS;
 }
 @Override protected void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
  if(!state.isOf(next.getBlock())&&world instanceof net.minecraft.server.world.ServerWorld serverWorld){
   PowerSwitches.remove(serverWorld,pos);
  }
  super.onStateReplaced(state,world,pos,next,moved);
 }
}
