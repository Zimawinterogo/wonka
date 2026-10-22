import dev.whiteout.power.SnapshotChunks;
import java.nio.charset.StandardCharsets;
public class SnapshotChunksTest {
 public static void main(String[] args){
  for(String input:new String[]{"", "я".repeat(70000), "x".repeat(15999)+"\uD83C\uDF28"+"雪".repeat(200000)}){
   var chunks=SnapshotChunks.split(input);var r=new SnapshotChunks.Receiver();String result=null;
   for(int i=0;i<chunks.size();i++){
    String wire=new String(chunks.get(i).getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);
    result=r.accept(i,chunks.size(),wire);
    if(i+1<chunks.size()&&result!=null)throw new AssertionError("Partial snapshot published");
   }
   if(!input.equals(result))throw new AssertionError("Roundtrip failed");
  }
  var r=new SnapshotChunks.Receiver();r.accept(0,3,"a");
  try{r.accept(2,3,"c");throw new AssertionError("Missing part accepted");}catch(IllegalArgumentException expected){}
  if(!"new".equals(r.accept(0,1,"new")))throw new AssertionError("Recovery failed");
  if(!SnapshotChunks.split("x".repeat(16000*256)).isEmpty())throw new AssertionError("Size limit");
  System.out.println("SnapshotChunks: large multilingual payloads, surrogate boundaries, loss, recovery and bounds passed");
 }
}
