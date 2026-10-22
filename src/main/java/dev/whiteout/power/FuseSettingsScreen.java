package dev.whiteout.power;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
public final class FuseSettingsScreen extends SharpScreen {
 private PowerNetwork.FuseSettings data;
 public FuseSettingsScreen(PowerNetwork.FuseSettings data){super(Text.translatable("screen.whiteout.fuse_settings"));this.data=data;}
 public void update(PowerNetwork.FuseSettings data){this.data=data;clearAndInit();}
 private void change(int delta){ClientPlayNetworking.send(new PowerNetwork.Action(data.pos(),100+Math.max(0,Math.min(100,data.percent()+delta))));}
 @Override protected void init(){
  int size=Math.min(240,Math.max(120,width-24)),x=(width-size)/2,y=height/2;
  addDrawableChild(ButtonWidget.builder(Text.literal("− 5%"),b->change(-5)).dimensions(x,y,size/2-3,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("+ 5%"),b->change(5)).dimensions(x+size/2+3,y,size/2-3,20).build());
  addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"),b->close()).dimensions(x,y+26,size,20).build());
 }
 @Override public void render(DrawContext c,int mx,int my,float delta){
  c.fill(0,0,width,height,0xDD101A23);
  c.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-24,0xDCEAFF);
  super.render(c,mx,my,delta);
 }
 @Override public boolean shouldPause(){return false;}
}
