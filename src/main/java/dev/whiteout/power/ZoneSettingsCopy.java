package dev.whiteout.power;

import com.google.gson.*;
import dev.whiteout.*;
import dev.whiteout.net.WhiteoutNetwork;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Explicit allowlist: identity, bounds, exclusions and fixed event coordinates never enter clipboard. */
public final class ZoneSettingsCopy {
 private ZoneSettingsCopy(){}
 public static final String[][] FIELDS={
  {"fog","snow","snowDistance","visibility","blizzard","transition"},
  {"volume","ambientVolume","music","delay","repeat","ambience","eerie","eerieVolume","eerieInterval"},
  {"cold"},
  {"strangerTracks","trailingSteps","shelterNoise","lull","eyes","foreignBreath","outsideKnock","branch","silhouette","blackout"}
 };
 private record Clipboard(JsonObject settings,JsonObject atmosphere,String dimension){}
 private static final Map<ServerPlayerEntity,Clipboard> clips=new WeakHashMap<>();
 private static final Set<String> EVENT_FIELDS=Set.of("enabled","repeat","once","chance","pityEnabled","pityBonusPerMiss","pityMaxChance","delay","interval");
 public static JsonObject select(JsonObject zone,int mask){
  JsonObject result=new JsonObject();
  for(int i=0;i<FIELDS.length;i++)if((mask&(1<<i))!=0)for(String key:FIELDS[i])if(zone.has(key)){
   if(i<3)result.add(key,zone.get(key).deepCopy());
   else if(zone.get(key).isJsonObject()){
    JsonObject event=new JsonObject();for(String field:EVENT_FIELDS)if(zone.getAsJsonObject(key).has(field))event.add(field,zone.getAsJsonObject(key).get(field).deepCopy());result.add(key,event);
   }
  }
  return result;
 }
 public static JsonObject merge(JsonObject target,JsonObject source,int mask){
  JsonObject result=target.deepCopy();select(source,mask).entrySet().forEach(e->{
   if(e.getValue().isJsonObject()&&result.has(e.getKey())&&result.get(e.getKey()).isJsonObject())e.getValue().getAsJsonObject().entrySet().forEach(f->result.getAsJsonObject(e.getKey()).add(f.getKey(),f.getValue()));
   else result.add(e.getKey(),e.getValue());
  });return result;
 }
 private static boolean handle(ServerPlayerEntity p,EditorNetwork.Request r){
  if(!r.action().equals("copy")&&!r.action().equals("paste"))return false;
  Zone target=EditorHistory.find(p,r.id());if(target==null)throw new IllegalStateException("Сначала сохраните и выберите зону.");
  String dim=p.getWorld().getRegistryKey().getValue().toString();
  if(r.action().equals("copy")){
   clips.put(p,new Clipboard(select(Store.GSON.toJsonTree(target).getAsJsonObject(),15),WorldAtmosphere.editorSnapshot(p.getServer(),target.id),dim));
   p.sendMessage(Text.literal("Настройки сохранённой зоны скопированы: "+target.name),false);return true;
  }
  Clipboard clip=clips.get(p);if(clip==null||!clip.dimension.equals(dim))throw new IllegalStateException("Буфер пуст. Скопируйте настройки зоны заново.");
  int mask=r.flags();if(mask<=0||(mask&~31)!=0)throw new IllegalArgumentException("Выберите группы параметров.");
  if((mask&16)!=0&&(!p.hasPermissionLevel(2)||!target.shelter))throw new IllegalStateException("Атмосфера здания требует зону-укрытие и права оператора.");
  JsonObject before=EditorHistory.capture(p,target.id),after=before.deepCopy();
  JsonObject merged=merge(before.getAsJsonObject("zone"),clip.settings,mask);
  Zone changed=Store.GSON.fromJson(merged,Zone.class);changed.validate();
  after.add("zone",Store.GSON.toJsonTree(changed));
  if((mask&16)!=0)after.add("atmosphere",clip.atmosphere.deepCopy());
  EditorHistory.apply(p,target.id,before,after,new EditorDelta(before,after));
  EditorHistory.record(p,target.id,before,(mask&16)!=0);
  WhiteoutNetwork.syncAll(p.getServer());EditorNetwork.status(p,true,false,target.id);
  p.sendMessage(Text.literal("Настройки применены: "+target.name),false);return true;
 }
 public static void register(){
  EditorNetwork.extensions.add(ZoneSettingsCopy::handle);
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->clips.remove(h.getPlayer()));
  net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((p,a,b)->clips.remove(p));
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s->clips.clear());
 }
}
