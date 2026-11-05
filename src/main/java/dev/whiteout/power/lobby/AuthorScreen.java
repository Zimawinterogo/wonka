package dev.whiteout.power.lobby;

import com.google.gson.*;
import dev.whiteout.power.SharpScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

final class AuthorScreen extends SharpScreen{
 private final BoardNetwork.View board;private TextFieldWidget key;private EditBoxWidget body;
 private String rev="",message="";private int page;private String[] keys={};
 AuthorScreen(BoardNetwork.View board){super(Text.literal("Редактор сюжета"));this.board=board;}
 static void register(){ClientPlayNetworking.registerGlobalReceiver(StoryAuthor.Page.ID,(m,c)->c.client().execute(()->{
  if(c.client().currentScreen instanceof AuthorScreen screen&&screen.board.token().equals(m.token()))screen.receive(m);
 }));}
 private void receive(StoryAuthor.Page m){
  rev=m.revision();message=m.message();
  if(m.key().isEmpty()&&!m.body().isEmpty()){keys=new Gson().fromJson(m.body(),String[].class);if(keys.length>0)key.setText(keys[0]);}
  else if(!m.body().isEmpty()){key.setText(m.key());body.setText(new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(m.body())));}
 }
 private void send(String id,String json){ClientPlayNetworking.send(new StoryAuthor.Page(board.token(),id,rev,json,""));}
 private void btn(int x,int y,int w,String name,Runnable action){addDrawableChild(LobbyUi.button(Text.literal(name),x,y,w,19,action));}
 private void preset(boolean secret,boolean ending){
  try{JsonObject event=body.getText().isBlank()?new JsonObject():JsonParser.parseString(body.getText()).getAsJsonObject();event.addProperty("key",key.getText());event.addProperty("once",true);
   if(!event.has("conditions"))event.add("conditions",new JsonArray());if(!event.has("actions"))event.add("actions",new JsonArray());
   if(!event.has("area")&&!event.has("trigger")){var mc=client.player;JsonObject area=new JsonObject();area.addProperty("dimension",mc.getWorld().getRegistryKey().getValue().toString());area.addProperty("x",mc.getX());area.addProperty("y",mc.getY());area.addProperty("z",mc.getZ());area.addProperty("radius",4);event.add("area",area);}
   var n=event.has("narrative")?event.getAsJsonObject("narrative"):new JsonObject();event.add("narrative",n);
   n.addProperty("mode","DUO");n.addProperty("actor",secret||ending?"SECOND":"ANY");n.addProperty("recipient",secret?"SECOND":"ANY");
   n.addProperty("visibility",secret?"SECRET":"SHARED");event.addProperty("scope",secret?"PLAYER":"WORLD");
   if(ending)n.add("endings",JsonParser.parseString("[{\"id\":\"ending_id\",\"title\":\"Название финала\",\"priority\":1,\"event\":\"server_ending_event\"}]"));
   body.setText(new GsonBuilder().setPrettyPrinting().create().toJson(event));
  }catch(RuntimeException ex){message="Исправьте JSON перед применением шаблона";}
 }
 private void cycle(String field,String... values){
  try{var event=JsonParser.parseString(body.getText()).getAsJsonObject();var policy=event.getAsJsonObject("narrative");var options=java.util.List.of(values);
   String selected=options.get((options.indexOf(policy.has(field)?policy.get(field).getAsString():"")+1)%options.size());policy.addProperty(field,selected);
   if(field.equals("visibility")){boolean shared=selected.equals("SHARED");event.addProperty("scope",shared?"WORLD":"PLAYER");if(!shared&&policy.get("recipient").getAsString().equals("ANY"))policy.addProperty("recipient","SECOND");}
   if(policy.get("visibility").getAsString().equals("SECRET")){if(field.equals("actor"))policy.addProperty("recipient",selected);else policy.addProperty("actor",policy.get("recipient").getAsString());}
   body.setText(new GsonBuilder().setPrettyPrinting().create().toJson(event));message=field+" = "+selected;
  }catch(RuntimeException ex){message="Выберите шаблон или загрузите событие с настройками ролей";}
 }
 @Override protected void init(){
  boolean firstOpen=key==null;
  String savedKey=firstOpen?"":key.getText(),savedBody=body==null?"":body.getText();
  int x=10,w=width-20;key=addDrawableChild(new TextFieldWidget(textRenderer,x,22,w-99,19,Text.literal("ID события")));key.setMaxLength(128);
  btn(x+w-95,22,23,"›",()->{if(keys.length>0)key.setText(keys[++page%keys.length]);});btn(x+w-70,22,70,"Загрузить",()->send(key.getText(),""));
  body=addDrawableChild(new EditBoxWidget(textRenderer,x,46,w,height-117,Text.literal("Определение события JSON"),Text.literal("Событие")));body.setMaxLength(16000);
  int y=height-66;btn(x,y,66,"Общее",()->preset(false,false));btn(x+69,y,70,"Секретное",()->preset(true,false));btn(x+142,y,61,"Финал",()->preset(false,true));
  btn(x,y+20,63,"Режим",()->cycle("mode","BOTH","SOLO","DUO"));btn(x+65,y+20,60,"Автор",()->cycle("actor","ANY","FIRST","SECOND"));btn(x+127,y+20,81,"Получатель",()->cycle("recipient","ANY","FIRST","SECOND"));btn(x+210,y+20,80,"Видимость",()->cycle("visibility","SHARED","PERSONAL","SECRET"));
  key.setText(savedKey);body.setText(savedBody);
  btn(x+w-84,height-20,84,"Сохранить",()->send(key.getText(),body.getText()));if(firstOpen)send("","");
 }
 @Override public void render(DrawContext d,int mx,int my,float dt){LobbyUi.backdrop(d,width,height);LobbyUi.board(d,new LobbyUi.Box(3,3,width-6,height-6));
  LobbyUi.chalk(d,8,42,width-16,height-108);
  LobbyUi.fit(d,textRenderer,"СОБЫТИЯ / ЗАДАНИЯ / ВЕТКИ",12,9,width-24,LobbyUi.LIGHT,false,0);
  LobbyUi.fit(d,textRenderer,message,12,height-17,width-115,LobbyUi.LIGHT,false,0);
  super.render(d,mx,my,dt);}
 @Override public void close(){client.setScreen(new BoardScreen(board));}
}
