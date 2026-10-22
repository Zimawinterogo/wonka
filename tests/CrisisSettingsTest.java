package dev.whiteout.power;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class CrisisSettingsTest {
 static int checks;
 static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  Path root=Files.createTempDirectory("whiteout-crisis-test-");Path file=root.resolve("whiteout-winter-crisis.json");
  try{
   CrisisSettings settings=CrisisSettings.load(file);ok(!settings.enabled()&&!settings.blocked()&&!Files.exists(file),"absent defaults off without writes");
   settings.save(true);ok(CrisisSettings.load(file).enabled(),"enable survives restart");
   settings.save(false);ok(!CrisisSettings.load(file).enabled(),"disable survives restart");
   for(String bad:List.of("{broken","null","[]","{}","{\"version\":2,\"enabled\":true}","{\"version\":1,\"enabled\":\"true\"}","{\"version\":1,\"enabled\":null}","x".repeat(4097),
     "{version:1,enabled:true}","{\"version\":1,\"enabled\":false,\"enabled\":true}","{\"version\":1,\"enabled\":true} trailing")){
    Files.writeString(file,bad);settings=CrisisSettings.load(file);
    ok(settings.blocked()&&!settings.enabled(),"corrupt/unknown schema disabled");
    boolean denied=false;try{settings.save(false);}catch(IOException expected){denied=true;}
    ok(denied&&Files.readString(file).equals(bad),"invalid bytes preserved");
   }
   Files.writeString(file,"{\"version\":1,\"enabled\":true}");settings=CrisisSettings.load(file);
   Files.writeString(file,"externally corrupted");boolean denied=false;try{settings.save(false);}catch(IOException expected){denied=true;}
   ok(denied&&settings.blocked()&&Files.readString(file).equals("externally corrupted"),"external corruption not overwritten");
   Path parentFile=root.resolve("not-a-directory");Files.writeString(parentFile,"preserve");
   settings=CrisisSettings.load(parentFile.resolve("config.json"));denied=false;try{settings.save(true);}catch(IOException expected){denied=true;}
   ok(denied&&Files.readString(parentFile).equals("preserve"),"I/O failure preserves unrelated file");
   try(var files=Files.list(root)){ok(files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")),"temporary files cleaned");}
   System.out.println("CrisisSettingsTest PASS: "+checks+" temporary-directory checks; no user saves accessed");
  }finally{
   try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
  }
 }
}
