package dev.whiteout.power;
import dev.whiteout.Zone;
import dev.whiteout.client.ZoneScreen;
import dev.whiteout.power.mixin.ZoneScreenAccess;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ZoneSettingsScreen extends SharpScreen {
 private final Screen parent;private final String id,name;private int mask=15;
 public ZoneSettingsScreen(Screen parent,Zone zone){super(Text.literal("Настройки зоны"));this.parent=parent;id=zone.id;name=zone.name;}
 public static void initialize(){EditorClient.toolbars.add(screen->{
  if(screen instanceof ZoneScreen zs)Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Копировать…"),b->{
   Zone selected=((ZoneScreenAccess)(Object)zs).whiteout$getDraft();
   if(selected!=null)net.minecraft.client.MinecraftClient.getInstance().setScreen(new ZoneSettingsScreen(screen,selected));
  }).dimensions(EditorClient.toolbarX(screen)+EditorClient.toolbarWidth(screen)+8,EditorClient.toolbarY(screen),EditorClient.toolbarWidth(screen),20).build());
 });}
 @Override protected void init(){
  int x=width/2-140,y=Math.max(36,height/2-110);
  addDrawableChild(ButtonWidget.builder(Text.literal("Копировать настройки"),b->{EditorClient.send("copy",id,0);client.setScreen(parent);}).dimensions(x,y,280,20).build());
  String[] groups={"Погода","Звуки и жуть","Холод","События","Атмосфера здания (оператор)"};
  for(int i=0;i<groups.length;i++){
   final int bit=1<<i;final String label=groups[i];
   addDrawableChild(ButtonWidget.builder(Text.literal(((mask&bit)!=0?"✓ ":"— ")+label),b->{mask^=bit;b.setMessage(Text.literal(((mask&bit)!=0?"✓ ":"— ")+label));}).dimensions(x,y+28+i*23,280,20).build());
  }
  addDrawableChild(ButtonWidget.builder(Text.literal("Применить скопированные…"),b->{
   if(mask==0)return;
   client.setScreen(new ConfirmScreen(ok->{
    client.setScreen(ok?parent:this);
    if(ok)EditorClient.send("paste",id,mask);
   },Text.literal("Заменить настройки зоны «"+name+"»?"),Text.literal("Выбранные группы будут сохранены одним действием Undo. Несохранённый черновик не применяется.")));
  }).dimensions(x,y+151,280,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("Назад"),b->close()).dimensions(x,y+177,280,20).build());
 }
 @Override public void close(){client.setScreen(parent);}
 @Override public void render(DrawContext g,int mx,int my,float delta){
  g.fill(0,0,width,height,0xCC101821);
  super.render(g,mx,my,delta);g.drawCenteredTextWithShadow(textRenderer,"Настройки: "+name,width/2,12,0xFFFFFF);
  g.drawCenteredTextWithShadow(textRenderer,"Копируется сохранённая серверная версия",width/2,25,0xADB9C7);
 }
}
