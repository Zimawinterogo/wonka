package dev.whiteout.power.lobby;

import dev.whiteout.Store;
import java.util.*;

/** World-owned configuration. A world with no board has no lobby record. */
public record LobbyState(Card card,UUID first,UUID second,boolean started,long revision){
 public enum Mode { SOLO, DUO }
 public record Card(String title,String description,String author,String duration,String note){
  public Card{
   title=field(title,80);description=field(description,320);author=field(author,64);
   duration=field(duration,48);note=field(note,160);
   if(title.isBlank())throw new IllegalArgumentException("title");
  }
  private static String field(String s,int limit){
   if(s==null||s.length()>limit||s.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("invalid story card");
   return s.strip();
  }
 }
 public interface Access {LobbyState whiteout$lobby();void whiteout$lobby(LobbyState value);}
 public LobbyState{
  if(revision<1||card==null||second!=null&&(first==null||second.equals(first))||started&&first==null)throw new IllegalArgumentException("invalid lobby");
 }
 public Mode mode(){return second==null?Mode.SOLO:Mode.DUO;}
 public static LobbyState get(Store store){return ((Access)(Object)store.data).whiteout$lobby();}
 public static void edit(Store store,long revision,Card card)throws java.io.IOException{
  var old=get(store);check(old,revision);
  save(store,old,new LobbyState(card,old==null?null:old.first,old==null?null:old.second,false,Math.incrementExact(revision)));
 }
 public static void startSolo(Store store,long revision,UUID host)throws java.io.IOException{
  var old=get(store);check(old,revision);
  if(old==null||old.mode()!=Mode.SOLO||old.first!=null&&!old.first.equals(host))throw new IllegalStateException("unavailable");
  save(store,old,new LobbyState(old.card,Objects.requireNonNull(host),null,true,Math.incrementExact(revision)));
 }
 public static void resetLaunch(Store store)throws java.io.IOException{
  var old=get(store);if(old==null||!old.started)throw new IllegalStateException("not started");
  save(store,old,new LobbyState(old.card,null,null,false,Math.incrementExact(old.revision)));
 }
 public static final String STARTED="whiteout:lobby_started";
 private static void check(LobbyState old,long revision){
  if(old==null?revision!=0:old.revision!=revision||old.started)throw new IllegalStateException("stale or started");
 }
 private static void save(Store store,LobbyState old,LobbyState value)throws java.io.IOException{
  if(store.error!=null)throw new java.io.IOException(store.error);
  var access=(Access)(Object)store.data;access.whiteout$lobby(value);
  boolean hadMarker=store.data.completedEvents.contains(STARTED);
  if(value.started)store.data.completedEvents.add(STARTED);
  else if(old!=null&&old.started)store.data.completedEvents.remove(STARTED);
  try{store.save();}catch(java.io.IOException|RuntimeException ex){
   access.whiteout$lobby(old);if(hadMarker)store.data.completedEvents.add(STARTED);else store.data.completedEvents.remove(STARTED);throw ex;
  }
 }
}
