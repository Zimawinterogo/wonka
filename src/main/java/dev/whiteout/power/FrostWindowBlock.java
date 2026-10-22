package dev.whiteout.power;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Optional frostable glass (ordinary windows remain intact, not silently replaced). */
public final class FrostWindowBlock extends TransparentBlock {
 public static final IntProperty FROST=IntProperty.of("frost",0,3);
 public FrostWindowBlock(Settings settings){super(settings);setDefaultState(getDefaultState().with(FROST,0));}
 @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){super.appendProperties(builder);builder.add(FROST);}
 @Override protected void randomTick(BlockState state,ServerWorld world,BlockPos pos,Random random){
  if(!WorldAtmosphere.get(world.getServer()).windowFrost)return;
  boolean freezing=WorldAtmosphere.blizzard(world,pos);
  int stage=state.get(FROST);
  if(freezing&&stage<3&&random.nextInt(3)==0)world.setBlockState(pos,state.with(FROST,stage+1),3);
  else if(!freezing&&stage>0&&random.nextInt(4)==0)world.setBlockState(pos,state.with(FROST,stage-1),3);
 }
 @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
  if(state.get(FROST)==0)return ActionResult.PASS;
  if(!world.isClient){world.setBlockState(pos,state.with(FROST,0),3);world.playSound(null,pos,SoundEvents.BLOCK_GLASS_HIT,SoundCategory.BLOCKS,.35f,1.3f);}
  return ActionResult.SUCCESS;
 }
}
