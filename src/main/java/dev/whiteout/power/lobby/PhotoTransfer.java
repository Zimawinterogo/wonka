package dev.whiteout.power.lobby;

import dev.whiteout.Store;
import dev.whiteout.power.PowerDevices;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public final class PhotoTransfer{
 public static final int W=256,H=192,BYTES=W*H*3,CHUNK=16384,COUNT=BYTES/CHUNK;
 private static final ExecutorService IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(24),r->{var t=new Thread(r,"Whiteout board photos");t.setDaemon(true);return t;});
 public record Part(UUID token,long revision,int index,int part,byte[] data) implements CustomPayload{
  public static final Id<Part> ID=new Id<>(PowerDevices.id("board_pixels"));
  public static final PacketCodec<RegistryByteBuf,Part> CODEC=PacketCodec.ofStatic((b,p)->{b.writeUuid(p.token);b.writeLong(p.revision);b.writeInt(p.index);b.writeInt(p.part);b.writeByteArray(p.data);},b->new Part(b.readUuid(),b.readLong(),b.readInt(),b.readInt(),b.readByteArray(CHUNK)));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public record Edit(UUID token,long revision,int index,String op,int x,int y,int angle) implements CustomPayload{
  public static final Id<Edit> ID=new Id<>(PowerDevices.id("board_photo_edit"));
  public static final PacketCodec<RegistryByteBuf,Edit> CODEC=PacketCodec.ofStatic((b,p)->{b.writeUuid(p.token);b.writeLong(p.revision);b.writeInt(p.index);b.writeString(p.op,12);b.writeInt(p.x);b.writeInt(p.y);b.writeInt(p.angle);},b->new Edit(b.readUuid(),b.readLong(),b.readInt(),b.readString(12),b.readInt(),b.readInt(),b.readInt()));
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 public static final class Assembly{
  final UUID token;final long revision;final int index;final byte[] bytes=new byte[BYTES];int next;
  public Assembly(UUID token,long revision,int index){this.index=index;this.token=token;this.revision=revision;}
  public boolean add(Part p){
   if(!token.equals(p.token)||revision!=p.revision||index!=p.index||next>=COUNT||p.part!=next||p.data.length!=CHUNK)throw new IllegalArgumentException("invalid photo part");
   System.arraycopy(p.data,0,bytes,next++*CHUNK,CHUNK);return next==COUNT;
  }
 }
 private static final Map<ServerPlayerEntity,Assembly> uploads=new WeakHashMap<>();
 private static final Map<ServerPlayerEntity,Long> gate=new WeakHashMap<>();
 private static final Map<ServerPlayerEntity,Set<Integer>> fetched=new WeakHashMap<>();
 static void opened(ServerPlayerEntity player){fetched.remove(player);uploads.remove(player);}
 private static boolean budget(ServerPlayerEntity p){long now=System.nanoTime();if(now-gate.getOrDefault(p,now-1000000000L)<500000000L)return false;gate.put(p,now);return true;}
 private static Path folder(ServerPlayerEntity p){return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("whiteout-board-photos");}
 public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception ex){throw new IllegalStateException(ex);}}
 static List<LobbyState.Photo> pictures(BoardNetwork.View v){return List.of(BoardNetwork.JSON.fromJson(v.details(),LobbyState.Photo[].class));}
 private static void receive(ServerPlayerEntity p,Part msg){
  if(!BoardNetwork.access(p,msg.token,true)||msg.index< -1)return;
  var store=Store.get(p.getServer());var state=LobbyState.get(store);
  if(state==null||state.started()||state.revision()!=msg.revision||msg.index>=state.photos().size()||msg.index==-1&&state.photos().size()>=5)return;
  if(msg.part==0){if(!budget(p))return;uploads.put(p,new Assembly(msg.token,msg.revision,msg.index));}
  var pending=uploads.get(p);if(pending==null)return;
  try{if(!pending.add(msg))return;}catch(IllegalArgumentException ex){uploads.remove(p);return;}
  uploads.remove(p);var folder=folder(p);var server=p.getServer();var bytes=pending.bytes;
  async(()->{
   String hash=hash(bytes);Files.createDirectories(folder);var path=folder.resolve(hash+".rgb");
   if(!Files.exists(path)){
    try(var files=Files.list(folder)){if(files.limit(256).count()>=256)throw new java.io.IOException("Photo storage full");}
    var tmp=Files.createTempFile(folder,"upload-",".tmp");try{Files.write(tmp,bytes);Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(tmp);}
   }
   server.execute(()->{
    if(!BoardNetwork.access(p,msg.token,true))return;
    try{var photos=new ArrayList<>(state.photos());if(msg.index==-1)photos.add(new LobbyState.Photo(hash,0,0,0));else {var old=photos.get(msg.index);photos.set(msg.index,new LobbyState.Photo(hash,old.x(),old.y(),old.angle()));}LobbyState.photos(store,msg.revision,photos);}
    catch(Exception ex){denied(p);}BoardNetwork.refresh(p);
   });
  },p);
 }
 private static void denied(ServerPlayerEntity p){p.sendMessage(net.minecraft.text.Text.translatable("story.whiteout.request_denied"));}
 private interface Task{void run()throws Exception;}
 private static void async(Task task,ServerPlayerEntity p){
  var server=p.getServer();try{IO.execute(()->{try{task.run();}catch(Exception ex){server.execute(()->denied(p));}});}catch(RejectedExecutionException ex){denied(p);}
 }
 private static void edit(ServerPlayerEntity p,Edit m){
  boolean get="get".equals(m.op);if(!BoardNetwork.access(p,m.token,!get))return;
  var store=Store.get(p.getServer());var state=LobbyState.get(store);
  if(state==null||state.revision()!=m.revision||m.index<0||m.index>=state.photos().size())return;
  var photo=state.photos().get(m.index);
  if(get){
   if(!fetched.computeIfAbsent(p,k->new HashSet<>()).add(m.index))return;
   Path path=folder(p).resolve(photo.hash()+".rgb");var server=p.getServer();
   async(()->{if(Files.size(path)!=BYTES)throw new java.io.IOException();byte[] bytes=Files.readAllBytes(path);
    if(!hash(bytes).equals(photo.hash()))throw new java.io.IOException();
    server.execute(()->{if(BoardNetwork.access(p,m.token,false))for(int i=0;i<COUNT;i++)ServerPlayNetworking.send(p,new Part(m.token,m.revision,m.index,i,Arrays.copyOfRange(bytes,i*CHUNK,(i+1)*CHUNK)));});
   },p);return;
  }
  if(state.started()||!budget(p))return;
  try{
   var list=new ArrayList<>(state.photos());
   switch(m.op){
    case "main"->{list.remove(m.index);list.add(0,photo);}
    case "earlier"->{if(m.index>0)Collections.swap(list,m.index,m.index-1);}
    case "delete"->list.remove(m.index);
    case "layout"->list.set(m.index,new LobbyState.Photo(photo.hash(),m.x,m.y,m.angle));
    default->{return;}
   }
   LobbyState.photos(store,m.revision,list);
  }catch(Exception ex){denied(p);}BoardNetwork.refresh(p);
 }
 static void register(){
  PayloadTypeRegistry.playC2S().register(Part.ID,Part.CODEC);PayloadTypeRegistry.playS2C().register(Part.ID,Part.CODEC);PayloadTypeRegistry.playC2S().register(Edit.ID,Edit.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(Part.ID,(m,c)->c.server().execute(()->receive(c.player(),m)));
  ServerPlayNetworking.registerGlobalReceiver(Edit.ID,(m,c)->c.server().execute(()->edit(c.player(),m)));
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{opened(h.getPlayer());gate.remove(h.getPlayer());});
 }
}
