package dev.whiteout.client;

import dev.whiteout.MusicCatalog;
import dev.whiteout.power.SharpScreen;
import dev.whiteout.power.WhiteoutTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Read-only soundtrack catalog; no music placeholders or external pack import. */
public final class MusicLibraryScreen extends SharpScreen {
 private final Screen parent;
 public MusicLibraryScreen(Screen parent) {
  super(Text.translatable("screen.whiteout.music.title"));
  this.parent = parent;
 }
 @Override protected void init() {
  addDrawableChild(ButtonWidget.builder(Text.translatable("screen.whiteout.music.back"), b -> close())
   .dimensions(width/2-65, Math.min(height-29,height/2+138),130,20).build());
 }
 @Override public boolean shouldPause() { return false; }
 @Override public void close() { if(client!=null) client.setScreen(parent); }
 @Override public void render(DrawContext c,int mouseX,int mouseY,float delta) {
  WhiteoutTheme.backdrop(c,width,height);
  int w=Math.min(480,width-20),h=Math.min(325,height-12),x=(width-w)/2,y=(height-h)/2;
  WhiteoutTheme.panel(c,x,y,w,h,Text.translatable("screen.whiteout.music.heading").getString());
  int itemY=y+45;
  for(int i=1;i<=7;i++) {
   String str=i+".  "+MusicCatalog.name(i);
   str=textRenderer.trimToWidth(str,w-40);
   c.drawTextWithShadow(textRenderer,Text.literal(str),x+20,itemY+(i-1)*28,WhiteoutTheme.SNOW);
  }
  c.drawTextWithShadow(textRenderer,Text.translatable("screen.whiteout.music.count"),x+20,y+h-45,WhiteoutTheme.MUTED);
  super.render(c,mouseX,mouseY,delta);
 }
}
