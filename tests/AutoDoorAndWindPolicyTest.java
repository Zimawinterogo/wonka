import dev.whiteout.power.AutoDoorPolicy;
import dev.whiteout.power.WindMovementPolicy;
import dev.whiteout.power.SharedWindField;

/** Standalone Java 21 behaviour checks, without Fabric/MC runtime. */
public final class AutoDoorAndWindPolicyTest {
 private static void check(boolean condition,String what){if(!condition)throw new AssertionError(what);}
 public static void main(String[] args){
  check(AutoDoorPolicy.DELAY_TICKS==210,"10.5 second delay");
  check(AutoDoorPolicy.shouldClose(true,false,false,false,true,true),"ordinary door closes");
  check(!AutoDoorPolicy.shouldClose(true,true,false,false,true,true),"Whiteout electric door excluded");
  check(!AutoDoorPolicy.shouldClose(true,false,true,false,true,true),"redstone power respected");
  check(!AutoDoorPolicy.shouldClose(true,false,false,true,true,true),"NPC/player obstruction respected");
  check(!AutoDoorPolicy.shouldClose(true,false,false,false,false,true),"unloaded chunks skipped");
  check(!AutoDoorPolicy.shouldClose(true,false,false,false,true,false),"not before deadline");
  check(!AutoDoorPolicy.shouldClose(false,false,false,false,true,true),"closed door ignored");
  check(Math.abs(WindMovementPolicy.resistance(-1,0,1,0,true)-.15)<.00001,"15% upwind slowdown");
  check(WindMovementPolicy.resistance(1,0,1,0,true)==0,"tailwind unaffected");
  check(WindMovementPolicy.resistance(0,1,1,0,true)==0,"crosswind unaffected");
  check(WindMovementPolicy.resistance(-1,0,1,0,false)==0,"no storm penalty");
  check(WindMovementPolicy.resistance(0,0,1,0,true)==0,"idle player unaffected");
  check(Math.abs(WindMovementPolicy.resistance(-1,-1,1,1,true)-.15)<.00001,"diagonal headwind slowdown");
  check(WindMovementPolicy.resistance(-1,0,1,1,true)>0,"partial headwind slowdown");
  check(WindMovementPolicy.resistance(0,0,1,1,true)==0,"stationary penalty cleared");
  check(WindMovementPolicy.resistance(0,0,0,0,true)==0,"still air no slowdown");
  check(Double.isFinite(WindMovementPolicy.angle(123L,-10L)),"finite deterministic direction");
  check(SharedWindField.strength(1234,0,true,1)==0,"blizzard cannot override true calm");
  check(SharedWindField.strength(1234,.35,true,1)>SharedWindField.strength(1234,.35,false,0),"storm increases shared wind");
  check(SharedWindField.directionX(1234)==Math.cos(WindMovementPolicy.angle(1234,0)),"same direction on both sides");
  check(WindMovementPolicy.resistance(-1,0,1,0,.5)==.075,"half strength gives half resistance");
  check(WindMovementPolicy.resistance(-1,0,1,0,0)==0,"physical calm cancels movement penalty");
  check(WindMovementPolicy.resistance(1,0,1,0,1)==0,"tailwind penalty is zero");
  check(Double.isFinite(SharedWindField.gust(-9223372036854775808L)),"very old world ticks valid");
  System.out.println("AutoDoorAndWindPolicyTest: PASS (25 assertions)");
 }
}
