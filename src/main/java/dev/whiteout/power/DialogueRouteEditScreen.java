package dev.whiteout.power;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import static dev.whiteout.power.DialogueRouteClient.text;

public final class DialogueRouteEditScreen extends SharpScreen {
 private final DialogueRouteScreen parent;private final long revision;private final boolean existing;
 private String id,fallback,error="";private final List<DialogueRoute.Variant> variants=new ArrayList<>();
 private int selected,offset,waitingTicks;private boolean saving;
 public DialogueRouteEditScreen(DialogueRouteScreen parent,DialogueRoute route){
  super(text("edit"));this.parent=parent;revision=parent.revision();existing=route!=null;
  id=route==null?"":route.id();fallback=route==null?"":route.fallback();if(route!=null)variants.addAll(route.variants());
 }
 void variant(int index,DialogueRoute.Variant value){if(index<0)variants.add(value);else variants.set(index,value);}
 void result(DialogueRouteNetwork.EditorData data){
  parent.update(data);if(!saving)return;saving=false;
  if(data.error().isEmpty()&&!data.invalid())client.setScreen(parent);else{error=data.invalid()?"corrupt":data.error();clearAndInit();}
 }
 @Override protected void init(){
  var name=addDrawableChild(new TextFieldWidget(textRenderer,100,32,width-110,20,Text.literal("ID")));name.setMaxLength(64);name.setText(id);name.setChangedListener(v->id=v);name.setEditable(!existing);
  addDrawableChild(ButtonWidget.builder(Text.literal(fallback.isEmpty()?text("choose").getString():fallback),b->client.setScreen(new DialogueRoutePickerScreen(this,s->fallback=s))).dimensions(100,57,width-110,20).build());
  int rows=Math.max(1,(height-169)/23);selected=Math.max(0,Math.min(selected,variants.size()-1));offset=Math.max(0,Math.min(offset,Math.max(0,variants.size()-rows)));
  for(int i=offset;i<Math.min(variants.size(),offset+rows);i++){
   int index=i;var v=variants.get(i);String label=(i==selected?"● ":"")+(i+1)+". ["+v.priority()+"] "+v.dialogue();
   addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,width-30)),b->{selected=index;client.setScreen(new DialogueRouteVariantScreen(this,index,v));}).dimensions(10,85+(i-offset)*23,width-20,21).build());
  }
  int w=(width-20)/4;String[] ops={"add","up","down","remove"};
  for(int i=0;i<4;i++){String op=ops[i];var button=addDrawableChild(ButtonWidget.builder(text(op),b->change(op)).dimensions(10+i*w,height-78,w-3,20).build());button.active=op.equals("add")?variants.size()<DialogueRoute.MAX_VARIANTS:!variants.isEmpty();}
  addDrawableChild(ButtonWidget.builder(text("save"),b->save()).dimensions(10,height-29,(width-25)/2,20).build()).active=!saving;
  addDrawableChild(ButtonWidget.builder(text("back"),b->close()).dimensions((width+5)/2,height-29,(width-25)/2,20).build()).active=!saving;
 }
 private void change(String op){
  switch(op){
   case "add"->client.setScreen(new DialogueRouteVariantScreen(this,-1,null));
   case "remove"->{variants.remove(selected);clearAndInit();}
   case "up","down"->{int next=selected+(op.equals("up")?-1:1);if(next>=0&&next<variants.size()){Collections.swap(variants,selected,next);selected=next;}clearAndInit();}
  }
 }
 private void save(){
  try{
   var route=new DialogueRoute(id,fallback,variants);route.validate();
   if(!DialogueRouteClient.exists(fallback)||variants.stream().anyMatch(v->!DialogueRouteClient.exists(v.dialogue()))){error="missing_dialogue";return;}
   saving=true;waitingTicks=0;DialogueRouteClient.save(route,revision,!existing);clearAndInit();
  }catch(RuntimeException ex){saving=false;error="invalid";}
 }
 @Override public void tick(){if(saving&&++waitingTicks>100){saving=false;error="save_timeout";clearAndInit();}}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){offset-=(int)Math.signum(vertical);clearAndInit();return true;}
 @Override public void close(){if(!saving)client.setScreen(parent);}
 @Override public boolean shouldPause(){return false;}
 @Override public void render(DrawContext c,int mx,int my,float dt){
  WhiteoutTheme.backdrop(c,width,height);c.drawCenteredTextWithShadow(textRenderer,title,width/2,12,WhiteoutTheme.SNOW);
  c.drawTextWithShadow(textRenderer,"ID",10,38,WhiteoutTheme.MUTED);c.drawTextWithShadow(textRenderer,text("fallback"),10,63,WhiteoutTheme.MUTED);
  c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(text(error.isEmpty()?"priority_hint":error).getString(),width-20),10,height-48,error.isEmpty()?WhiteoutTheme.MUTED:WhiteoutTheme.WARN);
  super.render(c,mx,my,dt);
 }
}
