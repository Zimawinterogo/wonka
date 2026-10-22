import dev.whiteout.power.BuildingSoundPolicy;
import dev.whiteout.power.AtmosphereConfig;
import com.google.gson.Gson;
public class BuildingAtmosphereTest {
 public static void main(String[] args){
  for(int seconds:new int[]{8,30,300})for(float jitter:new float[]{0,.5f,1}){
   int strong=BuildingSoundPolicy.delayTicks(seconds,1,jitter);
   int weak=BuildingSoundPolicy.delayTicks(seconds,.1f,jitter);
   if(strong<seconds*20||weak<=strong)throw new AssertionError("storm frequency/interval");
  }
  AtmosphereConfig c=new Gson().fromJson("{\"oldShelters\":[\"house\"],\"oldSoundIntervals\":{\"house\":45},\"doorRequiredPulls\":3}",AtmosphereConfig.class);
  c.normalize();
  if(!c.oldHouseSounds||!c.oldShelters.contains("house")||c.oldSoundIntervals.get("house")!=45||c.doorRequiredPulls!=3)throw new AssertionError("legacy config");
  if(BuildingSoundPolicy.delayTicks(0,1,0)<160)throw new AssertionError("minimum delay");
  System.out.println("PASS BuildingAtmosphereTest: intervals, storm strength, legacy world config");
 }
}
