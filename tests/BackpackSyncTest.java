import dev.whiteout.power.backpack.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import io.netty.buffer.Unpooled;
import java.util.UUID;

public final class BackpackSyncTest {
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 public static void main(String[] args){
  check(BackpackPosePolicy.allowed(true,false,false,true,false,false,false,false,true),"grounded survival can sit");
  boolean[] flags={true,false,false,true,false,false,false,false,true};
  for(int i=0;i<flags.length;i++){
   flags[i]=!flags[i];
   check(!BackpackPosePolicy.allowed(flags[0],flags[1],flags[2],flags[3],flags[4],flags[5],flags[6],flags[7],flags[8]),"reject invalid state "+i);
   flags[i]=!flags[i];
  }
  check(BackpackPosePolicy.validLease(60,0,true,0),"lease boundary");
  check(!BackpackPosePolicy.validLease(61,0,true,0),"lost close expires");
  check(!BackpackPosePolicy.validLease(10,0,false,0),"dimension reset");
  check(!BackpackPosePolicy.validLease(10,0,true,.041),"movement interrupts");
  check(!BackpackPosePolicy.validLease(0,10,true,0),"old server clock rejected");
  check(BackpackPosePolicy.blend(0,1,0)==0&&BackpackPosePolicy.blend(0,1,350)==1,"sit interpolation");
  check(BackpackPosePolicy.blend(1,0,350)==0,"stand restores");
  check(BackpackPosePolicy.blend(.45f,0,0)==.45f,"reversal continuous");
  check(BackpackPosePolicy.blend(0,1,3600000)==1,"holds indefinitely");
  var buffer=new RegistryByteBuf(Unpooled.buffer(),DynamicRegistryManager.EMPTY);
  try{
   for(boolean open:new boolean[]{true,false}){
    var request=new BackpackNetwork.Request(open);
    BackpackNetwork.Request.CODEC.encode(buffer,request);
    check(BackpackNetwork.Request.CODEC.decode(buffer).equals(request),"request roundtrip");
    var state=new BackpackNetwork.State(UUID.randomUUID(),Identifier.of("minecraft","overworld"),open,1100);
    BackpackNetwork.State.CODEC.encode(buffer,state);
    check(BackpackNetwork.State.CODEC.decode(buffer).equals(state),"viewer snapshot roundtrip");
   }
  }finally{buffer.release();}
  System.out.println("PASS BackpackSyncTest: codec roundtrips, eligibility, lease expiry, dimension/movement cleanup, smooth sit/stand");
 }
}
