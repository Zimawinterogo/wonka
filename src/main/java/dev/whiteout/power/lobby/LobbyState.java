package dev.whiteout.power.lobby;

import dev.whiteout.Store;
import java.util.*;

/** World-owned configuration. A world with no board has no lobby record. */
public record LobbyState(Card card,UUID first,UUID second,boolean started,long revision,List<Photo> photos,Start start){
 public record Photo(String hash,int x,int y,int angle){
  public Photo{if(hash==null||!hash.matches("[a-f0-9]{64}")||Math.abs((long)x)>30||Math.abs((long)y)>20||Math.abs((long)angle)>15)throw new IllegalArgumentException("photo layout");}
 }
 public record Start(String dimension,double x,double y,double z,String event){
  public Start{
   if(dimension!=null&&(net.minecraft.util.Identifier.tryParse(dimension)==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>29999984||Math.abs(z)>29999984||y< -2048||y>2048))throw new IllegalArgumentException("start position");
   if(event!=null&&!event.matches("[a-zA-Z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("start event");
  }
 }
 public LobbyState(Card card,UUID first,UUID second,boolean started,long revision){this(card,first,second,started,revision,List.of(),null);}
 LobbyState change(UUID host,UUID partner,boolean running,List<Photo> pictures,Start config){
  return new LobbyState(card,host,partner,running,Math.incrementExact(revision),pictures,config);
 }
 static LobbyState editable(Store store,long revision){
  var old=get(store);check(old,revision);return Objects.requireNonNull(old,"configure card first");
 }
 static void photos(Store store,long revision,List<Photo> photos)throws java.io.IOException{
  var old=editable(store,revision);save(store,old,old.change(old.first,old.second,false,photos,old.start));
 }
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
  photos=photos==null?List.of():List.copyOf(photos);if(photos.size()>5)throw new IllegalArgumentException("five photos maximum");
  if(revision<1||card==null||second!=null&&(first==null||second.equals(first))||started&&first==null)throw new IllegalArgumentException("invalid lobby");
 }
 public Mode mode(){return second==null?Mode.SOLO:Mode.DUO;}
 public static LobbyState get(Store store){return ((Access)(Object)store.data).whiteout$lobby();}
 public static void edit(Store store,long revision,Card card)throws java.io.IOException{
  var old=get(store);check(old,revision);
  save(store,old,new LobbyState(card,old==null?null:old.first,old==null?null:old.second,false,Math.incrementExact(revision),old==null?List.of():old.photos,old==null?null:old.start));
 }
 public static void startSolo(Store store,long revision,UUID host)throws java.io.IOException{
  var old=get(store);check(old,revision);
  if(old==null||old.mode()!=Mode.SOLO||old.first!=null&&!old.first.equals(host))throw new IllegalStateException("unavailable");
  save(store,old,old.change(Objects.requireNonNull(host),null,true,old.photos,old.start));
 }
 public static void resetLaunch(Store store)throws java.io.IOException{
  var old=get(store);if(old==null||!old.started)throw new IllegalStateException("not started");
  if(((dev.whiteout.power.SharedScene.Access)(Object)store.data).whiteout$scene()!=null)throw new IllegalStateException("scene still running");
  save(store,old,old.change(null,null,false,old.photos,old.start));
 }
 public static final String STARTED="whiteout:lobby_started";
 private static void check(LobbyState old,long revision){
  if(old==null?revision!=0:old.revision!=revision||old.started)throw new IllegalStateException("stale or started");
 }
 static void save(Store store,LobbyState old,LobbyState value)throws java.io.IOException{
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
