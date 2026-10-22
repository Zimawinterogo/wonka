package dev.whiteout.power;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Visual/audio only: does not write door state or frost counters. */
public final class FrozenDoorEffects {
 private FrozenDoorEffects(){}
 private static Vec3d handle(BlockPos pos,BlockState state,int side){
  var facing=state.get(DoorBlock.FACING);
  var edge=state.get(DoorBlock.HINGE)==DoorHinge.LEFT?facing.rotateYClockwise():facing.rotateYCounterclockwise();
  return new Vec3d(pos.getX()+.5+edge.getOffsetX()*.34+facing.getOffsetX()*.44*side,
   pos.getY()+.98,pos.getZ()+.5+edge.getOffsetZ()*.34+facing.getOffsetZ()*.44*side);
 }
 public static void frost(ServerWorld world,BlockPos pos,BlockState state){
  for(int side:new int[]{-1,1}){
   Vec3d p=handle(pos,state,side);
   world.spawnParticles(new DustParticleEffect(new org.joml.Vector3f(.38f,.72f,.95f),.55f),
    p.x,p.y,p.z,2,.025,.08,.025,0);
  }
 }
 public static void crack(ServerWorld world,BlockPos pos,BlockState state,boolean broken){
  world.playSound(null,pos,PowerDevices.ICE_CRACK,SoundCategory.BLOCKS,broken?.6f:.34f,
   broken?.85f:.55f+world.random.nextFloat()*.12f);
  for(int side:new int[]{-1,1}){
   Vec3d p=handle(pos,state,side);
   world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,Blocks.PACKED_ICE.getDefaultState()),
    p.x,p.y,p.z,broken?9:3,.07,.10,.07,broken?.075:.025);
   if(broken)world.spawnParticles(new DustParticleEffect(new org.joml.Vector3f(.5f,.8f,1f),.6f),
    p.x,p.y,p.z,4,.1,.14,.1,.02);
  }
 }
}
