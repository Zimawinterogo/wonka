package dev.whiteout.power;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import static dev.whiteout.power.DialogueRouteClient.text;

/** A reference picker over DialogueCatalog, not a second registry or script editor. */
public final class DialogueRoutePickerScreen extends SharpScreen {
 private final Screen parent;private final Consumer<String> choose;
 private String query="";private int page;
 public DialogueRoutePickerScreen(Screen parent,Consumer<String> choose){super(text("dialogue"));this.parent=parent;this.choose=choose;}
 @Override protected void init(){
  var search=addDrawableChild(new TextFieldWidget(textRenderer,10,32,width-20,20,text("search")));search.setMaxLength(128);search.setText(query);
  search.setChangedListener(v->{query=v;page=0;});
  var list=DialogueCatalog.entries().stream().filter(e->e.matches(query.toLowerCase(Locale.ROOT))).toList();int rows=Math.max(1,(height-100)/23);
  page=Math.max(0,Math.min(page,Math.max(0,(list.size()-1)/rows)));
  for(int i=page*rows;i<Math.min(list.size(),(page+1)*rows);i++){
   String id=list.get(i).id();addDrawableChild(ButtonWidget.builder(Text.literal(id),b->{choose.accept(id);client.setScreen(parent);}).dimensions(10,60+(i%rows)*23,width-20,21).build());
  }
  addDrawableChild(ButtonWidget.builder(text("search"),b->clearAndInit()).dimensions(10,height-30,80,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("◀"),b->{page--;clearAndInit();}).dimensions(94,height-30,30,20).build()).active=page>0;
  addDrawableChild(ButtonWidget.builder(Text.literal("▶"),b->{page++;clearAndInit();}).dimensions(128,height-30,30,20).build()).active=(page+1)*rows<list.size();
  addDrawableChild(ButtonWidget.builder(text("back"),b->close()).dimensions(width-90,height-30,80,20).build());setInitialFocus(search);
 }
 @Override public void close(){client.setScreen(parent);}
 @Override public boolean shouldPause(){return false;}
 @Override public void render(DrawContext c,int mx,int my,float dt){WhiteoutTheme.backdrop(c,width,height);c.drawCenteredTextWithShadow(textRenderer,title,width/2,12,WhiteoutTheme.SNOW);super.render(c,mx,my,dt);}
}
