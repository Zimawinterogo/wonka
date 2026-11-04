package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.StoryStoreReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;
import static java.nio.file.StandardCopyOption.*;

/** A validated backup may be staged only while the live world is locked read-only. */
public final class StoryRecovery {
 private StoryRecovery(){}
 public static String status(Store store,Path path){
  var backup=path.resolveSibling(path.getFileName()+".bak");
  return "Store: "+(store.error==null?"writable":"read-only: "+store.error)+"; backup: "+
   (!Files.isRegularFile(backup)?"missing":StoryStoreReader.open(backup).error==null?"valid":"invalid");
 }
 public static void recover(Store live,Path path)throws IOException{
  if(live.error==null||Files.exists(path)&&StoryStoreReader.open(path).error==null)
   throw new IOException("Recovery requires a damaged or missing primary and a read-only Store");
  Path backup=path.resolveSibling(path.getFileName()+".bak");
  if(!Files.isRegularFile(backup))throw new IOException("No backup");
  Path stage=Files.createTempFile(path.getParent(),"whiteout-recovery-",".json");
  try{
   Files.copy(backup,stage,REPLACE_EXISTING);
   Store candidate=StoryStoreReader.open(stage);
   if(candidate.error!=null)throw new IOException("Backup failed validation: "+candidate.error);
   try(var channel=FileChannel.open(stage,StandardOpenOption.WRITE)){channel.force(true);}
   if(Files.exists(path))Files.copy(path,path.resolveSibling(path.getFileName()+".damaged-"+UUID.randomUUID()));
   Files.move(stage,path,ATOMIC_MOVE,REPLACE_EXISTING);
  }finally{Files.deleteIfExists(stage);}
 }
}
