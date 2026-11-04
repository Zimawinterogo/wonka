package dev.whiteout.power.lobby;

import dev.whiteout.power.SharpScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;

final class PartyScreen extends SharpScreen{
 static UUID token;static Party.Status state;
 private final BoardNetwork.View view;
 private int tab,page;private final TextFieldWidget[] fields=new TextFieldWidget[9];private String error="";
 PartyScreen(BoardNetwork.View v,int tab){super(Text.literal("Whiteout"));view=v;this.tab=tab;}
 static void register(){ClientPlayNetworking.registerGlobalReceiver(Party.Info.ID,(m,c)->c.client().execute(()->{
  token=m.token();state=BoardNetwork.JSON.fromJson(m.json(),Party.Status.class);
  if(c.client().currentScreen instanceof PartyScreen scr&&scr.view.token().equals(token)&&scr.tab==0)scr.clearAndInit();
  if(c.client().currentScreen instanceof BoardScreen scr)scr.changed(token);
 }));}
 private static final String[] labels={"Ген. новый %","Ген. старый %","Лампа мин","Заряд мин","Фонарь мин","Отказ %","Проверка сек","Метель сек","Снег сек"};
 private void request(String action,String value){ClientPlayNetworking.send(new Party.Request(view.token(),state.revision(),action,value));}
 private LobbyUi.Btn btn(int x,int y,int w,String t,Runnable r){return addDrawableChild(LobbyUi.button(Text.literal(t),x,y,w,19,r));}
 private String name(UUID id){return state.online().stream().filter(p->p.id().equals(id)).map(Party.Person::name).findFirst().orElse("офлайн");}
 @Override protected void init(){
  if(state==null||!view.token().equals(token))return;
  var box=LobbyUi.Box.centered(width,height,406,248);int x=box.x()+22,y=box.y()+30;var me=client.player.getUuid();
  btn(x+204,y+177,72,"Назад",()->close());
  if(tab==1){
   String[] val={"","","","",""};var start=state.start();
   if(start!=null){if(start.dimension()!=null){val[0]=start.dimension();val[1]=""+start.x();val[2]=""+start.y();val[3]=""+start.z();}if(start.event()!=null)val[4]=start.event();}
   for(int i=0;i<5;i++){fields[i]=addDrawableChild(new TextFieldWidget(textRenderer,x+86,y+i*23,196,19,Text.literal("Старт")));fields[i].setMaxLength(128);fields[i].setText(val[i]);}
   btn(x,y+120,138,"Текущая позиция",()->{fields[0].setText(state.dimension());fields[1].setText(""+state.x());fields[2].setText(""+state.y());fields[3].setText(""+state.z());});
   btn(x+142,y+120,140,"На месте",()->{for(int i=0;i<4;i++)fields[i].setText("");});
   btn(x+75,y+146,130,"Сохранить старт",()->{try{
    String dim=fields[0].getText().strip(),ev=fields[4].getText().strip();
    var cfg=new LobbyState.Start(dim.isEmpty()?null:dim,dim.isEmpty()?0:Double.parseDouble(fields[1].getText()),dim.isEmpty()?0:Double.parseDouble(fields[2].getText()),dim.isEmpty()?0:Double.parseDouble(fields[3].getText()),ev.isEmpty()?null:ev);
    request("start-config",BoardNetwork.JSON.toJson(cfg));
   }catch(RuntimeException ex){error="Проверьте координаты и ID";}});return;
  }
  if(tab==2){int i=0;for(var level:List.of("easy","normal","hard")){
    var option=btn(x+64,y+22+i*23,156,List.of("Лёгкая","Нормальная","Сложная").get(i),()->request("difficulty",level));option.selected=state.balance()!=null&&level.equals(state.balance().preset());i++;}
   btn(x+64,y+95,156,"Своя…",()->{tab=3;clearAndInit();});return;}
  if(tab==3){
   var b=state.balance()==null?Balance.preset("normal"):state.balance();int[] values={b.newFuel(),b.oldFuel(),b.lampMinutes(),b.chargeMinutes(),b.torchMinutes(),b.fusePercent(),b.fuseSeconds(),b.stormSeconds(),b.snowSeconds()};
   for(int i=0;i<9;i++){fields[i]=addDrawableChild(new TextFieldWidget(textRenderer,x+(i/5)*144+88,y+18+(i%5)*22,46,18,Text.literal(labels[i])));fields[i].setMaxLength(5);fields[i].setText(""+values[i]);}
   btn(x,y+141,138,"К нормальной",()->request("difficulty","normal"));
   btn(x+144,y+141,138,"Сохранить",()->{try{int[] n=Arrays.stream(fields).mapToInt(t->Integer.parseInt(t.getText())).toArray();request("difficulty",BoardNetwork.JSON.toJson(new Balance("custom",n[0],n[1],n[2],n[3],n[4],n[5],n[6],n[7],n[8])));}catch(RuntimeException ex){error="Проверьте диапазоны";}});return;
  }
  if(!state.started()){
   if(me.equals(state.invited())){btn(x,y+43,134,"Принять приглашение",()->request("accept",""));btn(x+142,y+43,134,"Отклонить",()->request("decline",""));return;}
   if(state.partner()!=null){if(me.equals(state.host())||me.equals(state.partner())){
    btn(x,y+48,134,state.mine()?"Снять готовность":"Я готов",()->request("ready",""));btn(x+142,y+48,134,"Покинуть Duo",()->request("solo",""));}return;}
   if(state.host()==null||me.equals(state.host())){
    var list=state.online().stream().filter(p->!p.id().equals(me)).toList();
    for(int i=page*4;i<Math.min(page*4+4,list.size());i++){var who=list.get(i);btn(x+30,y+42+(i%4)*21,224,"Пригласить · "+who.name(),()->request("invite",who.id().toString()));}
    if(page>0)btn(x,y+130,24,"‹",()->{page--;clearAndInit();});if(page*4+4<list.size())btn(x+250,y+130,24,"›",()->{page++;clearAndInit();});
    if(me.equals(state.host()))btn(x+65,y+142,150,"Вернуться в Solo",()->request("solo",""));
   }
  }
 }
 @Override public void render(DrawContext d,int mx,int my,float dt){
  var box=LobbyUi.Box.centered(width,height,406,248);int x=box.x()+22,y=box.y()+30;
  LobbyUi.backdrop(d,width,height);LobbyUi.board(d,box);
  LobbyUi.accent(d,box.x()+17,box.y()+11,14,0xffbd3e3c);
  LobbyUi.fit(d,textRenderer,tab==0?"DUO / УЧАСТНИКИ":tab==1?"НАСТРОЙКА СТАРТА":tab==2?"УРОВЕНЬ СЛОЖНОСТИ":"РУЧНАЯ СЛОЖНОСТЬ",box.x()+27,box.y()+12,box.w()-51,LobbyUi.LIGHT,false,0);
  LobbyUi.divider(d,box.x()+17,box.y()+37,box.w()-34);
  LobbyUi.paper(d,x-9,y+12,Math.min(294,box.right()-x-7),160);
  if(tab==1){
   String[] lab={"Измерение","X","Y","Z","Событие"};
   for(int i=0;i<5;i++){
    LobbyUi.fit(d,textRenderer,lab[i],x+2,y+19+i*23,79,LobbyUi.INK_SOFT,false,0);
    if(fields[i]!=null)LobbyUi.plate(d,fields[i]);
   }
   LobbyUi.fit(d,textRenderer,error,x+2,y+166,270,0xffe48483,false,0);
  }else if(tab==2){
   LobbyUi.fit(d,textRenderer,"ВЫБЕРИ РЕЖИМ ВЫЖИВАНИЯ",x+12,y+20,270,LobbyUi.INK_SOFT,false,0);
   LobbyUi.fit(d,textRenderer,state.balance()==null?"ПРЕСЕТ: ПО МИРУ":"ПРЕСЕТ: "+state.balance().label(),x+12,y+153,260,LobbyUi.INK_SOFT,false,0);
  }else if(tab==3){
   for(int i=0;i<9;i++){
    LobbyUi.fit(d,textRenderer,labels[i],x+(i/5)*144,y+24+(i%5)*22,84,LobbyUi.INK_SOFT,false,0);
    if(fields[i]!=null)LobbyUi.plate(d,fields[i]);
   }
   LobbyUi.fit(d,textRenderer,error,x,y+132,270,0xffe48483,false,0);
  }else if(state!=null){
   LobbyUi.chalk(d,x+4,y+19,275,29);
   LobbyUi.accent(d,x+5,y+22,20,0xff537992);
   LobbyUi.caption(d,textRenderer,"FIRST",x+13,y+23,47,LobbyUi.LIGHT_DIM);
   LobbyUi.fit(d,textRenderer,state.host()==null?"Выбор ведущего":name(state.host()),x+65,y+23,186,LobbyUi.INK,false,0);
   LobbyUi.led(d,x+254,y+24,state.mine()?LobbyUi.LED_OK:LobbyUi.LED_WAIT);
   LobbyUi.chalk(d,x+4,y+51,275,29);
   LobbyUi.accent(d,x+5,y+54,20,0xffbd514c);
   LobbyUi.caption(d,textRenderer,"SECOND",x+13,y+55,48,LobbyUi.LIGHT_DIM);
   LobbyUi.fit(d,textRenderer,state.partner()!=null?name(state.partner()):state.invited()!=null?"Приглашение отправлено":"Напарник не выбран",x+65,y+55,186,LobbyUi.INK,false,0);
   LobbyUi.led(d,x+254,y+56,state.partner()!=null&&state.other()?LobbyUi.LED_OK:LobbyUi.LED_WAIT);
   String status=state.partner()!=null?"ГОТОВНОСТЬ "+((state.mine()?1:0)+(state.other()?1:0))+"/2":
     state.invited()!=null?"ОЖИДАНИЕ ПОДТВЕРЖДЕНИЯ":"SOLO";
   LobbyUi.fit(d,textRenderer,status,x+13,y+91,253,LobbyUi.INK_SOFT,false,0);
   if(state.started())LobbyUi.fit(d,textRenderer,"ИСТОРИЯ УЖЕ ЗАПУЩЕНА",x+13,y+154,253,LobbyUi.LIGHT_DIM,false,0);
  }
  LobbyUi.caption(d,textRenderer,"WHITEOUT / LOBBY CONTROL",box.x()+17,box.bottom()-14,box.w()-34,LobbyUi.LIGHT_DIM);
  super.render(d,mx,my,dt);
 }
 @Override public void close(){client.setScreen(new BoardScreen(view));}
}
