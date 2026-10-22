package dev.whiteout.power;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Separate author-only read view. No action controls are exposed. */
public final class PowerDiagnosticsScreen extends SharpScreen {
 private DiagnosticData.Snapshot snapshot;
 private String filter="all",selected="";
 private int page,detailScroll;
 private long refreshAt;
 private ButtonWidget refresh;
 public PowerDiagnosticsScreen(DiagnosticData.Snapshot snapshot){super(tr("title"));this.snapshot=snapshot;}
 private static Text tr(String key){return Text.translatable("diag.whiteout."+key);}
 private List<DiagnosticData.Row> rows(){return DiagnosticData.filter(snapshot.rows(),filter);}
 private int left(){return 10;} private int top(){return 64;}
 private int split(){return Math.max(122,width/3);}
 private int pageSize(){return Math.max(1,(height-112)/22);}
 public void update(DiagnosticData.Snapshot value){snapshot=value;detailScroll=0;clearAndInit();}
 @Override protected void init(){
  String[] filters={"all","networks","generators","panels","zones"};
  int fw=Math.max(45,(width-20)/5);
  for(int i=0;i<filters.length;i++){
   String type=filters[i];var button=addDrawableChild(ButtonWidget.builder(tr(type),b->{filter=type;page=0;selected="";detailScroll=0;clearAndInit();}).dimensions(10+i*fw,35,fw-3,20).build());button.active=!filter.equals(type);
  }
  var rows=rows();int size=pageSize();page=Math.max(0,Math.min(page,Math.max(0,(rows.size()-1)/size)));
  if(rows.stream().noneMatch(r->r.id().equals(selected)))selected=rows.isEmpty()?"":rows.get(page*size).id();
  for(int i=page*size;i<Math.min(rows.size(),(page+1)*size);i++){
   var row=rows.get(i);String label=(row.state().equals("on")?"● ":"○ ")+row.name();
   if(row.name().isBlank())label=tr(row.state()).getString();
   var button=addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,split()-35)),b->{selected=row.id();detailScroll=0;clearAndInit();}).dimensions(left(),top()+(i%size)*22,split()-20,20).build());
   button.active=!row.id().equals(selected);
  }
  addDrawableChild(ButtonWidget.builder(Text.literal("◀"),b->{page--;clearAndInit();}).dimensions(10,height-32,30,20).build()).active=page>0;
  addDrawableChild(ButtonWidget.builder(Text.literal("▶"),b->{page++;clearAndInit();}).dimensions(44,height-32,30,20).build()).active=(page+1)*size<rows.size();
  refresh=addDrawableChild(ButtonWidget.builder(tr("refresh"),b->{refreshAt=System.currentTimeMillis();PowerDiagnosticsClient.refresh();}).dimensions(width-192,height-32,88,20).build());
  addDrawableChild(ButtonWidget.builder(tr("close"),b->close()).dimensions(width-98,height-32,88,20).build());
 }
 @Override public void tick(){if(refresh!=null)refresh.active=System.currentTimeMillis()-refreshAt>=1100;}
 @Override public boolean shouldPause(){return false;}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
  if(x>=split()){detailScroll=Math.max(0,detailScroll-(int)Math.signum(vertical)*3);return true;}
  page=Math.max(0,page-(int)Math.signum(vertical));clearAndInit();return true;
 }
 @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
  WhiteoutTheme.backdrop(c,width,height);
  c.drawCenteredTextWithShadow(textRenderer,title,width/2,12,WhiteoutTheme.SNOW);
  c.fill(split(),top(),width-10,height-39,WhiteoutTheme.PANEL);
  var row=rows().stream().filter(r->r.id().equals(selected)).findFirst().orElse(null);
  if(row!=null){
   List<net.minecraft.text.OrderedText> lines=new ArrayList<>();
   lines.addAll(textRenderer.wrapLines(Text.literal(row.name()),width-split()-25));
   lines.addAll(textRenderer.wrapLines(tr(row.state()),width-split()-25));
   for(var detail:row.details()){
    Text value=detail.translated()?tr(detail.value()):Text.literal(detail.value());
    lines.addAll(textRenderer.wrapLines(tr(detail.key()).copy().append(": ").append(value),width-split()-25));
   }
   int count=Math.max(1,(height-110)/12);detailScroll=Math.min(detailScroll,Math.max(0,lines.size()-count));
   c.enableScissor(split()+4,top()+2,width-13,height-40);
   for(int i=detailScroll;i<Math.min(lines.size(),detailScroll+count);i++)c.drawText(textRenderer,lines.get(i),split()+8,top()+7+(i-detailScroll)*12,i==1?(row.state().equals("on")?0xA7DBA0:WhiteoutTheme.WARN):WhiteoutTheme.SNOW,false);
   c.disableScissor();
  }
  String footer=tr(snapshot.truncated()?"truncated":"scope").getString();
  c.drawText(textRenderer,textRenderer.trimToWidth(footer,Math.max(10,width-100)),10,height-45,WhiteoutTheme.MUTED,false);
  super.render(c,mouseX,mouseY,delta);
 }
}
