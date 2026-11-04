package dev.whiteout.power.lobby;

import dev.whiteout.power.SharpScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;

public final class BoardScreen extends SharpScreen {
 private final BoardNetwork.View data;
 private boolean editing,detail;
 private int page;
 private final TextFieldWidget[] fields=new TextFieldWidget[5];
 private static final String[] NAMES={"title","description","author","duration","note"};
 private static final String[] LABELS={"Название","Описание","Автор","Длительность","Примечание"};
 private static final int[] LIMIT={80,320,64,48,160};
 private static Text tr(String key){return Text.translatable("board.whiteout."+key);}
 BoardScreen(BoardNetwork.View data){super(tr("board"));this.data=data;}
 public static void register(){
  AuthorScreen.register();PartyScreen.register();PhotoScreen.register();
  ClientPlayNetworking.registerGlobalReceiver(BoardNetwork.View.ID,(msg,ctx)->ctx.client().execute(()->{
   PhotoScreen.opened(msg,ctx.client());ctx.client().setScreen(new BoardScreen(msg));
  }));
 }
 private LobbyUi.Box board(){return LobbyUi.Box.centered(width,height,430,234);}
 private boolean started(){return data.token().equals(PartyScreen.token)&&PartyScreen.state!=null?PartyScreen.state.started():data.started();}
 void changed(UUID token){if(data.token().equals(token)&&!editing)clearAndInit();}
 private String[] values(){var c=data.card();return c==null?new String[]{"","","","",""}:new String[]{c.title(),c.description(),c.author(),c.duration(),c.note()};}
 private LobbyUi.Btn button(String text,int x,int y,int w,Runnable action){return addDrawableChild(LobbyUi.button(Text.literal(text),x,y,w,20,action));}
 private LobbyUi.Btn startButton(String text,int x,int y,int w,Runnable action){return addDrawableChild(LobbyUi.primary(Text.literal(text),x,y,w,21,action));}
 private void send(LobbyState.Card card){
  long rev=card==null&&PartyScreen.state!=null?PartyScreen.state.revision():data.revision();
  ClientPlayNetworking.send(new BoardNetwork.Change(data.token(),rev,card));close();
 }
 @Override protected void init(){
  LobbyUi.Box b=board();int x=b.x(),y=b.y(),w=b.w(),h=b.h();
  if(editing){
   String[] vals=values();
   for(int i=0;i<5;i++){
    TextFieldWidget f=new TextFieldWidget(textRenderer,x+105,y+42+i*25,w-125,18,tr(NAMES[i]));
    f.setMaxLength(LIMIT[i]);f.setText(vals[i]);fields[i]=addDrawableChild(f);
   }
   button("Сценарий",x+16,y+h-32,78,()->client.setScreen(new AuthorScreen(data)));
   if(data.card()!=null)button("Старт...",x+97,y+h-32,75,()->client.setScreen(new PartyScreen(data,1)));
   LobbyUi.Btn save=startButton("Сохранить",x+w-117,y+h-33,99,()->send(new LobbyState.Card(
    fields[0].getText(),fields[1].getText(),fields[2].getText(),fields[3].getText(),fields[4].getText())));
   save.active=!fields[0].getText().isBlank();
   fields[0].setChangedListener(v->save.active=!v.isBlank());
   setInitialFocus(fields[0]);return;
  }
  if(detail){
   button("К доске",x+16,y+h-32,78,()->{detail=false;clearAndInit();});
   if(data.editor()||!PhotoTransfer.pictures(data).isEmpty())button("Фотографии",x+w-120,y+h-32,103,()->client.setScreen(new PhotoScreen(data)));
   int count=lines().size();if(count>9){
    LobbyUi.Btn prev=button("‹",x+w/2-24,y+h-32,22,()->{page--;clearAndInit();});prev.active=page>0;
    LobbyUi.Btn next=button("›",x+w/2+3,y+h-32,22,()->{page++;clearAndInit();});next.active=(page+1)*9<count;
   }return;
  }
  if(data.editor()&&!started()){
   LobbyUi.Btn edit=addDrawableChild(LobbyUi.iconButton(LobbyUi.ICON_GEAR,Text.literal(""),x+w-37,y+12,23,21,()->{editing=true;clearAndInit();}));
   if(PersonalScreen.prefs().hints())edit.setTooltip(Tooltip.of(tr("edit")));
  }
  if(data.card()!=null){
   int rx=x+w*59/100,rw=x+w-rx-17,lw=rx-x-27;
   button("История",x+17,y+153,lw,()->{detail=true;clearAndInit();});
   button("Участники",rx,y+49,rw,()->client.setScreen(new PartyScreen(data,0)));
   button("Сложность",rx,y+92,rw,()->client.setScreen(new PartyScreen(data,2)));
   button("Личные",rx,y+134,rw,()->client.setScreen(new PersonalScreen(data)));
  }
  LobbyUi.Btn start=startButton(started()?"ИСТОРИЯ ЗАПУЩЕНА":"НАЧАТЬ ИСТОРИЮ",x+w/2-88,y+h-34,176,()->send(null));
  start.active=data.card()!=null&&!started()&&PartyScreen.state!=null&&data.token().equals(PartyScreen.token)&&PartyScreen.state.canStart();
 }
 private List<net.minecraft.text.OrderedText> lines(){
  List<net.minecraft.text.OrderedText> lines=new ArrayList<>();String[] v=values();int max=board().w()-62;
  for(int i=1;i<v.length;i++)if(!v[i].isBlank()){
   Text line=i==1?Text.literal(v[i]):Text.literal(LABELS[i]+": "+v[i]);
   lines.addAll(textRenderer.wrapLines(line,max));
  }
  return lines;
 }
 @Override public void render(DrawContext d,int mx,int my,float dt){
  LobbyUi.Box b=board();int x=b.x(),y=b.y(),w=b.w(),h=b.h();
  LobbyUi.backdrop(d,width,height);LobbyUi.board(d,b);
  LobbyUi.fit(d,textRenderer,"ДОСЬЕ / WHITEOUT",x+18,y+15,w-70,LobbyUi.LIGHT,false,0);
  LobbyUi.pin(d,x+16,y+14);LobbyUi.pin(d,x+w-14,y+h-15);
  if(editing){
   LobbyUi.paper(d,x+13,y+32,w-26,h-72);
   for(int i=0;i<5;i++){
    LobbyUi.fit(d,textRenderer,LABELS[i],x+24,y+47+i*25,77,LobbyUi.INK_SOFT,false,0);
    LobbyUi.plate(d,fields[i]);
   }
   LobbyUi.caption(d,textRenderer,"РЕДАКТИРОВАНИЕ КАРТОЧКИ",x+24,y+h-50,w-50,LobbyUi.INK_SOFT);
  }else if(detail||data.card()==null){
   LobbyUi.paper(d,x+14,y+34,w-28,h-77);
   LobbyUi.pin(d,x+22,y+39);
   if(data.card()==null){
    LobbyUi.fit(d,textRenderer,"ДЕЛО ЕЩЁ НЕ ОФОРМЛЕНО",x+33,y+48,w-72,LobbyUi.INK,false,0);
    d.drawTextWrapped(textRenderer,tr("empty"),x+27,y+75,w-60,LobbyUi.INK_SOFT);
    LobbyUi.caption(d,textRenderer,"НАСТРОЙТЕ КАРТОЧКУ ЧЕРЕЗ ШЕСТЕРЁНКУ",x+27,y+134,w-60,LobbyUi.INK_SOFT);
   }else{
    LobbyUi.fit(d,textRenderer,data.card().title(),x+34,y+47,w-73,LobbyUi.INK,false,0);
    LobbyUi.divider(d,x+28,y+66,w-56);
    List<net.minecraft.text.OrderedText> txt=lines();page=Math.min(page,Math.max(0,(txt.size()-1)/9));
    int yy=y+77;for(int i=page*9;i<Math.min(txt.size(),(page+1)*9);i++){d.drawText(textRenderer,txt.get(i),x+28,yy,LobbyUi.INK_SOFT,false);yy+=12;}
   }
  }else{
   int rx=x+w*59/100,rw=x+w-rx-17,lw=rx-x-27;
   LobbyUi.paper(d,x+13,y+36,lw+9,145);
   LobbyUi.pin(d,x+23,y+40);
   LobbyUi.fit(d,textRenderer,data.card().title(),x+28,y+49,lw-17,LobbyUi.INK,false,0);
   LobbyUi.divider(d,x+24,y+65,lw-16);
   if(PhotoTransfer.pictures(data).isEmpty()){
    int yy=y+76;
    for(var line:textRenderer.wrapLines(Text.literal(data.card().description()),lw-26)){
     if(yy>y+132)break;d.drawText(textRenderer,line,x+25,yy,LobbyUi.INK_SOFT,false);yy+=12;
    }
    LobbyUi.caption(d,textRenderer,data.card().author(),x+25,y+138,lw-25,LobbyUi.INK_SOFT);
   }else{
    PhotoScreen.collage(d,data,x+24+lw/2,y+116);
   }
   LobbyUi.sticky(d,rx-5,y+36,rw+13,144,false);
   LobbyUi.fit(d,textRenderer,"УЧАСТНИКИ",rx+6,y+57,rw-13,LobbyUi.INK,false,0);
   var party=PartyScreen.state;
   if(party!=null&&data.token().equals(PartyScreen.token)){
    String status=party.partner()!=null?"DUO "+((party.mine()?1:0)+(party.other()?1:0))+"/2":party.invited()!=null?"ОЖИДАНИЕ":"SOLO";
    LobbyUi.fit(d,textRenderer,status,rx+6,y+76,rw-13,LobbyUi.INK_SOFT,false,0);
    LobbyUi.led(d,rx+rw-12,y+75,party.canStart()?LobbyUi.LED_OK:LobbyUi.LED_WAIT);
    LobbyUi.fit(d,textRenderer,party.balance()==null?"По миру":party.balance().label(),rx+6,y+119,rw-13,LobbyUi.INK_SOFT,false,0);
   }else{
    LobbyUi.fit(d,textRenderer,"Ожидание связи...",rx+6,y+76,rw-13,LobbyUi.INK_SOFT,false,0);
   }
   LobbyUi.thread(d,x+25,y+39,rx+rw-1,y+40);
  }
  LobbyUi.caption(d,textRenderer,"ЛОББИ / ЗАПИСЬ № 013",x+17,y+h-16,w-34,LobbyUi.LIGHT_DIM);
  super.render(d,mx,my,dt);
 }
 @Override public boolean shouldPause(){return false;}
}
