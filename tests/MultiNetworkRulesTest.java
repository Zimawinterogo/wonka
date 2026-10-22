import dev.whiteout.power.CircuitTopology;
import dev.whiteout.power.GridStatePolicy;
import java.util.*;

/** Dependency-free tests for network separation rules; does not start Minecraft. */
public final class MultiNetworkRulesTest {
 static int count;
 static void check(boolean b){if(!b)throw new AssertionError("independent grid check "+count);count++;}
 static boolean on(boolean fuse, boolean generator){return GridStatePolicy.supplied(true,true,true,true,fuse,false,generator,true,false,false);}
 public static void main(String[] args){
  check(CircuitTopology.validName("fisher_house"));
  check(CircuitTopology.validName("circuit_2"));
  check(!CircuitTopology.validName("main"));
  check(!CircuitTopology.validName("Fisher House"));
  check(!CircuitTopology.validName("../outside"));
  check(!CircuitTopology.validName(""));
  check(!CircuitTopology.validName("x".repeat(33)));
  // Each circuit uses its OWN fuse/generator facts. One fault cannot change another.
  boolean original=on(true,true), fisherman=on(false,true);
  check(original);check(!fisherman);
  fisherman=on(true,true);check(original&&fisherman);
  original=on(true,false);check(!original&&fisherman);
  System.out.println("PASS "+count+" independent-network topology/power assertions");
 }
}
