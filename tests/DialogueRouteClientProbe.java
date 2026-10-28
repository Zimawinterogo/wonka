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
  System.out.println("PASS real CLIENT Mixin application, both ordinary entry points without routes, legacy script lookup and alias metadata (no graphics/world)");
 }
}
