package dev.whiteout.power.lobby;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

public final class BoardBlock extends Block {
 public static final DirectionProperty FACE=Properties.HORIZONTAL_FACING;
 public static final IntProperty PART=IntProperty.of("part",0,3);
 public BoardBlock(Settings settings){super(settings);setDefaultState(getDefaultState().with(FACE,Direction.NORTH).with(PART,0));}
 @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACE,PART);}
 public static BlockPos cell(BlockPos root,Direction face,int i){return root.offset(face.rotateYCounterclockwise(),i%2).up(i/2);}
 public static BlockPos root(BlockPos pos,BlockState s){int i=s.get(PART);return pos.offset(s.get(FACE).rotateYClockwise(),i%2).down(i/2);}
 static boolean supported(WorldView world,BlockPos pos,Direction face){
  var behind=pos.offset(face.getOpposite());return world.getBlockState(behind).isSideSolidFullSquare(world,behind,face);
 }
 public static boolean complete(World world,BlockPos root,BlockState reference){
  Direction face=reference.get(FACE);
  for(int i=0;i<4;i++){
   var p=cell(root,face,i);var s=world.getBlockState(p);
   if(!s.isOf(reference.getBlock())||s.get(FACE)!=face||s.get(PART)!=i||!supported(world,p,face))return false;
  }
  return true;
 }
 @Override protected VoxelShape getOutlineShape(BlockState s,BlockView world,BlockPos pos,ShapeContext ctx){
  return switch(s.get(FACE)){
   case NORTH->createCuboidShape(0,0,14,16,16,16);case SOUTH->createCuboidShape(0,0,0,16,16,2);
   case EAST->createCuboidShape(0,0,0,2,16,16);default->createCuboidShape(14,0,0,16,16,16);
  };
 }
 @Override protected BlockState getStateForNeighborUpdate(BlockState s,Direction dir,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos other){
  world.scheduleBlockTick(pos,this,1);return s;
 }
 @Override protected void neighborUpdate(BlockState state,World world,BlockPos pos,Block source,BlockPos from,boolean notify){
  super.neighborUpdate(state,world,pos,source,from,notify);if(!world.isClient)world.scheduleBlockTick(pos,this,1);
 }
 @Override protected void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
  super.onStateReplaced(state,world,pos,next,moved);
  if(world.isClient||next.isOf(this))return;var anchor=root(pos,state);
  for(int i=0;i<4;i++){var part=cell(anchor,state.get(FACE),i);if(!part.equals(pos)&&world.getBlockState(part).isOf(this))world.scheduleBlockTick(part,this,1);}
 }
 @Override protected void onBlockAdded(BlockState s,World world,BlockPos pos,BlockState old,boolean notify){
  super.onBlockAdded(s,world,pos,old,notify);world.scheduleBlockTick(pos,this,1);
 }
 @Override public BlockState onBreak(World world,BlockPos pos,BlockState s,PlayerEntity player){
  if(!world.isClient&&player.isCreative()&&s.get(PART)!=0){
   var anchor=root(pos,s);if(world.getBlockState(anchor).equals(s.with(PART,0)))world.removeBlock(anchor,false);
  }
  return super.onBreak(world,pos,s,player);
 }
 @Override protected void scheduledTick(BlockState s,ServerWorld world,BlockPos pos,Random random){
  var anchor=root(pos,s);var face=s.get(FACE);
  for(int i=0;i<4;i++){
   var cell=cell(anchor,face,i);
   if(!world.isChunkLoaded(cell)||!world.isChunkLoaded(cell.offset(face.getOpposite()))){world.scheduleBlockTick(pos,this,20);return;}
  }
  if(!complete(world,root(pos,s),s))world.breakBlock(pos,s.get(PART)==0);
 }
 @Override protected ActionResult onUse(BlockState s,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
  if(player instanceof ServerPlayerEntity p&&complete(world,root(pos,s),s))BoardNetwork.open(p,root(pos,s));
  return ActionResult.SUCCESS;
 }
 public static final class WallItem extends BlockItem{
  public WallItem(BoardBlock block){super(block,new Item.Settings());}
  @Override protected BlockState getPlacementState(ItemPlacementContext ctx){
   Direction face=ctx.getSide();if(!face.getAxis().isHorizontal())return null;
   var world=ctx.getWorld();var root=ctx.getBlockPos();var player=ctx.getPlayer();
   for(int i=0;i<4;i++){
    var pos=cell(root,face,i);var state=getBlock().getDefaultState().with(FACE,face).with(PART,i);
    if(!world.isChunkLoaded(pos)||world.isOutOfHeightLimit(pos)||!world.getWorldBorder().contains(pos)||!world.getBlockState(pos).isAir()||
       !supported(world,pos,face)||player==null||!world.canPlayerModifyAt(player,pos)||!world.canPlace(state,pos,ShapeContext.of(player)))return null;
   }
   return getBlock().getDefaultState().with(FACE,face).with(PART,0);
  }
  @Override protected boolean place(ItemPlacementContext ctx,BlockState state){
   var world=ctx.getWorld();var root=ctx.getBlockPos();var face=state.get(FACE);
   for(int i=0;i<4;i++){
    if(!world.setBlockState(cell(root,face,i),state.with(PART,i),Block.NOTIFY_ALL)){
     for(int j=0;j<i;j++){var p=cell(root,face,j);if(world.getBlockState(p).equals(state.with(PART,j)))world.removeBlock(p,false);}
     return false;
    }
   }
   return true;
  }
 }
}
