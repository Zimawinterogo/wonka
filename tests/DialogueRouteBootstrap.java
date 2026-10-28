import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/** Real Fabric class transformation; no game window or world is started. */
public final class DialogueRouteBootstrap {
 public static void main(String[] args)throws Exception{
  boolean client=args.length>0&&args[0].equals("client");
  ClassLoader game=new Knot(client?EnvType.CLIENT:EnvType.SERVER).init(new String[0]);
  try{Class.forName(client?"DialogueRouteClientProbe":"DialogueRouteServerProbe",true,game).getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
  catch(java.lang.reflect.InvocationTargetException ex){if(ex.getCause() instanceof Error e)throw e;if(ex.getCause() instanceof Exception e)throw e;throw ex;}
 }
}
