package dev.whiteout.power;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Lightweight high-contrast polar-horror palette. No textures, shaders or blur required. */
public final class WhiteoutTheme {
 private WhiteoutTheme(){}
 public static final int INK=0xF20A121B, PANEL=0xF31B2934, EDGE=0xFF627B88;
 public static final int ICE=0xFF9BDDE2, SNOW=0xFFF1F6F6, MUTED=0xFFB0C6CE, WARN=0xFFFFA19C;

 public static void backdrop(DrawContext ctx,int width,int height){
  ctx.fill(0,0,width,height,0xC8061019);
  // Fixed snow-like static scratches: no per-frame random allocations or texture sampling.
  for(int i=0;i<26;i++){
   int x=Math.floorMod(i*97+17,width),y=Math.floorMod(i*53+9,height);
   ctx.fill(x,y,x+((i%4)==0?3:1),y+1,0x4488BFC9);
  }
 }
 public static void panel(DrawContext ctx,int x,int y,int w,int h,String heading){
  ctx.fill(x+2,y+3,x+w+3,y+h+4,0x8B000307);
  ctx.fill(x,y,x+w,y+h,PANEL);
  ctx.fill(x,y,x+w,y+2,ICE);
  ctx.fill(x,y+2,x+1,y+h,EDGE);
  ctx.fill(x+w-1,y+2,x+w,y+h,EDGE);
  ctx.fill(x,y+h-1,x+w,y+h,EDGE);
  ctx.fill(x+10,y+30,x+w-10,y+31,0x77677E87);
  if(heading!=null&&!heading.isBlank())
   ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,Text.literal(heading),x+13,y+12,SNOW);
 }
 /** Called only while a Whiteout Screen is open, by the PressableWidget mixin. */
 public static void button(DrawContext ctx, ButtonWidget button){
  int x=button.getX(),y=button.getY(),w=button.getWidth(),h=button.getHeight();
  boolean on=button.active,hover=button.isHovered();
  int fill=!on?0xCC1B252E:(hover?0xF03A5963:0xE922343E);
  int edge=!on?0xFF39434A:(hover?ICE:EDGE);
  ctx.fill(x,y,x+w,y+h,fill);
  ctx.fill(x,y,x+w,y+1,edge);
  ctx.fill(x,y+h-1,x+w,y+h,edge);
  ctx.fill(x,y+1,x+1,y+h-1,edge);
  ctx.fill(x+w-1,y+1,x+w,y+h-1,edge);
  if(on)ctx.fill(x+3,y+4,x+5,y+h-4,hover?ICE:0xFF678C95);
  var tr=MinecraftClient.getInstance().textRenderer;
  String label=tr.trimToWidth(button.getMessage().getString(),Math.max(8,w-16));
  ctx.drawCenteredTextWithShadow(tr,label,x+w/2,y+(h-8)/2,!on?0xFF74818A:(hover?0xFFFFFFFF:SNOW));
 }
}
