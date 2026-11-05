package dev.whiteout.power.lobby;

import dev.whiteout.power.SharpScreen;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static dev.whiteout.power.lobby.PhotoTransfer.*;

final class PhotoScreen extends SharpScreen{
 private static final Map<String,Identifier> cache=new LinkedHashMap<>();
 private static final Map<Integer,Assembly> incoming=new HashMap<>();
 private static BoardNetwork.View current;
 private final BoardNetwork.View data;private final boolean editor;private int index,uploadIndex=-1;
 private BufferedImage source;private byte[] draft;private Identifier preview;private double cropX=.5,cropY=.5,zoom=1;
 private int x,y,angle;private boolean choosing;
 private String status="";
 PhotoScreen(BoardNetwork.View data){super(Text.literal("Фотографии"));this.data=data;editor=data.editor()&&!data.started();select(0);}
 private static RetrySchedule retries=new RetrySchedule();
 static void register(){
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{if(current!=null&&(c.currentScreen instanceof BoardScreen||c.currentScreen instanceof PhotoScreen)){if(retries.due())requestMissing(current);}});
  ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{clear(client);current=null;}));
  ClientPlayNetworking.registerGlobalReceiver(Part.ID,(p,c)->c.client().execute(()->{
   if(current==null||!current.token().equals(p.token())||current.revision()!=p.revision()||p.index()<0||p.index()>=pictures(current).size())return;
   if(cache.containsKey(pictures(current).get(p.index()).hash()))return;
   if(p.part()==0)incoming.put(p.index(),new Assembly(p.token(),p.revision(),p.index()));
   var a=incoming.get(p.index());if(a==null)return;
   try{if(a.add(p)){incoming.remove(p.index());var hash=pictures(current).get(p.index()).hash();if(hash(a.bytes).equals(hash))cache.put(hash,texture(c.client(),hash,a.bytes));}}catch(RuntimeException ex){incoming.remove(p.index());}
  }));
 }
 static void opened(BoardNetwork.View data,MinecraftClient client){
  current=data;retries=new RetrySchedule();incoming.clear();var photos=pictures(data);var keep=photos.stream().map(LobbyState.Photo::hash).toList();
  for(var it=cache.entrySet().iterator();it.hasNext();){var entry=it.next();if(!keep.contains(entry.getKey())){client.getTextureManager().destroyTexture(entry.getValue());it.remove();}}
  requestMissing(data);
 }
 private static void requestMissing(BoardNetwork.View data){var photos=pictures(data);
  for(int i=0;i<photos.size();i++)if(!cache.containsKey(photos.get(i).hash()))ClientPlayNetworking.send(new Edit(data.token(),data.revision(),i,"get",0,0,0));
 }
 static void clear(MinecraftClient c){cache.values().forEach(c.getTextureManager()::destroyTexture);cache.clear();incoming.clear();}
 private static Identifier texture(MinecraftClient c,String key,byte[] bytes){
  var img=new NativeImage(W,H,false);int k=0;
  for(int row=0;row<H;row++)for(int col=0;col<W;col++){int r=bytes[k++]&255,g=bytes[k++]&255,b=bytes[k++]&255;img.setColor(col,row,0xff000000|b<<16|g<<8|r);}
  var id=Identifier.of("whiteout","board_photo/"+key);c.getTextureManager().registerTexture(id,new NativeImageBackedTexture(img));return id;
 }
 static void draw(DrawContext d,Identifier id,int cx,int cy,int w,int h,int angle){
  var m=d.getMatrices();m.push();m.translate(cx,cy,0);m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
  LobbyUi.polaroid(d,-w/2,-h/2,w,h);
  if(id!=null)d.drawTexture(id,-w/2,-h/2,w,h,0,0,W,H,W,H);else {
   d.fill(-w/2,-h/2,w/2,h/2,0xff9ca5a3);
   for(int col=-w/2;col<w/2;col++){double t=(col+w/2.0)/w;int ridge=(int)(h*(.16*Math.cos(8*t)+.08*Math.cos(16*t)));d.fill(col,ridge,col+1,h/2,0xffd8d9cf);}
  }
  LobbyUi.pin(d,0,-h/2-3);m.pop();
 }
 static void mainPhoto(DrawContext d,BoardNetwork.View data,int cx,int cy){
  var photos=pictures(data);if(photos.isEmpty())return;var p=photos.getFirst();draw(d,cache.get(p.hash()),cx+p.x()/3,cy+p.y()/3,70,52,p.angle());
 }
 static void collage(DrawContext d,BoardNetwork.View data,int cx,int cy){
  var photos=pictures(data);if(photos.size()<2){mainPhoto(d,data,cx,cy);return;}
  var main=photos.getFirst();draw(d,cache.get(main.hash()),cx-19+main.x()/3,cy+main.y()/3,58,44,main.angle());
  for(int i=1;i<photos.size();i++){var p=photos.get(i);int col=(i-1)%2,row=(i-1)/2;
   draw(d,cache.get(p.hash()),cx+19+col*22+p.x()/6,cy-16+row*26+p.y()/5,19,15,p.angle());}
 }
 private void select(int i){var pics=pictures(data);index=Math.clamp(i,0,Math.max(0,pics.size()-1));if(!pics.isEmpty()){var p=pics.get(index);x=p.x();y=p.y();angle=p.angle();}}
 private void button(String name,int x,int y,int w,Runnable action){addDrawableChild(LobbyUi.button(Text.literal(name),x,y,w,19,action));}
 private void edit(String op){ClientPlayNetworking.send(new Edit(data.token(),data.revision(),index,op,x,y,angle));}
 private void choose(){
  if(choosing)return;choosing=true;var mc=client;status="Выбор PNG или JPG…";
  CompletableFuture.supplyAsync(()->{
   String file=TinyFileDialogs.tinyfd_openFileDialog("Whiteout · PNG / JPG","",(org.lwjgl.PointerBuffer)null,"PNG / JPEG",false);
   if(file==null)return null;
   try{Path path=Path.of(file);if(Files.size(path)>8*1024*1024)throw new Exception();
    try(var in=ImageIO.createImageInputStream(path.toFile())){var readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new Exception();
     var reader=readers.next();try{reader.setInput(in,true,true);String fmt=reader.getFormatName();
      int w=reader.getWidth(0),h=reader.getHeight(0);if(!(fmt.equalsIgnoreCase("png")||fmt.equalsIgnoreCase("jpeg"))||w>4096||h>4096||w<1||h<1)throw new Exception();
      return reader.read(0);}finally{reader.dispose();}}
   }catch(Exception ex){throw new IllegalArgumentException("PNG/JPG, до 8 МиБ и 4096 пикселей");}
  }).whenComplete((img,err)->mc.execute(()->{choosing=false;if(mc.currentScreen!=this)return;status=err==null?"":"PNG/JPG до 8 МиБ, 4096 пикселей";if(img!=null){source=img;cropX=cropY=.5;zoom=1;crop();clearAndInit();}}));
 }
 private void crop(){
  int sw=source.getWidth(),sh=source.getHeight();double scale=Math.max((double)W/sw,(double)H/sh)*zoom;
  var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
  try{int dw=(int)Math.ceil(sw*scale),dh=(int)Math.ceil(sh*scale);g.drawImage(source,-(int)((dw-W)*cropX),-(int)((dh-H)*cropY),dw,dh,null);}finally{g.dispose();}
  draft=new byte[BYTES];int k=0;for(int row=0;row<H;row++)for(int col=0;col<W;col++){int rgb=image.getRGB(col,row);draft[k++]=(byte)(rgb>>16);draft[k++]=(byte)(rgb>>8);draft[k++]=(byte)rgb;}
  preview=texture(client,"preview",draft);
 }
 @Override protected void init(){
  int cx=width/2,b=height-46;var pics=pictures(data);
  if(source!=null){
   button("−",cx-118,b,24,()->{zoom=Math.max(1,zoom-.25);crop();});button("+",cx-91,b,24,()->{zoom=Math.min(4,zoom+.25);crop();});
   button("Сохранить снимок",cx-60,b,122,()->{for(int i=0;i<COUNT;i++)ClientPlayNetworking.send(new Part(data.token(),data.revision(),uploadIndex,i,Arrays.copyOfRange(draft,i*CHUNK,(i+1)*CHUNK)));source=null;status="Отправлено";clearAndInit();});return;
  }
  if(editor&&pics.size()<5)button("+ Фото",cx-140,b,62,()->{uploadIndex=-1;choose();});
  if(!pics.isEmpty()){
   button("‹",cx-72,b,24,()->{select(index-1);clearAndInit();});button("›",cx-46,b,24,()->{select(index+1);clearAndInit();});
   if(editor){button("Главное",cx-18,b,60,()->edit("main"));button("Раньше",cx+44,b,54,()->edit("earlier"));button("×",cx+100,b,26,()->edit("delete"));button("Заменить фото",cx-135,b+21,108,()->{uploadIndex=index;choose();});button("Сохранить положение",cx-24,b+21,140,()->edit("layout"));}
  }
 }
 @Override public boolean mouseDragged(double mx,double my,int btn,double dx,double dy){
  if(editor&&btn==0&&my>20&&my<height-55){if(source!=null){cropX=Math.clamp(cropX-dx/150,0,1);cropY=Math.clamp(cropY-dy/120,0,1);crop();}
   else{x=Math.clamp(x+(int)Math.round(dx),-30,30);y=Math.clamp(y+(int)Math.round(dy),-20,20);}return true;}
  return super.mouseDragged(mx,my,btn,dx,dy);
 }
 @Override public boolean mouseScrolled(double mx,double my,double h,double v){if(editor&&source==null){angle=Math.clamp(angle+(int)Math.signum(v),-15,15);return true;}return super.mouseScrolled(mx,my,h,v);}
 @Override public void render(DrawContext d,int mx,int my,float dt){
  LobbyUi.backdrop(d,width,height);var frame=LobbyUi.Box.centered(width,height,398,230);LobbyUi.board(d,frame);
  LobbyUi.paper(d,width/2-83,height/2-78,166,125);
  LobbyUi.pin(d,width/2-71,height/2-72);var pics=pictures(data);
  draw(d,source!=null?preview:pics.isEmpty()?null:cache.get(pics.get(index).hash()),width/2+(source!=null?0:x),height/2-18+(source!=null?0:y),136,102,source!=null?0:angle);
  LobbyUi.fit(d,textRenderer,status.isEmpty()?"ФОТОГРАФИИ / "+pics.size()+" ИЗ 5":status,frame.x()+18,frame.y()+12,frame.w()-36,LobbyUi.LIGHT,false,1);
  LobbyUi.caption(d,textRenderer,editor?"ЛКМ: перетащить / КОЛЕСО: поворот":"ГАЛЕРЕЯ ИСТОРИИ",frame.x()+17,frame.bottom()-12,frame.w()-34,LobbyUi.LIGHT_DIM);
  super.render(d,mx,my,dt);
 }
 @Override public void removed(){if(preview!=null){client.getTextureManager().destroyTexture(preview);preview=null;}}
 @Override public void close(){client.setScreen(new BoardScreen(data));}
}
