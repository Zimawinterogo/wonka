import dev.whiteout.power.GridFileIO;
import java.nio.file.*;
import java.io.IOException;
import java.util.concurrent.*;

/** Real filesystem tests: fail before replacement; no partially written live saves. */
public final class GridFileIOTest {
 static int assertions;
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);assertions++;}
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory("whiteout-grid-save-test-");
  try{
   Path save=dir.resolve("whiteout-grid.json");
   GridFileIO.writeAtomic(save,"{\"name\":\"старый мир\",\"generation\":1}");
   String original=Files.readString(save);
   check(original.contains("старый мир"),"unicode legacy save");
   GridFileIO.writeAtomic(save,"{\"generation\":2}");
   check(Files.readString(save).equals("{\"generation\":2}"),"update replaces full JSON");
   Files.writeString(save,"{\"legacy\":true}");
   String preserved=Files.readString(save);
   // A directory cannot be replaced by a regular file; this must not erase the sibling save.
   Path impossible=dir.resolve("destination-is-a-directory");Files.createDirectory(impossible);
   boolean rejected=false;
   try{GridFileIO.writeAtomic(impossible,"bad");}catch(IOException expected){rejected=true;}
   check(rejected,"invalid replacement fails loudly");
   check(Files.isDirectory(impossible),"existing target directory preserved");
   check(Files.readString(save).equals(preserved),"valid legacy save preserved on failed other save");
   ExecutorService pool=Executors.newFixedThreadPool(4);
   try{
    java.util.List<Future<?>> futures=new java.util.ArrayList<>();
    for(int i=0;i<24;i++){
     final int n=i;futures.add(pool.submit(()->{
      try{GridFileIO.writeAtomic(save,"{\"generation\":"+n+",\"payload\":\""+"x".repeat(4096)+"\"}");}
      catch(IOException failure){throw new RuntimeException(failure);}
     }));
    }
    for(Future<?> f:futures)f.get();
   }finally{pool.shutdownNow();}
   String result=Files.readString(save);
   check(result.startsWith("{\"generation\":")&&result.endsWith("\"}"),"complete document after concurrent saves");
   check(result.contains("x".repeat(4096)),"full payload after concurrent saves");
   try(var files=Files.list(dir)){
    check(files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")),"no temporary save leaks");
   }
  }finally{
   try(var stream=Files.walk(dir)){
    stream.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new RuntimeException(e);}});
   }
  }
  System.out.println("PASS GridFileIOTest: "+assertions+" filesystem / failure / concurrent assertions");
 }
}
