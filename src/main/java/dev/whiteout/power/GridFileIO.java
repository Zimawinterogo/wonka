package dev.whiteout.power;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Write JSON next to its destination, then replace it. Never truncate the live save. */
public final class GridFileIO {
 private GridFileIO(){}
 public static void writeAtomic(Path destination,String json) throws IOException {
  Path parent=destination.toAbsolutePath().getParent();
  Files.createDirectories(parent);
  Path temporary=Files.createTempFile(parent,destination.getFileName().toString()+"-", ".tmp");
  try{
   Files.writeString(temporary,json,StandardCharsets.UTF_8);
   try{Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
   catch(AtomicMoveNotSupportedException unsupported){
    Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);
   }
  }finally{
   // The name is unique to this save; never remove another writer's .tmp file.
   Files.deleteIfExists(temporary);
  }
 }
}
