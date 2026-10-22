package dev.whiteout.power;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Deliberate second click for irreversible script deletion; still uses SharpScreen (no blur). */
public final class DialogueDeleteConfirmScreen extends SharpScreen {
 private final DialogueCatalogScreen parent;
 private final String id;
 private final boolean restoreBuiltin;
 private String error="";

 public DialogueDeleteConfirmScreen(DialogueCatalogScreen parent,String id,boolean restoreBuiltin){
  super(Text.translatable("screen.whiteout.dialogues.confirm_title"));
  this.parent=parent;
  this.id=id;
  this.restoreBuiltin=restoreBuiltin;
 }

 @Override protected void init(){
  int center=width/2,y=height/2+42;
  addDrawableChild(ButtonWidget.builder(Text.translatable(restoreBuiltin
   ?"screen.whiteout.dialogues.reset_confirm":"screen.whiteout.dialogues.delete_confirm"),b->remove())
   .dimensions(center-108,y,104,22).build());
  addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"),b->close())
   .dimensions(center+4,y,104,22).build());
 }

 private void remove(){
  try {
   if(!DialogueCatalog.deleteScript(id)){
    error=Text.translatable("screen.whiteout.dialogues.not_found").getString();
    return;
   }
   parent.onDeletion(id,true,"");
   if(client!=null)client.setScreen(parent);
  }catch(Exception ex){
   error=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
  }
 }

 @Override public void close(){if(client!=null)client.setScreen(parent);}
 @Override public boolean shouldPause(){return false;}
 @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
  WhiteoutTheme.backdrop(context,width,height);
  int x=width/2-136,y=height/2-64;
  WhiteoutTheme.panel(context,x,y,272,150,"");
  context.drawCenteredTextWithShadow(textRenderer,title,width/2,y+15,0xFFF9E7E5);
  context.drawCenteredTextWithShadow(textRenderer,Text.literal(id),width/2,y+39,0xFFD4E0E5);
  context.drawCenteredTextWithShadow(textRenderer,Text.translatable(restoreBuiltin
   ?"screen.whiteout.dialogues.reset_warning":"screen.whiteout.dialogues.delete_warning"),width/2,y+58,0xFFFFC6B8);
  if(error.isEmpty()){
   context.drawCenteredTextWithShadow(textRenderer,Text.translatable("screen.whiteout.dialogues.delete_backup"),width/2,y+76,0xFFB8CAD0);
  }else{
   String msg=error.length()>47?error.substring(0,46)+"…":error;
   context.drawCenteredTextWithShadow(textRenderer,Text.literal(msg),width/2,y+76,0xFFFFB0AB);
  }
  super.render(context,mouseX,mouseY,delta);
 }
}
