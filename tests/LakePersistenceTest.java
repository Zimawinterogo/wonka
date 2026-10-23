import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.util.Set;

/** Uses the production chapter DTO, including legacy defaults and durable reset/finale state. */
public class LakePersistenceTest {
 static Object get(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
 static void check(boolean condition){if(!condition)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  Class<?> type=Class.forName("dev.whiteout.power.LakeStory$State");
  Gson gson=new GsonBuilder().create();
  Object legacy=gson.fromJson("{\"version\":1,\"stage\":0,\"enabled\":true}",type);
  check(get(legacy,"journal")!=null);check(((Set<?>)get(legacy,"waitingExit")).isEmpty());
  check(get(legacy,"inside")!=null);check(!(boolean)get(legacy,"homeAttempted"));
  for(int phase=0;phase<=3;phase++){
   set(legacy,"finalPhase",phase);set(legacy,"homeFueled",true);set(legacy,"homePulls",3);
   set(legacy,"homeAttempted",true);set(legacy,"resetPending",true);set(legacy,"waitingExit",Set.of("player-a","player-b"));
   Object restored=gson.fromJson(gson.toJson(legacy),type);
   check((int)get(restored,"finalPhase")==phase);check((int)get(restored,"homePulls")==3);
   check((boolean)get(restored,"homeFueled"));check((boolean)get(restored,"homeAttempted"));
   check((boolean)get(restored,"resetPending"));check(((Set<?>)get(restored,"waitingExit")).size()==2);
   check((boolean)get(restored,"recovering"));
  }
  String json=gson.toJson(legacy);check(!json.contains("loadError"));check(!json.contains("recovering"));check(!json.contains("inside"));
  System.out.println("PASS LakePersistenceTest: production DTO legacy defaults, reset/rearm and four final phases survive JSON round-trip");
 }
}
