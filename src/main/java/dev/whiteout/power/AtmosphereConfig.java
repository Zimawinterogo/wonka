package dev.whiteout.power;

import java.util.*;

/** Stored per world (rather than on static client state), including per-door options. */
public final class AtmosphereConfig {
 public Set<String> oldShelters=new HashSet<>();
 public Set<String> freezingDoors=new HashSet<>();
 public Map<String,DoorFrostState> doorStates=new HashMap<>();
 public static final class DoorFrostState {
  public int attempts,coldTicks;
  public long lastAttempt;
 }
 public Map<String,Integer> oldSoundIntervals=new HashMap<>();
 public Set<String> mutedOldSoundZones=new HashSet<>();
 public boolean windThroughOpenings=true;
 public boolean coldLeaks=true;
 public boolean windMovement=true; // toggle ONLY for movement resistance, does not stop physical wind
 public int windStrengthPercent=35; // shared server-owned wind, synced to the client
 public boolean oldHouseSounds=true;
 public boolean slammingDoors=true;
 public boolean radioStatic=true;
 public boolean indoorFootprints=false;
 public boolean windowFrost=true;
 public int doorRequiredPulls=3;
 public int tempOutdoorBlizzard=-28;
 public int tempOutdoorNormal=-12;
 public void normalize(){
  if(oldShelters==null)oldShelters=new HashSet<>();
  if(freezingDoors==null)freezingDoors=new HashSet<>();
  if(doorStates==null)doorStates=new HashMap<>();
  doorStates.entrySet().removeIf(e->e.getValue()==null||!freezingDoors.contains(e.getKey()));
  for(DoorFrostState state:doorStates.values())state.coldTicks=Math.max(0,Math.min(WorldAtmosphere.DOOR_FREEZE_TICKS,state.coldTicks));
  if(oldSoundIntervals==null)oldSoundIntervals=new HashMap<>();
  if(mutedOldSoundZones==null)mutedOldSoundZones=new HashSet<>();
  mutedOldSoundZones.removeIf(id->id==null||id.isBlank());
  oldSoundIntervals.values().removeIf(v->v==null||v<8||v>300);
  windStrengthPercent=Math.max(0,Math.min(100,windStrengthPercent));
  doorRequiredPulls=Math.max(2,Math.min(8,doorRequiredPulls));
  tempOutdoorBlizzard=Math.max(-80,Math.min(10,tempOutdoorBlizzard));
  tempOutdoorNormal=Math.max(-80,Math.min(20,tempOutdoorNormal));
 }
}
