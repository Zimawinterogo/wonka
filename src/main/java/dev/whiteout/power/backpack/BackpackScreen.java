package dev.whiteout.power.backpack;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.whiteout.power.mixin.BackpackSlotAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;

/** A view over the existing 46-slot player handler. No custom inventory/packets. */
public final class BackpackScreen extends HandledScreen<PlayerScreenHandler> {
 private static final Identifier BAG=Identifier.of("whiteout","textures/gui/backpack/bag.png");
 private int[] originalX,originalY;
 private final long openedAt=Util.getMeasuringTimeMs();
 private boolean skippedOpening;
 private long elapsed(){return Util.getMeasuringTimeMs()-openedAt;}
 private boolean opening(){return !skippedOpening&&!BackpackOpening.ready(elapsed(),BackpackClient.animation());}
 public BackpackScreen(PlayerEntity player){
  super(player.playerScreenHandler,player.getInventory(),Text.translatable("backpack.whiteout.title"));
  backgroundWidth=BackpackLayout.WIDTH;backgroundHeight=BackpackLayout.HEIGHT;
 }
 @Override protected void init(){
  super.init();
  if(originalX==null){
   originalX=new int[handler.slots.size()];originalY=new int[handler.slots.size()];
   for(int i=0;i<handler.slots.size();i++){originalX[i]=handler.slots.get(i).x;originalY[i]=handler.slots.get(i).y;}
  }
  for(int i=0;i<handler.slots.size();i++){
   var point=BackpackLayout.position(i);var access=(BackpackSlotAccess)handler.slots.get(i);
   access.whiteout$setX(point.x());access.whiteout$setY(point.y());
  }
 }
 public boolean hides(Slot slot){
  return slot.id>=0&&slot.id<5&&handler.slots.get(slot.id)==slot;
 }
 @Override public void removed(){
  try{
   if(originalX!=null)for(int i=0;i<originalX.length;i++){
    var access=(BackpackSlotAccess)handler.slots.get(i);
    access.whiteout$setX(originalX[i]);access.whiteout$setY(originalY[i]);
   }
   originalX=null;originalY=null;
  }finally{super.removed();}
 }
 @Override protected void handledScreenTick(){
  if(client==null||client.player==null)return;
  if(client.player.isSpectator()){close();return;}
  if(!BackpackClient.enabledFor(client)||!BackpackClient.emptyCrafting(handler)||!BackpackLayout.fits(width,height))
   client.setScreen(new InventoryScreen(client.player));
 }
 @Override protected void onMouseClick(Slot slot,int id,int button,SlotActionType action){
  // Also covers shift-double-click enumeration, which does not consult isEnabled.
  if(opening()||(id>=0&&!BackpackLayout.visible(id))||(slot!=null&&hides(slot)))return;
  if(!BackpackClient.emptyCrafting(handler))return;
  super.onMouseClick(slot,id,button,action);
 }
 @Override protected void applyBlur(float delta){}
 @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta){
  context.fill(0,0,width,height,0x94060C12);
  drawBackground(context,delta,mouseX,mouseY);
 }
 @Override protected void drawBackground(DrawContext context,float delta,int mouseX,int mouseY){
  RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
  try{
   context.drawTexture(BAG,x,y,backgroundWidth,backgroundHeight,0,0,1499,1049,1499,1049);
  }finally{RenderSystem.setShaderColor(1,1,1,1);}
  context.fill(x+36,y+61,x+222,y+150,0x40030B10);
  context.fill(x+244,y+55,x+279,y+199,0x40030B10);
  for(Slot slot:handler.slots){
   if(!BackpackLayout.visible(slot.id))continue;
   boolean gear=slot.id<9||slot.id==45;
   int sx=x+slot.x-2,sy=y+slot.y-2;
   context.fill(sx,sy,sx+20,sy+20,gear?0xFF9BA9AF:0xFF72868F);
   context.fill(sx+1,sy+1,sx+19,sy+19,0xFF273841);
   context.fill(sx+2,sy+2,sx+18,sy+18,0xF00B151D);
   context.fill(sx+1,sy+19,sx+20,sy+20,0xFF17242C);
  }
  // Thin light on the opening seams, fading in without changing slot geometry.
  int alpha=100;
  context.fill(x+33,y+61,x+216,y+62,(alpha<<24)|0x91A8B4);
  context.fill(x+238,y+60,x+273,y+61,(alpha<<24)|0x91A8B4);
  RenderSystem.disableBlend();
 }
 @Override protected void drawForeground(DrawContext context,int mouseX,int mouseY){
  context.fill(121,17,199,36,0xFF161E23);
  context.fill(123,19,197,34,0xFF3C484C);
  for(int stitch=125;stitch<196;stitch+=5){
   context.fill(stitch,19,stitch+2,20,0xFFACAD99);
   context.fill(stitch,33,stitch+2,34,0xFFACAD99);
  }
  context.drawCenteredTextWithShadow(textRenderer,Text.literal("BACKPACK"),160,23,0xE0E7E6);
  context.drawText(textRenderer,Text.translatable("backpack.whiteout.contents"),40,49,0xB4C5CA,false);
  for(int i=0;i<9;i++){
   var point=BackpackLayout.position(36+i);
   context.drawText(textRenderer,Integer.toString(i+1),point.x()+5,168,0x9CAFB7,false);
  }
  context.drawText(textRenderer,Text.translatable("backpack.whiteout.quick_access"),40,158,0x94A7AE,false);
 }
 @Override public boolean mouseClicked(double mouseX,double mouseY,int button){return opening()||super.mouseClicked(mouseX,mouseY,button);}
 @Override public boolean mouseReleased(double mouseX,double mouseY,int button){return opening()||super.mouseReleased(mouseX,mouseY,button);}
 @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double dx,double dy){return opening()||super.mouseDragged(mouseX,mouseY,button,dx,dy);}
 @Override public boolean keyPressed(int key,int scan,int modifiers){
  if(opening()){
   if(key==256||(client!=null&&client.options.inventoryKey.matchesKey(key,scan)))close();
   // Space skips the cinematic; number keys/drop cannot act on its preview.
   else if(key==32)skippedOpening=true;
   return true;
  }
  return super.keyPressed(key,scan,modifiers);
 }
 @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
  if(opening()){
   renderOpening(context,delta);
   return;
  }
  super.render(context,mouseX,mouseY,delta);
  drawMouseoverTooltip(context,mouseX,mouseY);
  if(focusedSlot!=null&&!focusedSlot.hasStack()&&((focusedSlot.id>=5&&focusedSlot.id<=8)||focusedSlot.id==45))
   context.drawTooltip(textRenderer,Text.translatable("backpack.whiteout.slot."+focusedSlot.id),mouseX,mouseY);
 }
 private void renderOpening(DrawContext context,float delta){
  long time=elapsed();float lift=BackpackOpening.lift(time);
  context.fill(0,0,width,height,((int)(148*BackpackOpening.phase(time,0,500))<<24)|0x060C12);
  var matrices=context.getMatrices();matrices.push();
  RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
  try{
   matrices.translate(x+160,y+112+(1-lift)*(height+120),0);
   matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((1-lift)*-9));
   float scale=.8f+.2f*lift;matrices.scale(scale,scale,1);
   matrices.translate(-x-160,-y-112,0);
   drawBackground(context,delta,-10000,-10000);
   // Render a read-only preview. HandledScreen never receives input until settled.
   for(Slot slot:handler.slots)if(BackpackLayout.visible(slot.id)&&slot.hasStack()){
    context.drawItem(slot.getStack(),x+slot.x,y+slot.y);
    context.drawItemInSlot(textRenderer,slot.getStack(),x+slot.x,y+slot.y);
   }
   matrices.push();matrices.translate(x,y,0);
   drawForeground(context,-10000,-10000);matrices.pop();
   RenderSystem.enableBlend();
   BackpackOpeningRenderer.cover(context,x,y,time);
   BackpackOpeningRenderer.hands(context,x,y,time);
  }finally{
   context.draw();matrices.pop();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();
  }
 }
}
