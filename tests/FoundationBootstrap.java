import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/** Loads the collision probe through Fabric's normal game class transformations. No world/JAR build. */
public final class FoundationBootstrap {
 public static void main(String[] args) throws Exception {
  ClassLoader game=new Knot(EnvType.SERVER).init(new String[0]);
  try {
   Class.forName("dev.whiteout.power.LakeJournalTest",true,game)
    .getMethod("main",String[].class).invoke(null,(Object)new String[0]);
   Class.forName("dev.whiteout.power.barricade.BarricadeCollisionTest",true,game)
    .getMethod("main",String[].class).invoke(null,(Object)new String[0]);
   Class.forName("dev.whiteout.power.WorldStateDeviceTest",true,game)
    .getMethod("main",String[].class).invoke(null,(Object)new String[0]);
   Class.forName("dev.whiteout.power.CrisisBootstrapTest",true,game)
    .getMethod("main",String[].class).invoke(null,(Object)new String[0]);
  } catch(java.lang.reflect.InvocationTargetException ex){
   Throwable cause=ex.getCause();
   if(cause instanceof Error error)throw error;
   if(cause instanceof Exception error)throw error;
   throw ex;
  }
 }
}
