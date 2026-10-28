package dev.whiteout.power;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Read-only author report; copying a command never teleports the player. */
public final class PassportScreen extends SharpScreen {
 private MapPassport report;
 private String tab="summary",severity="ALL";
 private int scroll,selected,detailScroll;
 private long refreshAt;
 private ButtonWidget refresh;
 private record Row(String label,List<String> lines,MapPassport.Position position){}
 public PassportScreen(MapPassport report){super(tr("title"));this.report=report;}
 static Text tr(String key){return Text.translatable("passport.whiteout."+key);}
 public void update(MapPassport value){report=value;scroll=0;selected=0;detailScroll=0;clearAndInit();}
 private int visible(){return Math.max(1,(height-137)/22);}
 private int split(){return Math.max(120,width/3);}
 private List<Row> rows(){
  List<Row> rows=new ArrayList<>();
  if(tab.equals("summary")){
   rows.add(new Row(tr("summary").getString(),List.of(tr("scope").getString(),"Objects: "+report.entries().size(),
    "ERROR: "+count("ERROR"),"WARN: "+count("WARN"),"INFO: "+count("INFO"),tr(report.truncated()?"truncated":"complete").getString()),null));
  }else if(tab.equals("issues")){
   for(var i:report.issues())if(severity.equals("ALL")||severity.equals(i.severity().name())){
    String label=i.severity()+": "+tr(i.code()).getString();
    rows.add(new Row(label,List.of(label,i.object()),i.position()));
   }
  }else for(var e:report.entries())if(e.section().equals(tab)){
   List<String> lines=new ArrayList<>();lines.add(e.id());e.values().forEach((k,v)->lines.add(k+": "+v));
   rows.add(new Row(e.id(),lines,e.position()));
  }
  return rows;
 }
 private long count(String severity){return report.issues().stream().filter(i->i.severity().name().equals(severity)).count();}
 @Override protected void init(){
  String[] tabs={"summary","zones","networks","atmosphere","dialogues","issues"};int w=(width-20)/6;
  for(int i=0;i<tabs.length;i++){String t=tabs[i];addDrawableChild(ButtonWidget.builder(tr(t),b->{tab=t;scroll=0;selected=0;detailScroll=0;clearAndInit();}).dimensions(10+i*w,32,w-2,20).build()).active=!tab.equals(t);}
  if(tab.equals("issues")){
   String[] levels={"ALL","ERROR","WARN","INFO"};
   for(int i=0;i<levels.length;i++){String level=levels[i];addDrawableChild(ButtonWidget.builder(Text.literal(level),b->{severity=level;scroll=0;selected=0;detailScroll=0;clearAndInit();}).dimensions(10+i*58,56,56,20).build()).active=!severity.equals(level);}
  }
  var rows=rows();scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-visible())));selected=Math.max(0,Math.min(selected,rows.size()-1));
  for(int i=scroll;i<Math.min(rows.size(),scroll+visible());i++){
   int index=i;var r=rows.get(i);
   addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(r.label(),split()-28)),b->{selected=index;detailScroll=0;clearAndInit();})
    .dimensions(10,80+(i-scroll)*22,split()-18,20).build()).active=i!=selected;
  }
  var pos=rows.isEmpty()?null:rows.get(selected).position();
  var copy=addDrawableChild(ButtonWidget.builder(tr("copy_tp"),b->client.keyboard.setClipboard(pos.teleport())).dimensions(10,height-30,90,20).build());
  copy.active=pos!=null&&!pos.teleport().isEmpty();
  refresh=addDrawableChild(ButtonWidget.builder(tr("refresh"),b->{refreshAt=System.currentTimeMillis();PassportClient.request();}).dimensions(width-192,height-30,88,20).build());
  addDrawableChild(ButtonWidget.builder(tr("close"),b->close()).dimensions(width-98,height-30,88,20).build());
 }
 @Override public void tick(){if(refresh!=null)refresh.active=System.currentTimeMillis()-refreshAt>=1100;}
 @Override public boolean shouldPause(){return false;}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
  if(x>=split())detailScroll=Math.max(0,detailScroll-(int)Math.signum(vertical)*3);
  else{scroll-=(int)Math.signum(vertical)*3;clearAndInit();}return true;
 }
 @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
  WhiteoutTheme.backdrop(c,width,height);c.drawCenteredTextWithShadow(textRenderer,title,width/2,12,WhiteoutTheme.SNOW);
  var rows=rows();c.fill(split(),80,width-10,height-40,WhiteoutTheme.PANEL);
  if(!rows.isEmpty()){
   var row=rows.get(selected);List<net.minecraft.text.OrderedText> lines=new ArrayList<>();
   for(String line:row.lines())lines.addAll(textRenderer.wrapLines(Text.literal(line),Math.max(20,width-split()-24)));
   if(row.position()!=null)lines.addAll(textRenderer.wrapLines(Text.literal(row.position().dimension()+" "+row.position().x()+" "+row.position().y()+" "+row.position().z()),Math.max(20,width-split()-24)));
   int count=Math.max(1,(height-130)/12);detailScroll=Math.min(detailScroll,Math.max(0,lines.size()-count));
   c.enableScissor(split()+2,80,width-10,height-40);
   for(int i=detailScroll;i<Math.min(lines.size(),detailScroll+count);i++)c.drawText(textRenderer,lines.get(i),split()+6,86+(i-detailScroll)*12,WhiteoutTheme.SNOW,false);
   c.disableScissor();
  }
  c.drawText(textRenderer,textRenderer.trimToWidth(tr(report.truncated()?"truncated":"scope").getString(),width-20),10,height-43,WhiteoutTheme.MUTED,false);
  super.render(c,mouseX,mouseY,delta);
 }
}
