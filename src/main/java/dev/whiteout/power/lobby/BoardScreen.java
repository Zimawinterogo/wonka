package dev.whiteout.power.lobby;

import dev.whiteout.power.SharpScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class BoardScreen extends SharpScreen {
 private final BoardNetwork.View data;
 private boolean editing,detail;
 private boolean started(){return data.token().equals(PartyScreen.token)&&PartyScreen.state!=null?PartyScreen.state.started():data.started();}
 void changed(java.util.UUID token){if(data.token().equals(token)&&!editing)clearAndInit();}
 private int page;
 private final TextFieldWidget[] fields=new TextFieldWidget[5];
 private static final String[] NAMES={"title","description","author","duration","note"};
 private static final int[] LIMIT={80,320,64,48,160};
 private static Text tr(String key){return Text.translatable("board.whiteout."+key);}
 BoardScreen(BoardNetwork.View data){super(tr("board"));this.data=data;}
 public static void register(){
  AuthorScreen.register();PartyScreen.register();PhotoScreen.register();
  ClientPlayNetworking.registerGlobalReceiver(BoardNetwork.View.ID,(msg,ctx)->ctx.client().execute(()->{PhotoScreen.opened(msg,ctx.client());ctx.client().setScreen(new BoardScreen(msg));}));
 }
 private int w(){return Math.min(width-16,374);}
 private int left(){return (width-w())/2;}
 private int top(){return Math.max(4,(height-200)/2);}
 private String[] values(){var c=data.card();return c==null?new String[]{"","","","",""}:new String[]{c.title(),c.description(),c.author(),c.duration(),c.note()};}
 private ButtonWidget button(Text text,int x,int y,int width,Runnable action){
  return addDrawableChild(new ButtonWidget(x,y,width,20,text,b->action.run(),supplier->supplier.get()){
   @Override protected void renderWidget(DrawContext d,int mx,int my,float dt){
    int px=getX(),py=getY(),w=getWidth();
    d.fill(px+2,py+3,px+w+2,py+21,0x77413423);
    d.fill(px,py,px+w,py+20,!active?0xffb3a68e:isHovered()||isFocused()?0xfff6e3b7:0xffdbc89c);
    d.fill(px+3,py+3,px+5,py+5,0xff85553c);
    d.drawText(textRenderer,getMessage(),px+(w-textRenderer.getWidth(getMessage()))/2,py+6,active?0x342a21:0x6b6053,false);
   }
  });
 }
 private void send(LobbyState.Card card){long rev=card==null&&PartyScreen.state!=null?PartyScreen.state.revision():data.revision();ClientPlayNetworking.send(new BoardNetwork.Change(data.token(),rev,card));close();}
 @Override protected void init(){
  int x=left(),y=top(),w=w();
  if(editing){
   var values=values();for(int i=0;i<5;i++){
    var field=new TextFieldWidget(textRenderer,x+w/3,y+26+i*26,w-w/3-14,20,tr(NAMES[i]));
    field.setMaxLength(LIMIT[i]);field.setText(values[i]);fields[i]=addDrawableChild(field);
   }
   button(Text.literal("Сценарий"),x+8,y+167,75,()->client.setScreen(new AuthorScreen(data)));
   if(data.card()!=null)button(Text.literal("Старт…"),x+w-76,y+167,66,()->client.setScreen(new PartyScreen(data,1)));
   var save=button(tr("save"),x+w/2-46,y+167,92,()->send(new LobbyState.Card(fields[0].getText(),fields[1].getText(),fields[2].getText(),fields[3].getText(),fields[4].getText())));
   save.active=!fields[0].getText().isBlank();fields[0].setChangedListener(v->save.active=!v.isBlank());setInitialFocus(fields[0]);return;
  }
  if(detail){
   button(Text.literal("‹ Доска"),x+10,y+167,70,()->{detail=false;clearAndInit();});
   if(data.editor()||!PhotoTransfer.pictures(data).isEmpty())button(Text.literal("Снимки"),x+w-82,y+167,72,()->client.setScreen(new PhotoScreen(data)));
   if(lines().size()>8){button(Text.literal("‹"),x+w/2-26,y+167,24,()->{page--;clearAndInit();}).active=page>0;
    button(Text.literal("›"),x+w/2+2,y+167,24,()->{page++;clearAndInit();}).active=(page+1)*8<lines().size();}return;
  }
  if(data.editor()&&!started()){var gear=button(Text.literal("⚙"),x+w-35,y+5,25,()->{editing=true;clearAndInit();});if(PersonalScreen.prefs().hints())gear.setTooltip(Tooltip.of(tr("edit")));}
  if(data.card()!=null){
   int right=x+w*56/100,rw=w-(right-x)-13;
   button(Text.literal("Карточка истории"),x+16,y+121,right-x-26,()->{detail=true;clearAndInit();});
   button(Text.literal("Участники"),right,y+41,rw,()->client.setScreen(new PartyScreen(data,0)));
   button(Text.literal("Сложность"),right,y+85,rw,()->client.setScreen(new PartyScreen(data,2)));
   button(Text.literal("Личные настройки"),right,y+122,rw,()->client.setScreen(new PersonalScreen(data)));
  }
  var start=button(tr(started()?"started":"start"),x+w/2-81,y+167,162,()->send(null));start.active=data.card()!=null&&!started()&&PartyScreen.state!=null&&data.token().equals(PartyScreen.token)&&PartyScreen.state.canStart();
 }
 private java.util.List<net.minecraft.text.OrderedText> lines(){
  var lines=new java.util.ArrayList<net.minecraft.text.OrderedText>();var v=values();
  for(int i=1;i<v.length;i++)if(!v[i].isBlank()){
   Text line=i==1?Text.literal(v[i]):tr(NAMES[i]).copy().append(" · ").append(v[i]);
   lines.addAll(textRenderer.wrapLines(line,w()-44-(PhotoTransfer.pictures(data).isEmpty()?0:90)));
  }
  return lines;
 }
 @Override public void render(DrawContext d,int mx,int my,float dt){
  int x=left(),y=top(),w=w();
  d.fill(0,0,width,height,0xb0181512);
  d.drawTexture(Identifier.of("whiteout","textures/block/rotten_spruce_planks.png"),x,y,0,0,w,196,32,32);
  d.fill(x+8,y+8,x+w-8,y+158,0xffd8c9a6);
  d.fill(x+14,y+11,x+17,y+14,0xff76452f);
  if(editing){
   d.drawText(textRenderer,tr("edit"),x+24,y+12,0x392e23,false);
   for(int i=0;i<5;i++)d.drawText(textRenderer,tr(NAMES[i]),x+14,y+31+i*26,0x392e23,false);
  }else if(detail||data.card()==null){
   var c=data.card();
   d.drawText(textRenderer,c==null?tr("board"):Text.literal(textRenderer.trimToWidth(c.title(),w-74)),x+24,y+16,0x382c20,false);
   PhotoScreen.mainPhoto(d,data,x+w-56,y+95);
   int row=y+38;
   if(c==null)d.drawTextWrapped(textRenderer,tr("empty"),x+22,row,w-44,0x504336);
   else{
    var lines=lines();page=Math.min(page,Math.max(0,(lines.size()-1)/8));
    for(int i=page*8;i<Math.min(lines.size(),(page+1)*8);i++){d.drawText(textRenderer,lines.get(i),x+22,row,0x504336,false);row+=12;}
   }
  }
  if(!editing&&!detail&&data.card()!=null){
   int right=x+w*56/100;var c=data.card();
   d.fill(x+12,y+30,right-7,y+148,0xffc4b593);d.fill(right-4,y+30,x+w-10,y+148,0xffcdbd99);
   d.drawText(textRenderer,textRenderer.trimToWidth(c.title(),w-70),x+18,y+15,0x352c22,false);
   if(PhotoTransfer.pictures(data).isEmpty()){
    int row=y+45;for(var line:textRenderer.wrapLines(Text.literal(c.description()),right-x-40)){if(row>y+106)break;d.drawText(textRenderer,line,x+22,row,0x483827,false);row+=12;}
   }else PhotoScreen.mainPhoto(d,data,(x+right)/2,y+77);
   var party=PartyScreen.state;if(party!=null){
    String mode=party.partner()!=null?"Duo · "+((party.mine()?1:0)+(party.other()?1:0))+"/2":party.invited()!=null?"Ожидание ответа":"Solo";
    d.drawText(textRenderer,mode,right+5,y+65,0x493d2d,false);
    d.drawText(textRenderer,party.balance()==null?"По миру":party.balance().preset(),right+5,y+109,0x493d2d,false);
   }
  }
  super.render(d,mx,my,dt);
 }
 @Override public boolean shouldPause(){return false;}
}
