import dev.whiteout.client.DialogueManager;
import dev.whiteout.power.DialogueRouteClient;
import java.util.*;

public class DialogueRouteClientProbe {
 @SuppressWarnings("unchecked")
 public static void main(String[] args)throws Exception{
  if(Arrays.stream(DialogueManager.class.getDeclaredMethods()).noneMatch(m->m.getName().contains("routeDirector")))throw new AssertionError("Director Mixin not applied");
  if(DialogueManager.open("__missing_legacy__")||DialogueManager.openDirector("__missing_legacy__",140))throw new AssertionError("legacy lookup changed");
  if(DialogueRouteClient.intercept("ordinary",140))throw new AssertionError("no-route behavior changed");
  var field=DialogueManager.class.getDeclaredField("SCRIPTS");field.setAccessible(true);
  var registry=(Map<String,DialogueManager.Script>)field.get(null);
  var script=new DialogueManager.Script("legacy",List.of("old_alias"),List.of(),List.of());registry.put(script.id(),script);
  if(DialogueManager.findById("legacy")!=script||DialogueManager.findById("LEGACY")!=script||!script.aliases().equals(List.of("old_alias")))throw new AssertionError("existing script metadata/lookup changed");
  registry.remove(script.id());
  var runtime=dev.whiteout.client.StoryRuntime.class;
  if(Arrays.stream(runtime.getDeclaredMethods()).noneMatch(m->m.getName().contains("authorizedFire")))throw new AssertionError("StoryRuntime authority Mixin absent");
  if(Arrays.stream(dev.whiteout.client.director.DirectorTriggerManager.class.getDeclaredMethods()).noneMatch(m->m.getName().contains("serverManaged")))throw new AssertionError("Migrated trigger guard absent");
  if(Arrays.stream(dev.whiteout.client.DialogueScreen.class.getDeclaredMethods()).noneMatch(m->m.getName().contains("awaitChoice")))throw new AssertionError("Dialogue choice redirect absent");
  if(dev.whiteout.client.StoryRuntime.commit("unapproved",true,List.of(),List.of()))throw new AssertionError("speculative commit survived");
  var trigger=new dev.whiteout.client.director.DirectorTriggerManager.Trigger();
  trigger.type=dev.whiteout.client.director.DirectorTriggerManager.TriggerType.ENTER_BOX;trigger.dimension="minecraft:overworld";
  trigger.x1=12;trigger.x2=10;trigger.y1=70;trigger.y2=72;trigger.z1=-2;trigger.z2=2;
  var area=new dev.whiteout.power.StoryEventCatalog.Area(trigger.dimension,0,0,0,0,new dev.whiteout.power.StoryEventCatalog.Box(10,70,-2,13,73,3));
  area.validate();
  for(double x:new double[]{9.99,10,12,12.999,13})for(double y:new double[]{69.99,70,72.999,73})for(double z:new double[]{-2.01,-2,2.999,3})
   if(trigger.contains(x,y,z,trigger.dimension)!=area.contains(trigger.dimension,x,y,z))throw new AssertionError("legacy box geometry differs");
  System.out.println("PASS CLIENT StoryRuntime/DialogueScreen authority Mixins, speculative commit blocked and 80 legacy box comparisons");
  System.out.println("PASS real CLIENT Mixin application, both ordinary entry points without routes, legacy script lookup and alias metadata (no graphics/world)");
 }
}
