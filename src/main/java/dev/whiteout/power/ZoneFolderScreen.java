package dev.whiteout.power;

import dev.whiteout.Zone;
import dev.whiteout.client.Bridge;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Folder-first view for meteor-resonator: folders -> sorted zones -> editor. */
public final class ZoneFolderScreen extends SharpScreen {
 private String selectedFolder; // null = folders, "" = unfiled
 private int folderPage,zonePage;
 private boolean rename,moveMode;
 private TextFieldWidget input;
 private static Text t(String key,Object... args){return Text.translatable("screen.whiteout.folders."+key,args);}
 private static String tr(String key,Object... args){return t(key,args).getString();}
 public ZoneFolderScreen(){super(Text.translatable("screen.whiteout.folders.title"));}
 public void refreshFromServer(){clearAndInit();}
 private List<String> folderNames(){
  List<String> folders=new ArrayList<>();folders.add("");folders.addAll(ZoneFoldersClient.get().folders);
  return folders;
 }
 private List<Zone> zonesInFolder(){
  List<Zone> zones=Bridge.snapshot==null||Bridge.snapshot.zones==null?List.of():Bridge.snapshot.zones;
  if(selectedFolder==null)return List.of();
  return ZoneFoldersClient.sortedForFolder(zones,selectedFolder);
 }
 private List<Zone> allZones(){
  return Bridge.snapshot==null||Bridge.snapshot.zones==null?List.of():Bridge.snapshot.zones;
 }
 @Override protected void init(){
  EditorClient.addToolbar(this);
  int x=width/2-168,y=Math.max(32,height/2-118),w=336;
  if(selectedFolder==null){
   input=new TextFieldWidget(textRenderer,x,y,207,20,t("folder_name"));
   input.setMaxLength(32);input.setText("");addDrawableChild(input);
   addDrawableChild(ButtonWidget.builder(t("create"),b->{
    String name=input.getText().strip();if(!name.isBlank())ZoneFoldersClient.edit("create",name,"");
    input.setText("");
   }).dimensions(x+212,y,124,20).build());
   List<String> folders=folderNames();
   int pages=Math.max(1,(folders.size()+6)/7);folderPage=Math.min(folderPage,pages-1);
   for(int i=folderPage*7;i<Math.min(folders.size(),folderPage*7+7);i++){
    String f=folders.get(i);
    String caption=f.isEmpty()?tr("unfiled"):f;
    // One click: open the editor of this folder with its first zone selected. An empty folder opens
    // the management view in "add zones" mode, because there is nothing to edit yet.
    addDrawableChild(ButtonWidget.builder(Text.literal("▸  "+caption),b->{
     List<Zone> inside=ZoneFoldersClient.sortedForFolder(allZones(),f);
     if(!inside.isEmpty()){ZoneFoldersClient.openEditor(f,inside.get(0));return;}
     selectedFolder=f;zonePage=0;moveMode=true;clearAndInit();
    }).dimensions(x,y+27+(i%7)*22,w-46,20).build());
    // Folder management (reorder, add/move zones, rename, delete) stays one click away.
    addDrawableChild(ButtonWidget.builder(t("manage"),b->{
     selectedFolder=f;zonePage=0;moveMode=false;clearAndInit();
    }).dimensions(x+w-42,y+27+(i%7)*22,42,20).build());
   }
   int navY=y+188;
   addDrawableChild(ButtonWidget.builder(Text.literal("◀"),b->{folderPage=Math.max(0,folderPage-1);clearAndInit();}).dimensions(x,navY,40,20).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("▶"),b->{folderPage=Math.min(pages-1,folderPage+1);clearAndInit();}).dimensions(x+45,navY,40,20).build());
   addDrawableChild(ButtonWidget.builder(t("close"),b->close()).dimensions(x+222,navY,114,20).build());
   return;
  }
  String caption=selectedFolder.isEmpty()?tr("unfiled"):selectedFolder;
  addDrawableChild(ButtonWidget.builder(t("back"),b->{selectedFolder=null;moveMode=false;clearAndInit();}).dimensions(x,y,96,20).build());
  addDrawableChild(ButtonWidget.builder(t(moveMode?"finish_move":"add_move"),b->{moveMode=!moveMode;zonePage=0;clearAndInit();}).dimensions(x+101,y,145,20).build());
  if(!selectedFolder.isEmpty()){
   addDrawableChild(ButtonWidget.builder(t("rename"),b->{rename=true;moveMode=false;clearAndInit();}).dimensions(x+251,y,40,20).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("✕"),b->{ZoneFoldersClient.edit("delete",selectedFolder,"");selectedFolder=null;clearAndInit();}).dimensions(x+296,y,40,20).build());
  }
  if(rename&&!selectedFolder.isEmpty()){
   input=new TextFieldWidget(textRenderer,x,y+25,225,20,t("new_name"));
   input.setMaxLength(32);input.setText(selectedFolder);addDrawableChild(input);
   addDrawableChild(ButtonWidget.builder(t("save"),b->{
    String name=input.getText().strip();if(!name.isBlank()&&!name.equals(selectedFolder)){
     ZoneFoldersClient.edit("rename",selectedFolder,name);selectedFolder=name;
    }
    rename=false;clearAndInit();
   }).dimensions(x+230,y+25,106,20).build());
  }
  List<Zone> zones=moveMode?allZones():zonesInFolder();
  int listTop=y+(rename?53:27);
  int pages=Math.max(1,(zones.size()+6)/7);zonePage=Math.min(zonePage,pages-1);
  for(int i=zonePage*7;i<Math.min(zones.size(),zonePage*7+7);i++){
   Zone z=zones.get(i);if(z==null||z.id==null)continue;
   int local=i%7,yy=listTop+local*22;
   String current=ZoneFoldersClient.get().assigned.getOrDefault(z.id,"");
   String label=z.name==null||z.name.isBlank()?z.id:z.name;
   if(moveMode){
    addDrawableChild(ButtonWidget.builder(Text.literal((selectedFolder.equals(current)?"✓ ":"+ ")+label),
      b->ZoneFoldersClient.edit("move",selectedFolder,z.id)).dimensions(x,yy,w,20).build());
   }else{
    addDrawableChild(ButtonWidget.builder(Text.literal(label),b->ZoneFoldersClient.openEditor(selectedFolder,z))
      .dimensions(x,yy,w-88,20).build());
    addDrawableChild(ButtonWidget.builder(Text.literal("↑"),b->ZoneFoldersClient.edit("up",selectedFolder,z.id))
      .dimensions(x+w-84,yy,40,20).build());
    addDrawableChild(ButtonWidget.builder(Text.literal("↓"),b->ZoneFoldersClient.edit("down",selectedFolder,z.id))
      .dimensions(x+w-41,yy,40,20).build());
   }
  }
  int navY=listTop+158;
  addDrawableChild(ButtonWidget.builder(Text.literal("◀"),b->{zonePage=Math.max(0,zonePage-1);clearAndInit();}).dimensions(x,navY,42,20).build());
  addDrawableChild(ButtonWidget.builder(t("page",zonePage+1,pages),b->{}).dimensions(x+48,navY,106,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("▶"),b->{zonePage=Math.min(pages-1,zonePage+1);clearAndInit();}).dimensions(x+160,navY,42,20).build());
  addDrawableChild(ButtonWidget.builder(t("editor"),b->ZoneFoldersClient.openEditor(selectedFolder,null)).dimensions(x+207,navY,129,20).build());
 }
 @Override public void render(DrawContext ctx,int mx,int my,float delta){
  super.render(ctx,mx,my,delta);
  int top=Math.max(16,height/2-141);
  String title=selectedFolder==null?tr("choose"):
   tr("folder_label",(selectedFolder.isEmpty()?tr("unfiled"):selectedFolder))+
    tr(moveMode?"move_help":"order_help");
  ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,top,0xD4E7F9);
 }
 @Override public boolean shouldPause(){return false;}
}
