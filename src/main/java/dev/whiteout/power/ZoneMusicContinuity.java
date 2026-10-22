package dev.whiteout.power;

/** Treat saved music-slot aliases as their actual bundled recording. */
public final class ZoneMusicContinuity {
 private ZoneMusicContinuity(){}
 /** Current Whiteout MusicSession keys use the prefix "music:". */
 public static String canonicalKey(String key){
  if(key==null||!key.startsWith("music:"))return key;
  try{
   int slot=Integer.parseInt(key.substring(6));
   // Slots 8–16 intentionally alias 1–7 in the personal soundtrack.
   int track=(slot>=1&&slot<=16)?((slot-1)%7)+1:slot;
   return "music:"+track;
  }catch(NumberFormatException ignored){return key;}
 }
}
