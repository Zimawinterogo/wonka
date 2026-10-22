package dev.whiteout.power;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;

/** Render the imported generator mesh exactly once, with a visual-only transform. */
public final class DeviceRenderer implements BlockEntityRenderer<DeviceEntity> {
 private final ShelfRenderer shelf;
 public DeviceRenderer(BlockEntityRendererFactory.Context context){shelf=new ShelfRenderer(context);}
 @Override public void render(DeviceEntity e,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay){
  if(e.kind()!=0&&e.kind()!=3){shelf.render(e,delta,matrices,vertices,light,overlay);return;}
  var mc=MinecraftClient.getInstance();var state=e.getCachedState();
  matrices.push();
  double distanceSq=mc.gameRenderer.getCamera().getPos().squaredDistanceTo(e.getPos().toCenterPos());
  boolean far=distanceSq>=24*24;
  float fade=GeneratorMotion.nearFade(distanceSq);
  boolean worn=e.kind()==3;
  long seed=e.getPos().asLong();
  if(fade>0){
   double shake=e.clientMotion.vibration(worn,delta,seed)*fade;
   matrices.translate(shake,Math.abs(shake)*.3,-shake*.6);
   float rock=e.clientMotion.rock(worn,delta,seed)*fade;
   matrices.translate(.5,.5,.5);
   matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rock));
   matrices.translate(-.5,-.5,-.5);
  }
  float indicator=1+(e.clientMotion.indicator(worn,delta,seed)-1)*fade;
  GeneratorModels.render(worn,e.active(),far,indicator,
   state.get(DeviceBlock.FACING),matrices,vertices,light,overlay);
  matrices.pop();
 }
 @Override public int getRenderDistance(){return Math.max(64,MinecraftClient.getInstance().options.getViewDistance().getValue()*16);}
}
