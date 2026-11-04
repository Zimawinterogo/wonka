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
 private void btn(int x,int y,int w,String name,Runnable action){addDrawableChild(ButtonWidget.builder(Text.literal(name),b->action.run()).dimensions(x,y,w,19).build());}
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
 @Override protected void init(){
  int x=10,w=width-20;key=addDrawableChild(new TextFieldWidget(textRenderer,x,22,w-99,19,Text.literal("ID события")));key.setMaxLength(128);
  btn(x+w-95,22,23,"›",()->{if(keys.length>0)key.setText(keys[++page%keys.length]);});btn(x+w-70,22,70,"Загрузить",()->send(key.getText(),""));
  body=addDrawableChild(new EditBoxWidget(textRenderer,x,46,w,height-111,Text.literal("Определение события JSON"),Text.literal("Событие")));body.setMaxLength(16000);
  int y=height-61;btn(x,y,66,"Общее",()->preset(false,false));btn(x+69,y,70,"Секретное",()->preset(true,false));btn(x+142,y,61,"Финал",()->preset(false,true));
  btn(x+w-84,height-25,84,"Сохранить",()->send(key.getText(),body.getText()));send("","");
 }
 @Override public void render(DrawContext d,int mx,int my,float dt){d.fill(0,0,width,height,0xff30271e);d.drawTextWithShadow(textRenderer,"События · задания · сюжетные ветки",10,7,0xffe4d4af);d.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(message,width-120),10,height-19,0xffe4d4af);super.render(d,mx,my,dt);}
 @Override public void close(){client.setScreen(new BoardScreen(board));}
}
