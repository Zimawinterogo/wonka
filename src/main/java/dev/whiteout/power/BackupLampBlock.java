package dev.whiteout.power;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** An emergency lamp; the backup charge is handled by its block entity. */
public final class BackupLampBlock extends BlockWithEntity {
 public static final BooleanProperty LIT=Properties.LIT;
 private static final MapCodec<BackupLampBlock> CODEC=createCodec(BackupLampBlock::new);
 public BackupLampBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(LIT,false));}
 @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return CODEC;}
 @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(LIT);}
 @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new BackupLampEntity(pos,state);}
 @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world,BlockState state,BlockEntityType<T> type){
  return world.isClient?null:validateTicker(type,PowerDevices.EMERGENCY_LAMP_ENTITY,BackupLampEntity::tick);
 }
 /** A smaller attached fixture must not cull the face of the supporting wall. */
 @Override protected VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
 @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return Block.createCuboidShape(2,4,2,14,15,14);}
 @Override protected VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return getOutlineShape(state,world,pos,context);}
 /** Read-only charge indicator; does not toggle the lamp or consume charge. */
 @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
  if(!world.isClient && !player.isSpectator() && world.getBlockEntity(pos) instanceof BackupLampEntity lamp){
   player.sendMessage(Text.translatable("message.whiteout.backup_lamp.charge",
    BackupLampPolicy.percent(lamp.charge(),BackupLampEntity.MAX_CHARGE),
    Text.translatable(lamp.statusTranslationKey())),true);
  }
  return ActionResult.SUCCESS;
 }
 @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
}
