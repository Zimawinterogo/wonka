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
 private LobbyUi.Box board(){return LobbyUi.Box.centered(width,height,520,286);}
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
  boolean roomy=h>=260;int lw=w*58/100,rx=x+lw+3,rw=w-lw-16;
  int bottom=y+h-37;
  if(editing){
   String[] vals=values();
   for(int i=0;i<5;i++){
    int fy=y+49+i*27;
    TextFieldWidget f=new TextFieldWidget(textRenderer,x+110,fy,w-130,19,tr(NAMES[i]));
    f.setMaxLength(LIMIT[i]);f.setText(vals[i]);fields[i]=addDrawableChild(f);
   }
   button("Сценарий",x+16,bottom,Math.min(88,(w-38)/4),()->client.setScreen(new AuthorScreen(data)));
   if(data.card()!=null)button("Старт...",x+112,bottom,79,()->client.setScreen(new PartyScreen(data,1)));
   LobbyUi.Btn save=startButton("Сохранить",x+w-120,bottom,104,()->send(new LobbyState.Card(
    fields[0].getText(),fields[1].getText(),fields[2].getText(),fields[3].getText(),fields[4].getText())));
   save.active=!fields[0].getText().isBlank();
   fields[0].setChangedListener(value->save.active=!value.isBlank());
   setInitialFocus(fields[0]);return;
  }
  if(detail){
   button("К доске",x+16,bottom,80,()->{detail=false;clearAndInit();});
   if(data.editor()||!PhotoTransfer.pictures(data).isEmpty())
    button("Фотографии",x+w-122,bottom,104,()->client.setScreen(new PhotoScreen(data)));
   int count=lines().size(),per=roomy?13:9;
   if(count>per){
    LobbyUi.Btn prev=button("‹",x+w/2-29,bottom,24,()->{page--;clearAndInit();});prev.active=page>0;
    LobbyUi.Btn next=button("›",x+w/2+4,bottom,24,()->{page++;clearAndInit();});next.active=(page+1)*per<count;
   }
   return;
  }
  if(data.editor()&&!started()){
   LobbyUi.Btn edit=addDrawableChild(LobbyUi.iconButton(LobbyUi.ICON_GEAR,Text.literal(""),x+w-37,y+12,24,22,()->{editing=true;clearAndInit();}));
   if(PersonalScreen.prefs().hints())edit.setTooltip(Tooltip.of(tr("edit")));
  }
  if(data.card()!=null){
   button("ОТКРЫТЬ ИСТОРИЮ",x+24,y+h-69,lw-36,()->{detail=true;clearAndInit();});
   int y0=y+(roomy?111:103),gap=roomy?40:29;
   button("УЧАСТНИКИ",rx+7,y0,rw-14,()->client.setScreen(new PartyScreen(data,0)));
   button("СЛОЖНОСТЬ",rx+7,y0+gap,rw-14,()->client.setScreen(new PartyScreen(data,2)));
   button("НАСТРОЙКИ",rx+7,y0+gap*2,rw-14,()->client.setScreen(new PersonalScreen(data)));
  }
  int startW=Math.min(226,w-85);
  LobbyUi.Btn start=startButton(started()?"ИСТОРИЯ ЗАПУЩЕНА":"НАЧАТЬ ИСТОРИЮ",x+(w-startW)/2,bottom-2,startW,()->send(null));
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
  boolean roomy=h>=260,wide=w>=470;int lw=w*58/100,rx=x+lw+3,rw=w-lw-16;
  LobbyUi.backdrop(d,width,height);LobbyUi.board(d,b);
  // All words below are live GUI text, NOT baked into the texture.
  LobbyUi.accent(d,x+16,y+12,15,0xffc1433f);
  LobbyUi.fit(d,textRenderer,"W H I T E O U T",x+26,y+11,w-70,LobbyUi.LIGHT,false,0);
  LobbyUi.caption(d,textRenderer,"ЛОББИ / ДОСЬЕ",x+26,y+25,w-76,LobbyUi.LIGHT_DIM);
  LobbyUi.divider(d,x+16,y+38,w-32);
  if(editing){
   LobbyUi.paper(d,x+13,y+43,w-26,h-87);
   for(int i=0;i<5;i++){
    LobbyUi.fit(d,textRenderer,LABELS[i],x+23,y+54+i*27,80,LobbyUi.INK_SOFT,false,0);
    if(fields[i]!=null)LobbyUi.plate(d,fields[i]);
   }
   LobbyUi.caption(d,textRenderer,"РЕДАКТИРОВАНИЕ КАРТОЧКИ",x+22,y+h-58,w-44,LobbyUi.LIGHT_DIM);
  }else if(detail||data.card()==null){
   LobbyUi.paper(d,x+13,y+44,w-26,h-90);
   LobbyUi.accent(d,x+21,y+52,15,0xffb83e37);
   if(data.card()==null){
    LobbyUi.fit(d,textRenderer,"КАРТОЧКА ИСТОРИИ НЕ НАСТРОЕНА",x+30,y+53,w-58,LobbyUi.INK,false,0);
    d.drawTextWrapped(textRenderer,tr("empty"),x+25,y+79,w-59,LobbyUi.INK_SOFT);
    LobbyUi.caption(d,textRenderer,"НАЖМИ НА ШЕСТЕРЁНКУ ДЛЯ НАСТРОЙКИ",x+25,y+136,w-50,LobbyUi.LIGHT_DIM);
   }else{
    LobbyUi.fit(d,textRenderer,data.card().title(),x+30,y+54,w-60,LobbyUi.INK,false,0);
    LobbyUi.divider(d,x+24,y+73,w-50);
    List<net.minecraft.text.OrderedText> txt=lines();
    int per=roomy?13:9;page=Math.min(page,Math.max(0,(txt.size()-1)/per));
    int yy=y+85;for(int i=page*per;i<Math.min(txt.size(),(page+1)*per);i++){
     d.drawText(textRenderer,txt.get(i),x+26,yy,LobbyUi.INK_SOFT,false);yy+=12;
    }
   }
  }else{
   LobbyUi.paper(d,x+13,y+44,lw-16,h-87);
   LobbyUi.sticky(d,rx,y+44,rw,h-87,false);
   LobbyUi.accent(d,x+21,y+53,11,0xffbf403a);
   LobbyUi.caption(d,textRenderer,"СЮЖЕТНАЯ КАМПАНИЯ",x+29,y+54,lw-48,LobbyUi.INK_SOFT);
   LobbyUi.fit(d,textRenderer,data.card().title(),x+25,y+73,lw-39,LobbyUi.INK,false,0);
   LobbyUi.divider(d,x+25,y+90,lw-39);
   int px=x+26,py=y+99,pw=Math.min(wide?150:lw-47,lw-47),ph=wide&&roomy?84:49;
   if(!PhotoTransfer.pictures(data).isEmpty()){
    PhotoScreen.collage(d,data,px+pw/2,py+ph/2);
   }else{
    d.drawTexture(LobbyUi.SCENE,px,py,0,0,pw,ph,160,90);
    d.drawBorder(px-1,py-1,pw+2,ph+2,0xff6c8792);
    d.fill(px,py+ph-10,px+pw,py+ph,0x8c0b1d26);
   }
   int descX=wide&&roomy?px+pw+12:x+25;
   int descY=wide&&roomy?py+9:py+ph+8;
   int descW=wide&&roomy?lw-pw-57:lw-46;
   int maxY=y+h-73;
   if(!PhotoTransfer.pictures(data).isEmpty()&&wide&&roomy){
    LobbyUi.fit(d,textRenderer,"ФОТОГРАФИИ",px,y+190,pw,LobbyUi.LIGHT_DIM,false,0);
   }
   for(var line:textRenderer.wrapLines(Text.literal(data.card().description()),Math.max(25,descW))){
    if(descY+9>maxY)break;
    d.drawText(textRenderer,line,descX,descY,LobbyUi.INK_SOFT,false);descY+=12;
   }
   if(roomy) LobbyUi.caption(d,textRenderer,"АВТОР: "+data.card().author(),x+25,y+h-86,lw-45,LobbyUi.LIGHT_DIM);
   LobbyUi.caption(d,textRenderer,"КОМАНДА / СОСТОЯНИЕ",rx+12,y+55,rw-24,LobbyUi.INK_SOFT);
   LobbyUi.divider(d,rx+11,y+69,rw-22);
   var party=PartyScreen.state;
   if(party!=null&&data.token().equals(PartyScreen.token)){
    String status=party.partner()!=null?"DUO  "+((party.mine()?1:0)+(party.other()?1:0))+"/2":party.invited()!=null?"ОЖИДАНИЕ":"SOLO";
    LobbyUi.fit(d,textRenderer,status,rx+13,y+81,rw-42,LobbyUi.INK,false,0);
    LobbyUi.led(d,rx+rw-22,y+82,party.canStart()?LobbyUi.LED_OK:LobbyUi.LED_WAIT);
    if(roomy) LobbyUi.caption(d,textRenderer,party.balance()==null?"Сложность: по миру":"Сложность: "+party.balance().label(),rx+12,y+h-64,rw-25,LobbyUi.LIGHT_DIM);
   }else{
    LobbyUi.fit(d,textRenderer,"ОЖИДАНИЕ СВЯЗИ",rx+13,y+81,rw-25,LobbyUi.INK_SOFT,false,0);
   }
   // Subtle thread connects the story dossier and the crew panel.
   LobbyUi.thread(d,x+25,y+45,rx+rw-12,y+44);
   LobbyUi.pin(d,x+25,y+45);LobbyUi.pin(d,rx+rw-12,y+44);
  }
  LobbyUi.caption(d,textRenderer,"АРХИВ / WHITEOUT 3.13",x+16,y+h-14,w-32,LobbyUi.LIGHT_DIM);
  super.render(d,mx,my,dt);
 }
 @Override public boolean shouldPause(){return false;}
}
