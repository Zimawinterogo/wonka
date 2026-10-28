package dev.whiteout.power;

import com.google.gson.Gson;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Per-world route metadata. A damaged file is read-only until repaired externally. */
public final class DialogueRouteStore {
 public static final int MAX_BYTES=524288;
 private static final Gson GSON=new Gson();
 public record Data(int schema,long revision,List<DialogueRoute> routes){}
 private final Path path;
 private Data data=new Data(1,0,List.of());
 private boolean invalid;
 public DialogueRouteStore(Path path){
  this.path=path;
  try{if(Files.exists(path)){
   if(Files.size(path)>MAX_BYTES)throw new IOException("size");
   Data loaded=GSON.fromJson(Files.readString(path),Data.class);validate(loaded);data=immutable(loaded);
  }}catch(Exception failure){invalid=true;}
 }
 public static void validate(Data data){
  if(data==null||data.schema!=1||data.revision<0||data.revision==Long.MAX_VALUE||data.routes==null||data.routes.size()>DialogueRoute.MAX_ROUTES)throw new IllegalArgumentException("file");
  Set<String> ids=new HashSet<>();
  for(var route:data.routes){if(route==null)throw new IllegalArgumentException("route");route.validate();validateWire(route);if(!ids.add(route.id()))throw new IllegalArgumentException("duplicate");}
 }
 public static String validateWire(DialogueRoute route){
  String json=GSON.toJson(route);
  if(json.length()>12000||json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>24000)throw new IllegalArgumentException("size");
  return json;
 }
 private static Data immutable(Data data){return new Data(data.schema,data.revision,List.copyOf(data.routes));}
 public Data snapshot(){return data;}
 public boolean invalid(){return invalid;}
 public DialogueRoute find(String id){return invalid?null:data.routes.stream().filter(r->r.id().equals(id)).findFirst().orElse(null);}
 public void save(DialogueRoute route,long revision)throws IOException {
  route.validate();List<DialogueRoute> updated=new ArrayList<>(data.routes);
  int i=0;while(i<updated.size()&&!updated.get(i).id().equals(route.id()))i++;
  if(i==updated.size())updated.add(route);else updated.set(i,route);
  commit(updated,revision);
 }
 public void delete(String id,long revision)throws IOException {
  List<DialogueRoute> updated=new ArrayList<>(data.routes);updated.removeIf(r->r.id().equals(id));commit(updated,revision);
 }
 private void commit(List<DialogueRoute> routes,long expected)throws IOException {
  if(invalid)throw new IOException("corrupt");
  if(expected!=data.revision)throw new IOException("conflict");
  Data next=new Data(1,data.revision+1,List.copyOf(routes));validate(next);
  String json=GSON.toJson(next);if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("size");
  GridFileIO.writeAtomic(path,json);data=next;
 }
}
