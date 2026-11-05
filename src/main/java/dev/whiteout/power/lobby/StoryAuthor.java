package dev.whiteout.power.lobby;

import com.google.gson.*;
import dev.whiteout.Store;
import dev.whiteout.power.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One revision-checked server definition per edit. The only path is the world's existing event catalogue. */
public final class StoryAuthor{
 /** Formatting must never make EditBoxWidget silently truncate a valid definition. */
 public static String editorText(String json){
  var value=JsonParser.parseString(json);var pretty=new GsonBuilder().setPrettyPrinting().create().toJson(value);
  if(pretty.length()<=16000)return pretty;
  var compact=value.toString();if(compact.length()>16000)throw new IllegalArgumentException("Событие превышает лимит редактора");
  return compact;
 }
 public record Page(UUID token,String key,String revision,String body,String message) implements CustomPayload{
  public static final Id<Page> ID=new Id<>(PowerDevices.id("board_story_editor"));
  public static final PacketCodec<RegistryByteBuf,Page> CODEC=PacketCodec.ofStatic((b,p)->{b.writeUuid(p.token);b.writeString(p.key,128);b.writeString(p.revision,64);b.writeString(p.body,16000);b.writeString(p.message,160);},b->new Page(b.readUuid(),b.readString(128),b.readString(64),b.readString(16000),b.readString(160)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 private static final Map<ServerPlayerEntity,Long> recent=new WeakHashMap<>();
 public static void register(){
  PayloadTypeRegistry.playS2C().register(Page.ID,Page.CODEC);PayloadTypeRegistry.playC2S().register(Page.ID,Page.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Page.ID,(m,c)->c.server().execute(()->{
   var p=c.player();if(!BoardNetwork.access(p,m.token,true))return;var state=LobbyState.get(Store.get(c.server()));if(state!=null&&state.started())return;
   long now=System.nanoTime();if(now-recent.getOrDefault(p,now-1000000000L)<150000000L)return;recent.put(p,now);
   try{
    var path=c.server().getSavePath(WorldSavePath.ROOT).resolve("whiteout-story-events.json");
    var response=update(path,m);if(!m.body.isEmpty()){StoryAuthority.reload(c.server());Party.contentChanged(c.server());}ServerPlayNetworking.send(p,response);
   }catch(Exception ex){String msg=Objects.toString(ex.getMessage(),"Ошибка определения");ServerPlayNetworking.send(p,new Page(m.token,m.key,m.revision,"",msg.substring(0,Math.min(150,msg.length()))));}
  }));
 }
 public static Page update(Path path,Page m)throws Exception{
  if(Files.exists(path)&&Files.size(path)>StoryEventCatalog.MAX_BYTES)throw new IllegalArgumentException("Каталог слишком большой");
  byte[] before=Files.exists(path)?Files.readAllBytes(path):new byte[0];String rev=PhotoTransfer.hash(before);
  var root=JsonParser.parseString(before.length==0?"{\"schema\":4,\"events\":[]}":new String(before,StandardCharsets.UTF_8)).getAsJsonObject();
  if(root.get("schema").getAsInt()<1||root.get("schema").getAsInt()>4)throw new IllegalArgumentException("Неизвестная версия каталога");
  var entries=root.getAsJsonArray("events");
  if(m.key.isBlank()&&m.body.isEmpty()){var keys=new JsonArray();entries.forEach(el->keys.add(el.getAsJsonObject().get("key")));String list=keys.toString();if(list.length()>16000)throw new IllegalArgumentException("Введите конкретный ключ");
   return new Page(m.token,"",rev,list,"");}
  if(!m.key.matches("[a-zA-Z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("Введите ID события");
  int index=-1;for(int i=0;i<entries.size();i++)if(m.key.equals(entries.get(i).getAsJsonObject().get("key").getAsString()))index=i;
  if(m.body.isEmpty()){String value=index<0?"":entries.get(index).toString();if(value.length()>16000)throw new IllegalArgumentException("Событие превышает лимит редактора");return new Page(m.token,m.key,rev,value,"");}
  if(!m.revision.equals(rev))throw new IllegalStateException("Каталог изменился: загрузите событие заново");
  var value=JsonParser.parseString(m.body).getAsJsonObject();
  if(!m.key.equals(value.get("key").getAsString()))throw new IllegalArgumentException("ID не совпадает");
  if(index<0)entries.add(value);else entries.set(index,value);root.addProperty("schema",4);
  var stage=Files.createTempFile(path.getParent(),".whiteout-story-",".tmp");
  try{
   byte[] bytes=new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8);Files.write(stage,bytes);
   var checked=new StoryEventCatalog(stage);if(checked.invalid())throw new IllegalArgumentException(checked.error());
   if(before.length>0)Files.copy(path,path.resolveSibling(path.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
   Files.move(stage,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
   return new Page(m.token,m.key,PhotoTransfer.hash(bytes),value.toString(),"Сохранено");
  }finally{Files.deleteIfExists(stage);}
 }
}
