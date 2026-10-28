package dev.whiteout.power;

import dev.whiteout.StoryRules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.function.Consumer;
import static dev.whiteout.power.DialogueRouteClient.text;

public final class DialogueRouteVariantScreen extends SharpScreen {
 private final DialogueRouteEditScreen parent;private final int index;
 private DialogueRoute.Type type=DialogueRoute.Type.NETWORK;private StoryRules.Operator operator=StoryRules.Operator.EQ;
 private String dialogue="",target="main",dimension="minecraft:overworld",position="0 64 0",value="true",priority="0",error="";
 private boolean expected=true;
 public DialogueRouteVariantScreen(DialogueRouteEditScreen parent,int index,DialogueRoute.Variant variant){
  super(text("variant"));this.parent=parent;this.index=index;
  if(variant!=null){var c=variant.condition();type=c.type();operator=c.operator()==null?StoryRules.Operator.EQ:c.operator();dialogue=variant.dialogue();target=c.target();dimension=c.dimension();position=c.x()+" "+c.y()+" "+c.z();value=c.value();expected=c.expected();priority=""+variant.priority();}
 }
 private void field(int y,String label,String initial,int max,Consumer<String> setter){
  var field=addDrawableChild(new TextFieldWidget(textRenderer,100,y,width-110,20,text(label)));field.setMaxLength(max);field.setText(initial);field.setChangedListener(setter);
 }
 @Override protected void init(){
  int w=(width-23)/2;
  addDrawableChild(ButtonWidget.builder(text("type_"+type.name().toLowerCase(java.util.Locale.ROOT)),b->{type=DialogueRoute.Type.values()[(type.ordinal()+1)%DialogueRoute.Type.values().length];clearAndInit();}).dimensions(10,32,w,20).build());
  addDrawableChild(ButtonWidget.builder(text(expected?"expected_yes":"expected_no"),b->{expected=!expected;clearAndInit();}).dimensions(13+w,32,w,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal(dialogue.isEmpty()?text("choose").getString():dialogue),b->client.setScreen(new DialogueRoutePickerScreen(this,s->dialogue=s))).dimensions(100,57,width-110,20).build());
  if(type==DialogueRoute.Type.GENERATOR){
   field(82,"dimension",dimension,128,s->dimension=s);field(107,"position",position,64,s->position=s);
   addDrawableChild(ButtonWidget.builder(text("here"),b->{if(client.player!=null){var p=client.player.getBlockPos();dimension=client.world.getRegistryKey().getValue().toString();position=p.getX()+" "+p.getY()+" "+p.getZ();clearAndInit();}}).dimensions(100,132,width-110,20).build());
  }else if(type!=DialogueRoute.Type.BLIZZARD){
   field(82,"target",target,128,s->target=s);
   if(type==DialogueRoute.Type.STORY){
    addDrawableChild(ButtonWidget.builder(Text.literal(StoryRules.symbol(operator)),b->{operator=StoryRules.Operator.values()[(operator.ordinal()+1)%StoryRules.Operator.values().length];clearAndInit();}).dimensions(100,107,width-110,20).build());
    field(132,"value",value,64,s->value=s);
   }
  }
  field(157,"priority",priority,6,s->priority=s);
  addDrawableChild(ButtonWidget.builder(text("apply"),b->apply()).dimensions(10,height-30,(width-25)/2,20).build());
  addDrawableChild(ButtonWidget.builder(text("back"),b->close()).dimensions((width+5)/2,height-30,(width-25)/2,20).build());
 }
 private void apply(){
  try{
   int x=0,y=0,z=0;
   if(type==DialogueRoute.Type.GENERATOR){String[] p=position.trim().split("\\s+");if(p.length!=3)throw new IllegalArgumentException();x=Integer.parseInt(p[0]);y=Integer.parseInt(p[1]);z=Integer.parseInt(p[2]);}
   var c=new DialogueRoute.Condition(type,target,dimension,x,y,z,expected,value,operator);c.validate();
   int rank=Integer.parseInt(priority);if(rank< -10000||rank>10000)throw new IllegalArgumentException();
   if(!DialogueRouteClient.exists(dialogue)){error="missing_dialogue";return;}
   parent.variant(index,new DialogueRoute.Variant(dialogue,rank,c));client.setScreen(parent);
  }catch(RuntimeException ex){error="invalid";}
 }
 @Override public boolean shouldPause(){return false;}
 @Override public void close(){client.setScreen(parent);}
 @Override public void render(DrawContext c,int mx,int my,float dt){
  WhiteoutTheme.backdrop(c,width,height);c.drawCenteredTextWithShadow(textRenderer,title,width/2,12,WhiteoutTheme.SNOW);
  label(c,"dialogue",63);label(c,"priority",163);
  if(type==DialogueRoute.Type.GENERATOR){label(c,"dimension",88);label(c,"position",113);}
  else if(type!=DialogueRoute.Type.BLIZZARD){label(c,"target",88);if(type==DialogueRoute.Type.STORY){label(c,"operator",113);label(c,"value",138);}}
  if(!error.isEmpty())c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(text(error).getString(),width-20),10,height-45,WhiteoutTheme.WARN);
  super.render(c,mx,my,dt);
 }
 private void label(DrawContext c,String key,int y){c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(text(key).getString(),85),10,y,WhiteoutTheme.MUTED);}
}
