package dev.whiteout.power;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Catalog only: actual script editing is delegated to Whiteout's pre-existing StoryDialogueEditorScreen. */
public final class DialogueCatalogScreen extends SharpScreen {
 private final List<ButtonWidget> scriptButtons=new ArrayList<>();
 private List<DialogueCatalog.Entry> items=List.of();
 private TextFieldWidget search;
 private ButtonWidget prev,next,edit,delete,reload,create,trigger;
 private String query="",selected="",error="",notice="";
 private int page=0,rows=7,panelLeft,panelTop,panelWidth,panelHeight;

 public DialogueCatalogScreen(){super(Text.translatable("screen.whiteout.dialogues.title"));}

 @Override protected void init(){
  scriptButtons.clear();
  panelWidth=Math.min(622,width-24);
  panelHeight=Math.min(472,height-22);
  panelLeft=(width-panelWidth)/2;
  panelTop=(height-panelHeight)/2;
  int x=panelLeft+16,y=panelTop+39,w=panelWidth-32;
  rows=Math.max(2,Math.min(10,(panelHeight-178)/25));
  search=new TextFieldWidget(textRenderer,x,y,w-100,20,Text.translatable("screen.whiteout.dialogues.search"));
  search.setMaxLength(120);
  search.setText(query);
  search.setChangedListener(value->{query=value;page=0;updateRows();});
  addDrawableChild(search);
  reload=addDrawableChild(ButtonWidget.builder(Text.translatable("screen.whiteout.dialogues.reload"),b->{
   DialogueManagerReload.reload();reloadItems();
  }).dimensions(x+w-92,y,92,20).build());
  for(int i=0;i<rows;i++){
   final int row=i;
   ButtonWidget button=ButtonWidget.builder(Text.empty(),b->selectRow(row))
    .dimensions(x,y+29+i*25,w,22).build();
   scriptButtons.add(addDrawableChild(button));
  }
  int navY=panelTop+panelHeight-84;
  prev=addDrawableChild(ButtonWidget.builder(Text.literal("◀"),b->{page=Math.max(0,page-1);updateRows();})
   .dimensions(x,navY,34,20).build());
  next=addDrawableChild(ButtonWidget.builder(Text.literal("▶"),b->{page++;updateRows();})
   .dimensions(x+w-34,navY,34,20).build());
  int gap=4;
  int createW=Math.max(64,w/6),triggerW=Math.max(68,w/6),delW=Math.max(60,w/6),doneW=Math.max(64,w/6);
  int editW=w-(createW+triggerW+delW+doneW+4*gap);
  int bx=x,by=panelTop+panelHeight-34;
  create=addDrawableChild(ButtonWidget.builder(Text.translatable("screen.whiteout.dialogues.create"),b->createNew())
   .dimensions(bx,by,createW,22).build());bx+=createW+gap;
  trigger=addDrawableChild(ButtonWidget.builder(Text.translatable("screen.whiteout.dialogues.trigger"),b->bindSelected())
   .dimensions(bx,by,triggerW,22).build());bx+=triggerW+gap;
  edit=addDrawableChild(ButtonWidget.builder(Text.translatable("screen.whiteout.dialogues.edit"),b->editSelected())
   .dimensions(bx,by,editW,22).build());bx+=editW+gap;
  delete=addDrawableChild(ButtonWidget.builder(Text.translatable("screen.whiteout.dialogues.delete"),b->confirmDelete())
   .dimensions(bx,by,delW,22).build());bx+=delW+gap;
  addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"),b->close())
   .dimensions(bx,by,doneW,22).build());
  reloadItems();
  setInitialFocus(search);
 }
 private void reloadItems(){
  try {items=DialogueCatalog.entries();error="";if(items.stream().noneMatch(e->e.id().equals(selected)))selected=items.isEmpty()?"":items.get(0).id();}
  catch(Exception ex){items=List.of();error=ex.getMessage()==null?Text.translatable("screen.whiteout.dialogues.registry_error").getString():ex.getMessage();}
  updateRows();
 }
 private List<DialogueCatalog.Entry> filtered(){
  String needle=query.strip().toLowerCase(Locale.ROOT);
  return items.stream().filter(e->e.matches(needle)).toList();
 }
 private void updateRows(){
  if(scriptButtons.isEmpty())return;
  List<DialogueCatalog.Entry> list=filtered();
  int pages=Math.max(1,(list.size()+rows-1)/rows);
  page=Math.max(0,Math.min(page,pages-1));
  for(int i=0;i<scriptButtons.size();i++){
   int index=page*rows+i;
   ButtonWidget b=scriptButtons.get(i);
   b.visible=index<list.size();
   if(!b.visible)continue;
   DialogueCatalog.Entry entry=list.get(index);
   String marker=entry.id().equals(selected)?"● ":"  ";
   String title=marker+entry.id()+(entry.bundled()?Text.translatable("screen.whiteout.dialogues.bundled").getString():"")+"  ("+entry.lines().size()+")";
   b.setMessage(Text.literal(truncate(title,Math.max(15,(panelWidth-70)/6))));
  }
  prev.active=page>0;
  next.active=page+1<pages;
  DialogueCatalog.Entry chosen=items.stream().filter(e->e.id().equals(selected)).findFirst().orElse(null);
  edit.active=chosen!=null;
  trigger.active=chosen!=null;
  delete.active=chosen!=null&&!chosen.bundled();
  delete.setMessage(Text.translatable(chosen!=null&&DialogueCatalog.isBuiltin(chosen.id())&&!chosen.bundled()
   ?"screen.whiteout.dialogues.reset":"screen.whiteout.dialogues.delete"));
 }
 private void selectRow(int row){
  List<DialogueCatalog.Entry> list=filtered();int i=page*rows+row;
  if(i<list.size()){selected=list.get(i).id();updateRows();}
 }
 private void createNew(){if(client!=null)client.setScreen(new DialogueCreateScreen(this));}
 private void bindSelected(){
  if(selected.isBlank())return;
  if(client!=null)client.setScreen(new DialogueTriggerScreen(this,selected));
 }
 void openNewlyCreated(String id){
  selected=id;
  reloadItems();
  DialogueCatalog.openEditor(this,id);
 }
 private void editSelected(){
  try {if(!DialogueCatalog.openEditor(this,selected))error=Text.translatable("screen.whiteout.dialogues.editor_failed",selected).getString();}
  catch(Exception e){error=Text.translatable("screen.whiteout.dialogues.editor_error",e.getClass().getSimpleName()).getString();}
 }
 private void confirmDelete(){
  DialogueCatalog.Entry chosen=items.stream().filter(e->e.id().equals(selected)).findFirst().orElse(null);
  if(chosen==null||chosen.bundled())return;
  if(client!=null)client.setScreen(new DialogueDeleteConfirmScreen(this,chosen.id(),DialogueCatalog.isBuiltin(chosen.id())));
 }
 void onDeletion(String id,boolean success,String detail){
  if(success){selected="";notice=Text.translatable("screen.whiteout.dialogues.deleted",id).getString();error="";}
  else {error=detail;notice="";}
 }
 private static String truncate(String s,int max){return s.length()<=max?s:s.substring(0,Math.max(1,max-1))+"…";}
 @Override public boolean shouldPause(){return false;}
 @Override public void close(){if(client!=null)client.setScreen(null);}
 @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
  WhiteoutTheme.backdrop(c,width,height);
  WhiteoutTheme.panel(c,panelLeft,panelTop,panelWidth,panelHeight,title.getString());
  c.drawTextWithShadow(textRenderer,Text.translatable("screen.whiteout.dialogues.search"),panelLeft+16,panelTop+29,0xFFB6C6D0);
  List<DialogueCatalog.Entry> found=filtered();int pages=Math.max(1,(found.size()+rows-1)/rows);
  String count=found.size()+" / "+items.size()+"    «"+(page+1)+" / "+pages+"»";
  c.drawCenteredTextWithShadow(textRenderer,count,width/2,panelTop+panelHeight-80,0xFFA3B9C5);
  DialogueCatalog.Entry picked=items.stream().filter(e->e.id().equals(selected)).findFirst().orElse(null);
  String preview=picked==null?Text.translatable("screen.whiteout.dialogues.select").getString():picked.speaker()+": "+picked.preview();
  c.drawTextWithShadow(textRenderer,truncate(preview,Math.max(22,(panelWidth-36)/6)),panelLeft+16,panelTop+panelHeight-58,0xFFD3E4E9);
  if(!error.isBlank())c.drawTextWithShadow(textRenderer,truncate(error,Math.max(24,(panelWidth-36)/6)),panelLeft+16,panelTop+panelHeight-99,0xFFFFA5A0);
  else if(!notice.isBlank())c.drawTextWithShadow(textRenderer,truncate(notice,Math.max(24,(panelWidth-36)/6)),panelLeft+16,panelTop+panelHeight-99,0xFFB7E8CB);
  super.render(c,mouseX,mouseY,delta);
 }
}
