package dev.whiteout.power;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Own file only. Bad/unknown data is preserved and blocks writes until repaired externally. */
public final class CrisisSettings {
 private final Path path;
 private boolean enabled,blocked;
 private String error="";
 private CrisisSettings(Path path){this.path=path;}
 public static CrisisSettings load(Path path){
  CrisisSettings s=new CrisisSettings(path);
  if(Files.notExists(path))return s;
  try{s.enabled=decode(path);}catch(Exception e){s.blocked=true;s.error=e.getClass().getSimpleName()+": invalid crisis settings";}
  return s;
 }
 private static boolean decode(Path path)throws IOException {
  if(Files.size(path)>4096)throw new IOException("Oversized settings");
  try(JsonReader reader=new JsonReader(new StringReader(Files.readString(path,StandardCharsets.UTF_8)))){
   reader.setLenient(false);reader.beginObject();boolean version=false,flag=false,value=false;
   while(reader.hasNext()){
    String name=reader.nextName();
    if(name.equals("version")&&!version){
     if(reader.peek()!=JsonToken.NUMBER||!reader.nextString().equals("1"))throw new IOException("Unknown version");
     version=true;
    }else if(name.equals("enabled")&&!flag){
     if(reader.peek()!=JsonToken.BOOLEAN)throw new IOException("Boolean required");
     value=reader.nextBoolean();flag=true;
    }else throw new IOException("Unknown or duplicate field");
   }
   reader.endObject();
   if(!version||!flag||reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Incomplete schema or trailing data");
   return value;
  }catch(RuntimeException e){throw new IOException("Invalid settings",e);}
 }
 public boolean enabled(){return enabled&&!blocked;}
 public boolean blocked(){return blocked;}
 public String error(){return error;}
 public void save(boolean value)throws IOException {
  if(blocked)throw new IOException("Existing configuration is invalid; preserved for repair");
  // Revalidate an externally changed file before replacing it.
  if(Files.exists(path))try{decode(path);}catch(IOException e){blocked=true;error="Configuration changed or corrupted";throw e;}
  Path parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);
  Path temporary=Files.createTempFile(parent,".whiteout-crisis-",".tmp");
  try{
   Files.writeString(temporary,"{\"version\":1,\"enabled\":"+value+"}\n",StandardCharsets.UTF_8);
   if(decode(temporary)!=value)throw new IOException("Validation mismatch");
   try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
   enabled=value;
  }finally{Files.deleteIfExists(temporary);}
 }
}
