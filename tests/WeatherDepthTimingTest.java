import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Check the actual supported Minecraft renderer, not a mock of our event order. */
public final class WeatherDepthTimingTest {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static ClassNode read(String name)throws Exception{
  try(var stream=ClassLoader.getSystemResourceAsStream(name+".class")){
   check(stream!=null,"missing class "+name);ClassNode node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
  }
 }
 public static void main(String[] args)throws Exception{
  ClassNode world=read("net/minecraft/client/render/WorldRenderer");
  boolean checked=false;
  for(MethodNode method:world.methods){
   if(!method.name.equals("render"))continue;
   int index=0,debug=-1,currentFlush=-1,skin=-1,skinFlush=-1,targets=0;
   for(var node:method.instructions){
    if(node instanceof MethodInsnNode call){
     if(call.owner.endsWith("/DebugRenderer")&&call.name.equals("render"))debug=index;
     if(debug>=0&&call.name.equals("drawCurrentLayer"))currentFlush=index;
     if(call.name.equals("getEntityTranslucentCull"))skin=index;
     if(skin>=0&&skinFlush<0&&call.owner.endsWith("VertexConsumerProvider$Immediate")&&call.name.equals("draw"))skinFlush=index;
     if(call.owner.equals("net/minecraft/client/render/RenderLayer")&&call.name.equals("getTranslucent")){
      check(debug>=0&&currentFlush>debug&&skin>debug&&skinFlush>skin,"old snapshot missed pending skin/entity buffers");
      check(index>skinFlush,"new snapshot must follow skin flush");targets++;
     }
    }
    index++;
   }
   check(targets==2,"normal + Fabulous capture points");checked=true;
  }
  check(checked,"world render method checked");
  ClassNode mixin=read("dev/whiteout/power/mixin/WeatherDepthCaptureMixin");
  boolean invokes=false;
  for(var method:mixin.methods)for(var node:method.instructions)
   if(node instanceof MethodInsnNode call&&call.owner.equals("dev/whiteout/power/WeatherDepth")&&call.name.equals("captureBeforeTranslucent"))invokes=true;
  check(invokes,"capture hook wired");
  System.out.println("PASS WeatherDepthTimingTest: actual 1.21.1 skin/buffer flush order; two pre-glass capture sites; hook wiring");
 }
}
