package dev.whiteout.power.backpack;

import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Permanent cosmetic attachment: never an item, equipment slot or extra storage. */
public final class BackpackFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
 private static final Identifier TEXTURE=Identifier.of("whiteout","textures/entity/backpack.png");
 private final ModelPart shell,straps,hardware,lid;
 private static ModelPart model(ModelPartBuilder builder){
  ModelData data=new ModelData();data.getRoot().addChild("part",builder,ModelTransform.NONE);
  return TexturedModelData.of(data,64,64).createModel().getChild("part");
 }
 public BackpackFeatureRenderer(PlayerEntityRenderer context){
  super(context);
  shell=model(ModelPartBuilder.create().uv(0,0).cuboid(-5,0,0,10,12,5)
    .uv(0,20).cuboid(-4,6,5,8,5,2).uv(32,0).cuboid(-6,4,1,1,6,4)
    .uv(32,0).cuboid(5,4,1,1,6,4));
  straps=model(ModelPartBuilder.create().uv(0,40).cuboid(-4,-1,-7,2,13,1)
    .uv(0,40).cuboid(2,-1,-7,2,13,1).uv(0,40).cuboid(-4,-1,-7,2,1,8)
    .uv(0,40).cuboid(2,-1,-7,2,1,8).uv(0,40).cuboid(-4,1,5,1,10,1)
    .uv(0,40).cuboid(3,1,5,1,10,1));
  hardware=model(ModelPartBuilder.create().uv(40,40).cuboid(-4.5f,4,5.8f,2,2,1)
    .uv(40,40).cuboid(2.5f,4,5.8f,2,2,1));
  lid=model(ModelPartBuilder.create().uv(0,0).cuboid(-5.5f,-1,0,11,2,6));
 }
 @Override public void render(MatrixStack matrices,VertexConsumerProvider vertices,int light,AbstractClientPlayerEntity player,
   float limbAngle,float limbDistance,float tickDelta,float progress,float headYaw,float headPitch){
  if(player.isSpectator()||player.isInvisible())return;
  float unpack=BackpackVisualClient.unpack(player);
  matrices.push();
  try{
   getContextModel().body.rotate(matrices);
   // Travel around the player's right side, not through the torso, then rest on lap.
   matrices.translate(Math.sin(unpack*Math.PI)*.52,0,.25-unpack*.94);
   matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(unpack*180));
   var buffer=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
   int overlay=OverlayTexture.DEFAULT_UV;
   shell.render(matrices,buffer,light,overlay,0xFF33424B);
   if(unpack<.85f)straps.render(matrices,buffer,light,overlay,0xFF816047);
   hardware.render(matrices,buffer,light,overlay,0xFFC0C9CD);
   lid.pitch=-BackpackVisualClient.lid(player)*1.6f;
   lid.render(matrices,buffer,light,overlay,0xFF46545B);
  }finally{matrices.pop();}
 }
}
