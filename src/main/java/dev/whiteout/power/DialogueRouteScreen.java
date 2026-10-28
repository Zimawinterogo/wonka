package dev.whiteout.power;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import static dev.whiteout.power.DialogueRouteClient.text;

public final class DialogueRouteScreen extends SharpScreen {
 private final Screen parent;
 private DialogueRouteNetwork.EditorData data;
 private int offset,selected;
 private String error="";
 public DialogueRouteScreen(Screen parent){super(text("title"));this.parent=parent;}
 public void update(DialogueRouteNetwork.EditorData value){data=value;error=!value.error().isEmpty()?value.error():value.invalid()?"corrupt":"";clearAndInit();}
 long revision(){return data==null?0:data.data().revision();}
 private List<DialogueRoute> routes(){return data==null?List.of():data.data().routes();}
 private DialogueRoute chosen(){return routes().isEmpty()?null:routes().get(selected);}
 @Override protected void init(){
  int rows=Math.max(1,(height-123)/23),w=width-20;
  selected=Math.max(0,Math.min(selected,routes().size()-1));offset=Math.max(0,Math.min(offset,Math.max(0,routes().size()-rows)));
  for(int i=offset;i<Math.min(routes().size(),offset+rows);i++){
   int index=i;addDrawableChild(ButtonWidget.builder(Text.literal((i==selected?"● ":"")+routes().get(i).id()),b->{selected=index;clearAndInit();}).dimensions(10,42+(i-offset)*23,w,21).build());
  }
  String[] actions={"create","edit","delete","test","bind","copy","refresh","back"};int bw=w/4;
  for(int i=0;i<actions.length;i++){
   String action=actions[i];var button=addDrawableChild(ButtonWidget.builder(text(action),b->action(action)).dimensions(10+(i%4)*bw,height-58+(i/4)*24,bw-3,21).build());
   if(Set.of("edit","delete","test","bind","copy").contains(action))button.active=chosen()!=null&&data!=null&&!data.invalid();
   if(action.equals("create"))button.active=data!=null&&!data.invalid()&&routes().size()<DialogueRoute.MAX_ROUTES;
  }
 }
 private void action(String action){
  switch(action){
   case "create"->client.setScreen(new DialogueRouteEditScreen(this,null));
   case "edit"->client.setScreen(new DialogueRouteEditScreen(this,chosen()));
   case "delete"->{String id=chosen().id();long revision=revision();client.setScreen(new ConfirmScreen(ok->{client.setScreen(this);if(ok)DialogueRouteClient.edit("delete",id,revision);},text("delete"),text("confirm_delete",id)));}
   case "test"->{String ref=DialogueRoute.PREFIX+chosen().id();client.setScreen(null);dev.whiteout.client.DialogueManager.openDirector(ref,140);}
   case "bind"->client.setScreen(new DialogueTriggerScreen(this,DialogueRoute.PREFIX+chosen().id()));
   case "copy"->client.keyboard.setClipboard(DialogueRoute.PREFIX+chosen().id());
   case "refresh"->DialogueRouteClient.edit("list","",revision());
   case "back"->close();
  }
 }
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){offset-=(int)Math.signum(vertical)*3;clearAndInit();return true;}
 @Override public boolean shouldPause(){return false;}
 @Override public void close(){client.setScreen(parent);}
 @Override public void render(DrawContext c,int mx,int my,float dt){
  WhiteoutTheme.backdrop(c,width,height);c.drawCenteredTextWithShadow(textRenderer,title,width/2,12,WhiteoutTheme.SNOW);
  c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(text("storage").getString(),width-20),10,28,WhiteoutTheme.MUTED);
  String status=data==null?text("loading").getString():error.isEmpty()?text("director_hint").getString():text(error).getString();
  c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,width-20),10,height-73,error.isEmpty()?WhiteoutTheme.MUTED:WhiteoutTheme.WARN);
  super.render(c,mx,my,dt);
 }
}
