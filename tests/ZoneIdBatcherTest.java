import dev.whiteout.power.ZoneIdBatcher;
import java.util.*;
public class ZoneIdBatcherTest {
 public static void main(String[] args){
  List<String> ids=new ArrayList<>();
  for(int i=0;i<15000;i++)ids.add("zone-"+i);
  List<String> chunks=ZoneIdBatcher.batches(ids);
  if(chunks.size()<10)throw new AssertionError("Expected multiple packets");
  Set<String> decoded=new HashSet<>();
  for(String chunk:chunks){
   if(chunk.length()>ZoneIdBatcher.MAX_CHARS)throw new AssertionError("packet limit exceeded");
   decoded.addAll(Arrays.asList(chunk.split("\n")));
  }
  if(decoded.size()!=ids.size()||!decoded.containsAll(ids))throw new AssertionError("zone lost");
  System.out.println("ZoneIdBatcherTest PASS ("+chunks.size()+" packets)");
 }
}
