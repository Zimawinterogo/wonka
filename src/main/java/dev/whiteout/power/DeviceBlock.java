package dev.whiteout.power;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

public final class DeviceBlock extends BlockWithEntity {
 public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
 public static final BooleanProperty ACTIVE=BooleanProperty.of("active");
 public final int kind;
 private final MapCodec<DeviceBlock> codec;
 public DeviceBlock(int kind,Settings settings){super(settings);this.kind=kind;codec=createCodec(s->new DeviceBlock(kind,s));setDefaultState(stateManager.getDefaultState().with(FACING,Direction.NORTH).with(ACTIVE,false));}
 @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return codec;}
 @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING,ACTIVE);}
 @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite());}
 @Override protected BlockRenderType getRenderType(BlockState s){return kind==0||kind==3?BlockRenderType.INVISIBLE:BlockRenderType.MODEL;}
 @Override protected boolean canPlaceAt(BlockState state,WorldView world,BlockPos pos){
  Direction toward=kind==1?state.get(FACING).getOpposite():Direction.DOWN;
  BlockPos support=pos.offset(toward);
  return world.getBlockState(support).isSideSolidFullSquare(world,support,toward.getOpposite());
 }
 @Override protected BlockState getStateForNeighborUpdate(BlockState state,Direction direction,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos){
  Direction toward=kind==1?state.get(FACING).getOpposite():Direction.DOWN;
  return direction==toward&&!state.canPlaceAt(world,pos)?Blocks.AIR.getDefaultState():super.getStateForNeighborUpdate(state,direction,neighbor,world,pos,neighborPos);
 }
 @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new DeviceEntity(p,s);}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w,BlockState s,BlockEntityType<T> t){return validateTicker(t,PowerDevices.ENTITY,DeviceEntity::tick);}
 @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
  Direction d=s.get(FACING);boolean z=d.getAxis()==Direction.Axis.Z;
  if(kind==1) return switch(d){case NORTH->Block.createCuboidShape(3,2,11,13,15,16);case SOUTH->Block.createCuboidShape(3,2,0,13,15,5);case EAST->Block.createCuboidShape(0,2,3,5,15,13);default->Block.createCuboidShape(11,2,3,16,15,13);};
  if(kind==2)return z?Block.createCuboidShape(0,0,3,16,16,13):Block.createCuboidShape(3,0,0,13,16,16);
  return Block.createCuboidShape(1,0,1,15,14,15);
 }
 @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){
  if(!w.isClient && w.getBlockEntity(p) instanceof DeviceEntity e)e.interact((ServerPlayerEntity)player,Hand.MAIN_HAND,hit);
  return ActionResult.SUCCESS;
 }
 @Override protected ItemActionResult onUseWithItem(ItemStack stack,BlockState s,World w,BlockPos p,PlayerEntity player,Hand hand,BlockHitResult hit){
  if(!w.isClient && w.getBlockEntity(p) instanceof DeviceEntity e)e.interact((ServerPlayerEntity)player,hand,hit);
  return ItemActionResult.SUCCESS;
 }
 @Override protected void onStateReplaced(BlockState state,World w,BlockPos p,BlockState next,boolean moved){
  if(!state.isOf(next.getBlock()) && w.getBlockEntity(p) instanceof DeviceEntity e)e.dropContents();
  super.onStateReplaced(state,w,p,next,moved);
  if(!state.isOf(next.getBlock())&&w instanceof net.minecraft.server.world.ServerWorld sw)WhiteoutWorldState.invalidatePower(sw.getServer());
 }
 @Override protected boolean emitsRedstonePower(BlockState s){return kind==1;}
 @Override protected int getWeakRedstonePower(BlockState s,BlockView w,BlockPos p,Direction d){return kind==1&&s.get(ACTIVE)?15:0;}
 @Override protected int getStrongRedstonePower(BlockState s,BlockView w,BlockPos p,Direction d){return getWeakRedstonePower(s,w,p,d);}
}
