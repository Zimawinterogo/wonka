import dev.whiteout.power.ZoneMusicContinuity;
import java.util.Objects;
public class ZoneMusicContinuityTest {
 public static void main(String[] args) {
  for(int slot=1;slot<=16;slot++){
   int physical=1+(slot-1)%7;
   var expected="music:"+physical;
   var actual=ZoneMusicContinuity.canonicalKey("music:"+slot);
   if(!Objects.equals(expected,actual))throw new AssertionError(slot+" maps to "+actual+" not "+expected);
  }
  for(var key:new String[]{"", "winter_wind", "music:bad", "music:17", "other:8"}){
   if(!Objects.equals(key,ZoneMusicContinuity.canonicalKey(key)))throw new AssertionError("Unexpected normalization of "+key);
  }
  if(ZoneMusicContinuity.canonicalKey(null)!=null)throw new AssertionError("null key changed");
  System.out.println("PASS: music continuity for all 16 saved slots and non-music keys");
 }
}
