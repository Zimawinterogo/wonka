package dev.whiteout.power;

import dev.whiteout.client.DialogueManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.List;

/** Creates a script in the existing registry and opens the original Whiteout line editor. */
public final class DialogueCreateScreen extends SharpScreen {
 private final DialogueCatalogScreen parent;
 private TextFieldWidget id,speaker;
 private String error="";
 private static Text t(String key,Object... args){return Text.translatable("screen.whiteout.create_dialogue."+key,args);}
 private static String tr(String key,Object... args){return t(key,args).getString();}
 public DialogueCreateScreen(DialogueCatalogScreen parent){super(Text.translatable("screen.whiteout.create_dialogue.title"));this.parent=parent;}
 @Override protected void init(){
  int x=width/2-144,y=height/2-65;
  id=addDrawableChild(new TextFieldWidget(textRenderer,x+12,y+43,264,20,Text.literal("ID")));
  id.setMaxLength(64);id.setText("scene_");
  speaker=addDrawableChild(new TextFieldWidget(textRenderer,x+12,y+87,264,20,t("speaker")));
  speaker.setMaxLength(48);speaker.setText("Я");
  addDrawableChild(ButtonWidget.builder(t("create"),b->create())
    .dimensions(x+12,y+125,179,22).build());
  addDrawableChild(ButtonWidget.builder(t("back"),b->close())
    .dimensions(x+198,y+125,78,22).build());
  setInitialFocus(id);
 }
 private void create(){
  String name=id.getText().trim(),who=speaker.getText().trim();
  if(!name.matches("[a-z0-9_:-]{1,64}")){error=tr("invalid_id");return;}
  if(who.isBlank()){error=tr("missing_speaker");return;}
  if(DialogueManager.findById(name)!=null||DialogueCatalog.isBuiltin(name)){error=tr("duplicate");return;}
  try {
   var line=new DialogueManager.Line(who,tr("new_line"),who,0xFFE7F0F5,who.equalsIgnoreCase("Я")?"player":"npc","",List.of(),null);
   var script=new DialogueManager.Script(name,List.of(),List.of(),List.of(line));
   if(!DialogueManager.saveScript(script)){error=tr("save_failed");return;}
   parent.openNewlyCreated(name);
  }catch(Exception ex){error=tr("error",ex.getClass().getSimpleName());}
 }
 @Override public boolean shouldPause(){return false;}
 @Override public void close(){if(client!=null)client.setScreen(parent);}
 @Override public void render(DrawContext c,int mx,int my,float dt){
  WhiteoutTheme.backdrop(c,width,height);
  int x=width/2-144,y=height/2-65;
  WhiteoutTheme.panel(c,x,y,288,175,tr("heading"));
  c.drawTextWithShadow(textRenderer,tr("script_id"),x+12,y+32,WhiteoutTheme.MUTED);
  c.drawTextWithShadow(textRenderer,tr("speaker_name"),x+12,y+76,WhiteoutTheme.MUTED);
  if(!error.isEmpty())c.drawTextWithShadow(textRenderer,error,x+12,y+113,WhiteoutTheme.WARN);
  super.render(c,mx,my,dt);
 }
}
