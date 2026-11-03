package dev.whiteout.power;

/** Fail closed for legacy arbitrary mutations. Server-authored STORY_COMMIT is handled separately. */
public final class StoryRequestPolicy {
 private StoryRequestPolicy(){}
 public static boolean legacyAllowed(String action,boolean operator){
  if(action==null||action.equals("STORY_COMMIT"))return false;
  return switch(action){
   // These paths retain their existing inventory/command-source validation.
   case "REQUEST","EQUIP","BATTERY_TICK","REPLACE_BATTERY","GOGGLES","SERVER_COMMAND","FOREST_REQUEST"->true;
   case "SAVE_ZONE","DELETE_ZONE","TELEPORT_ZONE","SURVIVAL_OPTION","INFINITE_BATTERY",
        "SAVE_OBJECTIVE","DELETE_OBJECTIVE","OBJECTIVE_EVENT","STORY_OBJECTIVE_EVENT","RESET_OBJECTIVE",
        "EVENT_DONE","REMOVE_COMPLETED_EVENT","RESET_EVENTS","CHECKPOINT_SAVE","CHECKPOINT_LOAD","CHECKPOINT_DELETE",
        "SAVE_PLOT","DELETE_PLOT","PLOT_MOVE","PLOT_SWAP","PLOT_HEAL_FOREST","PLOT_MASS_APPLY","PLOT_SYNC",
        "PLOT_SNOW","PLOT_VEGETATION","HEATMAP_RECORD","HEATMAP_RESET","HEATMAP_REQUEST","POI_DISTANCE","PLOT_UNDO",
        "FOREST_SAVE","FOREST_DELETE","FOREST_TOGGLE","FOREST_PRELOAD_TOGGLE","ACTOR_BEGIN","ACTOR_STOP","ACTOR_COMMAND","EASY_NPC"->operator;
   default->false;
  };
 }
 /** One bounded counter per connection; no attacker-controlled key map. */
 public static final class Budget {
  private long window=Long.MIN_VALUE;private int used;
  public boolean take(long tick){
   if(window==Long.MIN_VALUE||tick<window||tick-window>=20){window=tick;used=0;}
   if(used>=16)return false;used++;return true;
  }
 }
}
