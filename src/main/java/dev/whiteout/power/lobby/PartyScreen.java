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
 private void btn(int x,int y,int w,String t,Runnable r){addDrawableChild(ButtonWidget.builder(Text.literal(t),b->r.run()).dimensions(x,y,w,19).build());}
 private String name(UUID id){return state.online().stream().filter(p->p.id().equals(id)).map(Party.Person::name).findFirst().orElse("офлайн");}
 @Override protected void init(){
  if(state==null||!view.token().equals(token))return;
  int x=width/2-142,y=height/2-85;var me=client.player.getUuid();
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
    btn(x+64,y+22+i*23,156,List.of("Лёгкая","Нормальная","Сложная").get(i),()->request("difficulty",level));i++;}
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
  int x=width/2-142,y=height/2-85;d.fill(0,0,width,height,0xf333291f);d.fill(x-8,y-15,x+292,y+171,0xffd8c9a6);
  if(tab==1){String[] labels={"Измерение","X","Y","Z","Событие"};for(int i=0;i<5;i++)d.drawText(textRenderer,labels[i],x,y+6+i*23,0x382c20,false);d.drawText(textRenderer,error,x,y+168,0x97433b,false);}
  else if(tab==2)d.drawText(textRenderer,"Общая сложность · "+(state.balance()==null?"по миру":state.balance().preset()),x+12,y+4,0x382c20,false);
  else if(tab==3){for(int i=0;i<9;i++)d.drawText(textRenderer,labels[i],x+(i/5)*144,y+23+(i%5)*22,0x382c20,false);d.drawText(textRenderer,error,x,y+128,0x904533,false);}
  else if(state!=null){d.drawText(textRenderer,state.host()==null?"Solo · пригласите игрока для Duo":"№1 · "+name(state.host()),x+8,y,0x382c20,false);
   if(state.partner()!=null)d.drawText(textRenderer,"№2 · "+name(state.partner())+"  |  Готовность: "+((state.mine()?1:0)+(state.other()?1:0))+"/2",x+8,y+16,0x382c20,false);
   else if(state.invited()!=null)d.drawText(textRenderer,"Приглашение · "+name(state.invited()),x+8,y+16,0x382c20,false);}
  super.render(d,mx,my,dt);
 }
 @Override public void close(){client.setScreen(new BoardScreen(view));}
}
