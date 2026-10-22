package dev.whiteout.power.backpack;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

/** Client-only settings, no player/world data. Invalid files are never overwritten. */
public final class BackpackPreferences {
 private final Path path;
 private boolean enabled,animation=true;
 private String error;
 private JsonObject extra=new JsonObject();
 public BackpackPreferences(Path path){this.path=path;}
 public boolean enabled(){return enabled;}
 public boolean animation(){return animation;}
 public String error(){return error;}
 public void load(){
  enabled=false;animation=true;error=null;extra=new JsonObject();
  try{
   if(!Files.exists(path))return;
   var json=JsonParser.parseString(Files.readString(path));
   if(!json.isJsonObject())throw new IOException("Expected JSON object");
   JsonObject object=json.getAsJsonObject();
   boolean nextEnabled=flag(object,"enabled",false),nextAnimation=flag(object,"animation",true);
   enabled=nextEnabled;animation=nextAnimation;extra=object;
  }catch(Exception ex){error=ex.toString();}
 }
 private static boolean flag(JsonObject object,String key,boolean fallback)throws IOException{
  if(!object.has(key))return fallback;
  var value=object.get(key);
  if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IOException("Expected boolean: "+key);
  return value.getAsBoolean();
 }
 public void setEnabled(boolean value)throws IOException{
  save(value,animation);
 }
 public void setAnimation(boolean value)throws IOException{
  save(enabled,value);
 }
 private void save(boolean nextEnabled,boolean nextAnimation)throws IOException{
  if(error!=null)throw new IOException("Invalid whiteout-backpack.json; original preserved: "+error);
  JsonObject next=extra.deepCopy();next.addProperty("enabled",nextEnabled);next.addProperty("animation",nextAnimation);
  Files.createDirectories(path.toAbsolutePath().getParent());
  Path temporary=Files.createTempFile(path.toAbsolutePath().getParent(),"whiteout-backpack-",".tmp");
  try{
   Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(next)+"\n");
   try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   catch(AtomicMoveNotSupportedException unsupported){
    if(Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
    Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);
   }
   enabled=nextEnabled;animation=nextAnimation;extra=next;
  }finally{Files.deleteIfExists(temporary);}
 }
}
