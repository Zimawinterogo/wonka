package dev.whiteout.power.lobby;

import dev.whiteout.power.SharpScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class BoardScreen extends SharpScreen {
 private final BoardNetwork.View data;
 private boolean editing;
 private final TextFieldWidget[] fields=new TextFieldWidget[5];
 private static final String[] NAMES={"title","description","author","duration","note"};
 private static final int[] LIMIT={80,320,64,48,160};
 private static Text tr(String key){return Text.translatable("board.whiteout."+key);}
 private BoardScreen(BoardNetwork.View data){super(tr("board"));this.data=data;}
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(BoardNetwork.View.ID,(msg,ctx)->ctx.client().execute(()->ctx.client().setScreen(new BoardScreen(msg))));
 }
 private int w(){return Math.min(width-16,374);}
 private int left(){return (width-w())/2;}
 private int top(){return Math.max(4,(height-200)/2);}
 private String[] values(){var c=data.card();return c==null?new String[]{"","","","",""}:new String[]{c.title(),c.description(),c.author(),c.duration(),c.note()};}
 private ButtonWidget button(Text text,int x,int y,int width,Runnable action){
  return addDrawableChild(ButtonWidget.builder(text,b->action.run()).dimensions(x,y,width,20).build());
 }
 private void send(LobbyState.Card card){ClientPlayNetworking.send(new BoardNetwork.Change(data.token(),data.revision(),card));close();}
 @Override protected void init(){
  int x=left(),y=top(),w=w();
  if(editing){
   var values=values();for(int i=0;i<5;i++){
    var field=new TextFieldWidget(textRenderer,x+w/3,y+26+i*26,w-w/3-14,20,tr(NAMES[i]));
    field.setMaxLength(LIMIT[i]);field.setText(values[i]);fields[i]=addDrawableChild(field);
   }
   var save=button(tr("save"),x+w/2-46,y+167,92,()->send(new LobbyState.Card(fields[0].getText(),fields[1].getText(),fields[2].getText(),fields[3].getText(),fields[4].getText())));
   save.active=!fields[0].getText().isBlank();fields[0].setChangedListener(v->save.active=!v.isBlank());setInitialFocus(fields[0]);return;
  }
  if(data.editor()&&!data.started())button(Text.literal("⚙"),x+w-34,y+6,26,()->{editing=true;clearAndInit();}).setTooltip(Tooltip.of(tr("edit")));
  var start=button(tr(data.started()?"started":"start"),x+w/2-81,y+167,162,()->send(null));start.active=data.card()!=null&&!data.started();
 }
 @Override public void render(DrawContext d,int mx,int my,float dt){
  int x=left(),y=top(),w=w();
  d.fill(0,0,width,height,0xb0181512);
  d.drawTexture(Identifier.of("whiteout","textures/block/rotten_spruce_planks.png"),x,y,0,0,w,196,32,32);
  d.fill(x+8,y+8,x+w-8,y+158,0xffd8c9a6);
  d.fill(x+14,y+11,x+17,y+14,0xff76452f);
  if(editing){
   d.drawText(textRenderer,tr("edit"),x+24,y+12,0x392e23,false);
   for(int i=0;i<5;i++)d.drawText(textRenderer,tr(NAMES[i]),x+14,y+31+i*26,0x392e23,false);
  }else{
   var c=data.card();
   d.drawText(textRenderer,c==null?tr("board"):Text.literal(textRenderer.trimToWidth(c.title(),w-74)),x+24,y+16,0x382c20,false);
   int row=y+38;
   if(c==null)d.drawTextWrapped(textRenderer,tr("empty"),x+22,row,w-44,0x504336);
   else for(String line:new String[]{c.description(),c.author(),c.duration(),c.note()}){
    if(line.isBlank())continue;
    for(var part:textRenderer.wrapLines(Text.literal(line),w-44)){if(row>y+144)break;d.drawText(textRenderer,part,x+22,row,0x504336,false);row+=11;}row+=3;
   }
  }
  super.render(d,mx,my,dt);
 }
 @Override public boolean shouldPause(){return false;}
}
