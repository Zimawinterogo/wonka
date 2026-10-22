package dev.whiteout.power;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Per-zone old-building sound preferences; the authoritative values live in the world save. */
public final class OldBuildingSoundScreen extends SharpScreen {
 private final Screen parent;
 private final String zone;
 private boolean enabled;
 private int seconds;

 public OldBuildingSoundScreen(Screen parent,String zone){
  super(Text.translatable("screen.whiteout.old_sound.title"));
  this.parent=parent;
  this.zone=zone;
  this.enabled=EnvironmentClient.oldSoundEnabled(zone);
  this.seconds=EnvironmentClient.oldSoundInterval(zone);
 }
 private void send(){
  EnvironmentClient.setOldSoundLocal(zone,enabled,seconds);
  ClientPlayNetworking.send(new EnvironmentNetwork.SetOldSound(zone,enabled,seconds));
 }
 @Override protected void init(){
  int x=width/2-142,y=Math.max(44,height/2-82);
  addDrawableChild(ButtonWidget.builder(Text.translatable(enabled?"screen.whiteout.old_sound.on":"screen.whiteout.old_sound.off"),b->{
   enabled=!enabled;send();clearAndInit();
  }).dimensions(x,y,284,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("-10"),b->change(-10)).dimensions(x,y+31,52,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("-1"),b->change(-1)).dimensions(x+57,y+31,52,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("+1"),b->change(1)).dimensions(x+175,y+31,52,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("+10"),b->change(10)).dimensions(x+232,y+31,52,20).build());
  addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"),b->close()).dimensions(x,y+104,284,20).build());
 }
 private void change(int delta){
  int next=Math.max(8,Math.min(300,seconds+delta));
  if(next!=seconds){seconds=next;send();clearAndInit();}
 }
 @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
  super.render(ctx,mouseX,mouseY,delta);
  int cx=width/2, y=Math.max(44,height/2-82);
  ctx.drawCenteredTextWithShadow(textRenderer,getTitle(),cx,y-33,0xDFE8FF);
  ctx.drawCenteredTextWithShadow(textRenderer,Text.literal(zone),cx,y-20,0xB6C9E3);
  ctx.drawCenteredTextWithShadow(textRenderer,Text.translatable("screen.whiteout.old_sound.interval",seconds),cx,y+58,0xE7F0FF);
  ctx.drawCenteredTextWithShadow(textRenderer,Text.translatable("screen.whiteout.old_sound.hint"),cx,y+80,0xB5C8E3);
  if(!EnvironmentClient.oldSoundsGloballyEnabled())
   ctx.drawCenteredTextWithShadow(textRenderer,Text.translatable("screen.whiteout.old_sound.global_off"),cx,y+129,0xFFBFA6);
 }
 @Override public void close(){if(client!=null)client.setScreen(parent);}
 @Override public boolean shouldPause(){return false;}
}
