package dev.whiteout.power;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;

/** Emissions are client-local and limited to nearby running/cooling engines. */
public final class GeneratorParticles {
 private GeneratorParticles(){}
 public static void tick(DeviceEntity e,boolean started){
  var mc=MinecraftClient.getInstance();var w=e.getWorld();
  if(w==null||!w.isClient||mc.player==null||mc.player.squaredDistanceTo(e.getPos().toCenterPos())>24*24)return;
  boolean worn=e.kind()==3;
  var back=e.getCachedState().get(DeviceBlock.FACING).getOpposite();
  double x=e.getPos().getX()+.5+back.getOffsetX()*.4,y=e.getPos().getY()+.72,z=e.getPos().getZ()+.5+back.getOffsetZ()*.4;
  if(started&&worn)for(int i=0;i<6;i++)w.addParticle(ParticleTypes.LARGE_SMOKE,
   x+(w.random.nextDouble()-.5)*.12,y,z+(w.random.nextDouble()-.5)*.12,back.getOffsetX()*.025,.03+w.random.nextDouble()*.025,back.getOffsetZ()*.025);
  if(e.clientMotion.amount(1)>.05f&&w.random.nextInt(worn?10:24)==0)
   w.addParticle(worn?ParticleTypes.LARGE_SMOKE:ParticleTypes.SMOKE,x,y,z,back.getOffsetX()*.012,.025,back.getOffsetZ()*.012);
 }
}
