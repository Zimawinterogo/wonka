package dev.whiteout.power.backpack;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.util.math.RotationAxis;

/** GUI-space hands and a hinged fabric cover, over the real inventory's preview. */
final class BackpackOpeningRenderer {
 private BackpackOpeningRenderer(){}
 static void cover(DrawContext context,int x,int y,long elapsed){
  float lid=BackpackOpening.lid(elapsed);
  if(lid>=1)return;
  var matrices=context.getMatrices();
  matrices.push();
  try{
   context.draw();
   matrices.translate(x+22,y+54,500);
   matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-lid*165));
   matrices.scale(1,1-lid,1);
   // Two-sided canvas flap: folds around its upper seam, revealing the slots below.
   RenderSystem.disableCull();
   context.fill(0,0,260,152,0xFF111B20);
   context.fill(3,3,257,149,0xFF273237);
   context.fillGradient(5,5,255,147,0xFF344146,0xFF18252B);
   for(int row=8;row<146;row+=4)context.fill(6,row,254,row+1,0x22202A2D);
   for(int col=8;col<254;col+=6){
    context.fill(col,7,col+3,8,0xFF788184);
    context.fill(col,143,col+3,144,0xFF788184);
   }
   for(int strap:new int[]{40,205}){
    context.fill(strap,0,strap+15,150,0xFF151D20);
    context.fill(strap+2,0,strap+13,150,0xFF3C3D37);
    context.fill(strap-2,106,strap+17,126,0xFF899295);
    context.fill(strap+1,109,strap+14,123,0xFF172126);
    context.fill(strap+6,107,strap+9,126,0xFFADB4B3);
   }
   context.fill(98,56,162,77,0xFF17252B);
  }finally{context.draw();RenderSystem.enableCull();matrices.pop();}
 }
 static void hands(DrawContext context,int x,int y,long elapsed){
  var client=MinecraftClient.getInstance();
  float presence=BackpackOpening.hands(elapsed);
  if(presence<=0||client.player==null)return;
  var skin=client.player.getSkinTextures();
  boolean slim=skin.model()==SkinTextures.Model.SLIM;
  float lid=BackpackOpening.lid(elapsed);
  context.draw();RenderSystem.disableCull();
  try{for(boolean left:new boolean[]{true,false}){
   var matrices=context.getMatrices();matrices.push();
   try{
    float handX=x+(left?32:283);
    float handY=y+175+(1-presence)*160-(left?0:lid*95);
    matrices.translate(handX,handY,800);
    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(left?-24:24-lid*35));
    // Wrist at the grip, shoulder towards the bottom edge of the screen.
    matrices.translate(0,96,0);matrices.scale(1,-1,1);
    int armWidth=slim?3:4;
    context.drawTexture(skin.texture(),-12,0,24,96,left?36:44,left?52:20,armWidth,12,64,64);
    if(client.player.isPartVisible(left?PlayerModelPart.LEFT_SLEEVE:PlayerModelPart.RIGHT_SLEEVE))
     context.drawTexture(skin.texture(),-13,-1,26,98,left?52:44,left?52:36,armWidth,12,64,64);
   }finally{matrices.pop();}
  }}finally{context.draw();RenderSystem.enableCull();}
 }
}
