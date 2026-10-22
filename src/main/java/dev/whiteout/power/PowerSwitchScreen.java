package dev.whiteout.power;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Shift+right-click in Creative: choose network and individual light fixtures. */
public final class PowerSwitchScreen extends SharpScreen {
 private PowerSwitches.Snapshot data;
 private int page;
 public PowerSwitchScreen(PowerSwitches.Snapshot initial){super(Text.literal("Выключатель Whiteout"));data=initial;}
 public void update(PowerSwitches.Snapshot packet){data=packet;clearAndInit();}
 private static List<String> lines(String value){return value.isBlank()?List.of():Arrays.asList(value.split("\\n"));}
 private void send(String action,String value){ClientPlayNetworking.send(new PowerSwitches.Edit(data.pos(),action,value));}
 @Override protected void init(){
  int cx=width/2,top=Math.max(24,height/2-102);
  List<String> networks=lines(data.networks());
  addDrawableChild(ButtonWidget.builder(Text.literal("Сеть: "+data.network()+"  ▸"),b->{
   int at=networks.indexOf(data.network());if(!networks.isEmpty())send("network",networks.get((at+1)%networks.size()));
  }).dimensions(cx-142,top,284,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal(data.all()?"Вся сеть: ДА":"Выбранные лампы: ДА"),b->send("all","")).dimensions(cx-142,top+24,190,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal(data.on()?"ВКЛ":"ВЫКЛ"),b->send("state","")).dimensions(cx+52,top+24,90,20).build());
  List<String> lamps=lines(data.lamps());List<String> selected=lines(data.selected());
  int pages=Math.max(1,(lamps.size()+5)/6);page=Math.min(page,pages-1);
  for(int index=page*6;index<Math.min(lamps.size(),page*6+6);index++){
   String lamp=lamps.get(index),name=(selected.contains(lamp)?"[✓] ":"[  ] ")+lamp;
   addDrawableChild(ButtonWidget.builder(Text.literal(name),b->send("lamp",lamp)).dimensions(cx-142,top+50+(index%6)*22,284,20).build());
  }
  addDrawableChild(ButtonWidget.builder(Text.literal("<"),b->{page=Math.max(0,page-1);clearAndInit();}).dimensions(cx-142,top+184,42,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal((page+1)+" / "+pages),b->{}).dimensions(cx-94,top+184,85,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal(">"),b->{page=Math.min(pages-1,page+1);clearAndInit();}).dimensions(cx-3,top+184,42,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("Готово"),b->close()).dimensions(cx+45,top+184,97,20).build());
 }
 @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
  super.render(context,mouseX,mouseY,delta);
  context.drawCenteredTextWithShadow(textRenderer,"Выбор ламп: "+(data.all()?"все светильники сети":"только отмеченные"),width/2,Math.max(10,height/2-118),0xDCEAFF);
 }
 @Override public boolean shouldPause(){return false;}
}
