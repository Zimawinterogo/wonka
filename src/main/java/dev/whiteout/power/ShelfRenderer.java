package dev.whiteout.power;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class ShelfRenderer implements BlockEntityRenderer<DeviceEntity> {
 private final net.minecraft.client.render.item.ItemRenderer renderer;
 public ShelfRenderer(BlockEntityRendererFactory.Context c){renderer=c.getItemRenderer();}
 @Override public void render(DeviceEntity e,float delta,MatrixStack m,VertexConsumerProvider v,int light,int overlay){
  if(e.kind()==0 || (e.kind()==1&&!e.fuse))return;
  m.push();m.translate(.5,0,.5);
  float angle=switch(e.getCachedState().get(DeviceBlock.FACING)){case NORTH->0;case SOUTH->180;case EAST->-90;default->90;};
  m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
  if(e.kind()==1){
   m.push();m.translate(-.16,.45,.17);m.scale(.30f,.30f,.30f);
   renderer.renderItem(new net.minecraft.item.ItemStack(e.burnt?PowerDevices.BURNT_FUSE:PowerDevices.FUSE),ModelTransformationMode.FIXED,light,OverlayTexture.DEFAULT_UV,m,v,e.getWorld(),0);
   m.pop();m.pop();return;
  }
  for(int i=0;i<6;i++)if(!e.stacks.get(i).isEmpty()){
   m.push();m.translate(i%2==0?-.25:.25,.22+(i/2)*.31,0);m.scale(.30f,.30f,.30f);
   renderer.renderItem(e.stacks.get(i),ModelTransformationMode.FIXED,light,OverlayTexture.DEFAULT_UV,m,v,e.getWorld(),i);m.pop();
  }
  m.pop();
 }
}
