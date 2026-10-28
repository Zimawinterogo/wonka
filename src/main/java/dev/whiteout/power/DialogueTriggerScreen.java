package dev.whiteout.power;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.text.Text;

/** One-click link to the original Director ENTER_BOX, INTERACT_ENTITY or SIGNAL triggers. */
public final class DialogueTriggerScreen extends SharpScreen {
 private final net.minecraft.client.gui.screen.Screen parent;
 private final String id;
 private final List<Entity> nearbyNpc;
 private int npcIndex=0;
 private ButtonWidget npcButton;
 private DialogueTriggers.Kind kind=DialogueTriggers.Kind.AREA;
 private boolean once=true;
 private ButtonWidget kindButton,onceButton;
 private TextFieldWidget radius;
 private String error="",success="";
 private static Text t(String key,Object... args){return Text.translatable("screen.whiteout.trigger."+key,args);}
 private static String tr(String key,Object... args){return t(key,args).getString();}
 public DialogueTriggerScreen(net.minecraft.client.gui.screen.Screen parent,String id){
  super(Text.translatable("screen.whiteout.trigger.title"));this.parent=parent;this.id=id;
  MinecraftClient mc=MinecraftClient.getInstance();
  List<Entity> found=new ArrayList<>();
  if(mc.world!=null&&mc.player!=null){
   for(Entity e:mc.world.getEntities()){
    if(e!=mc.player&&e instanceof LivingEntity&&e.isAlive()&&mc.player.squaredDistanceTo(e)<=48*48)
     found.add(e);
   }
   found.sort(Comparator.comparingDouble(mc.player::squaredDistanceTo));
  }
  nearbyNpc=List.copyOf(found.subList(0,Math.min(found.size(),20)));
  if(mc.targetedEntity!=null)for(int i=0;i<nearbyNpc.size();i++)
   if(nearbyNpc.get(i).getUuid().equals(mc.targetedEntity.getUuid())){npcIndex=i;break;}
 }
 @Override protected void init(){
  int x=width/2-161,y=height/2-92;
  kindButton=addDrawableChild(ButtonWidget.builder(Text.empty(),b->{
   kind=DialogueTriggers.Kind.values()[(kind.ordinal()+1)%DialogueTriggers.Kind.values().length];update();
  }).dimensions(x+13,y+55,296,22).build());
  onceButton=addDrawableChild(ButtonWidget.builder(Text.empty(),b->{once=!once;update();})
   .dimensions(x+13,y+85,296,22).build());
  radius=addDrawableChild(new TextFieldWidget(textRenderer,x+217,y+114,92,20,t("radius")));
  radius.setMaxLength(2);radius.setText("3");
  npcButton=addDrawableChild(ButtonWidget.builder(Text.empty(),b->{
   if(!nearbyNpc.isEmpty()){npcIndex=(npcIndex+1)%nearbyNpc.size();update();}
  }).dimensions(x+13,y+114,296,20).build());
  addDrawableChild(ButtonWidget.builder(t("bind"),b->bind())
   .dimensions(x+13,y+151,188,22).build());
  addDrawableChild(ButtonWidget.builder(t("back"),b->close())
   .dimensions(x+208,y+151,101,22).build());
  update();
 }
 private void update(){
  String label=switch(kind){case AREA->tr("area");case NPC->tr("npc");case SIGNAL->tr("signal");};
  kindButton.setMessage(t("kind",label));
  onceButton.setMessage(t("once",tr(once?"yes":"no")));
  radius.visible=kind==DialogueTriggers.Kind.AREA;
  npcButton.visible=kind==DialogueTriggers.Kind.NPC;
  npcButton.active=!nearbyNpc.isEmpty();
  String npc=nearbyNpc.isEmpty()?tr("no_npc"):nearbyNpc.get(npcIndex).getName().getString()+" ["+(npcIndex+1)+"/"+nearbyNpc.size()+"]";
  npcButton.setMessage(t("npc_choice",npc));
 }
 private void bind(){
  try {
   int r=3;
   if(kind==DialogueTriggers.Kind.AREA){
    r=Integer.parseInt(radius.getText());
    if(r<1||r>64){error=tr("radius_bounds");return;}
   }
   Entity npc=kind==DialogueTriggers.Kind.NPC&&!nearbyNpc.isEmpty()?nearbyNpc.get(npcIndex):null;
   String result=DialogueTriggers.bind(id,kind,MinecraftClient.getInstance(),r,once,npc);
   success=tr("created",result);error="";
  }catch(Exception ex){error=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();success="";}
 }
 @Override public boolean shouldPause(){return false;}
 @Override public void close(){if(client!=null)client.setScreen(parent);}
 @Override public void render(DrawContext c,int mx,int my,float dt){
  WhiteoutTheme.backdrop(c,width,height);
  int x=width/2-161,y=height/2-92;
  WhiteoutTheme.panel(c,x,y,322,209,tr("heading",id));
  String note=switch(kind){
   case AREA -> tr("area_hint");
   case NPC -> tr("npc_hint");
   case SIGNAL -> tr("signal_hint",id,id);
  };
  c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(note,296),x+13,y+37,WhiteoutTheme.MUTED);
  if(kind==DialogueTriggers.Kind.AREA)c.drawTextWithShadow(textRenderer,tr("radius_blocks"),x+116,y+121,WhiteoutTheme.MUTED);
  String message=!error.isEmpty()?error:success;
  if(!message.isEmpty())c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(message,294),x+13,y+140,error.isEmpty()?WhiteoutTheme.ICE:WhiteoutTheme.WARN);
  c.drawTextWithShadow(textRenderer,tr("storage"),x+13,y+185,WhiteoutTheme.MUTED);
  super.render(c,mx,my,dt);
 }
}
