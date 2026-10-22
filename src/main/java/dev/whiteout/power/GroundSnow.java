package dev.whiteout.power;

import com.google.gson.*;
import dev.whiteout.Zone;
import dev.whiteout.client.WeatherState;
import dev.whiteout.client.Bridge;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import java.nio.file.*;
import java.util.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

/** Client-only terrain particles: vanilla depth/collision pipeline; no scene-depth hooks. */
public final class GroundSnow {
 private GroundSnow(){}
 private static final int MAX=180;
 private static final Set<Flake> live=new HashSet<>();
 private static ClientWorld previous;
 private static Zone cameraWeather;
 private static final GroundSnowWindSource.Sample sampledWind=new GroundSnowWindSource.Sample();
 private static double lastGust,lastWind,lastSpawn,lastX,lastZ;
 private static String windSource="автономный, НЕ синхронизирован";
 private static String sampledSource=windSource;
 private static String reason="нет мира";
 // Read-only, cumulative diagnostics. No per-tick logging or networking.
 private static long attemptsTotal, rejectedSurface, rejectedSky, rejectedShelter, rejectedZone, rejectedWeather, spawnedTotal;
 private static String diagnostics(){
  return "Позёмка: активных="+live.size()+", создано="+spawnedTotal+", попыток="+attemptsTotal
   +", нет снега="+rejectedSurface+", нет неба="+rejectedSky+", укрытие="+rejectedShelter
   +", ясных проб вне зон="+rejectedZone+", пропущено по ветру="+rejectedWeather
   +", ветер="+Math.round(lastWind*100)+"%, порыв="+Math.round(lastGust*100)+"%, спавн="+Math.round(lastSpawn*100)
   +"%, направление X/Z="+Math.round(lastX*100)/100.0+"/"+Math.round(lastZ*100)/100.0
   +", источник="+windSource+", причина="+reason;
 }
 private static GroundSnowConfig config=new GroundSnowConfig();
 private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("whiteout-ground-snow.json");
 private static void save(){try{Files.createDirectories(FILE.getParent());Files.writeString(FILE,new GsonBuilder().setPrettyPrinting().create().toJson(config));}catch(Exception e){System.err.println("[Whiteout] Cannot save ground snow settings: "+e);}}
 private static void clear(){for(Flake p:live)p.markDead();live.clear();previous=null;cameraWeather=null;sampledWind.clear();lastGust=lastWind=lastSpawn=lastX=lastZ=0;reason="нет мира";
  attemptsTotal=rejectedSurface=rejectedSky=rejectedShelter=rejectedZone=rejectedWeather=spawnedTotal=0;
 }
 public static void initialize(){
  // Fabric gives the factory a reload-aware particle sprite provider; do not
  // reuse the hard-edged snow-block terrain sprite from the previous version.
  ParticleFactoryRegistry.getInstance().register(PowerDevices.GROUND_SNOW_PARTICLE,
   sprites -> (effect,world,x,y,z,storm,wind,unusedZ) ->
    new Flake(world,x,y,z,storm,wind,sprites));
  try{if(Files.isRegularFile(FILE)){GroundSnowConfig read=new Gson().fromJson(Files.readString(FILE),GroundSnowConfig.class);if(read!=null)config=read;}}catch(Exception e){System.err.println("[Whiteout] Invalid ground snow settings: "+e);}
  config.normalize();
  // Server-synced base intensity and matching deterministic world tick/zone define
  // the common wind. No hidden autonomous fallback while waiting for the packet.
  GroundSnowWindSource.install((dimension,tick,x,y,z,out)->{
   MinecraftClient client=MinecraftClient.getInstance();
   if(client.world==null||Bridge.snapshot==null||Bridge.snapshot.zones==null
     ||!client.world.getRegistryKey().getValue().toString().equals(dimension))return false;
   int base=EnvironmentClient.windStrengthPercent();
   if(base<0)return false;
   Zone weather=WindowExposure.weather(BlockPos.ofFloored(x,y,z),dimension);
   boolean blizzard=weather!=null&&weather.blizzard;
   double snowfall=weather==null?0:weather.blizzard?1:weather.snow;
   out.set(SharedWindField.directionX(tick),SharedWindField.directionZ(tick),
     SharedWindField.strength(tick,base/100.0,blizzard,snowfall),SharedWindField.gust(tick));
   return true;
  });
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());
  ClientTickEvents.END_CLIENT_TICK.register(GroundSnow::tick);
  ClientCommandRegistrationCallback.EVENT.register((d,a)->d.register(literal("whiteout-ground-snow")
   .executes(c->{c.getSource().sendFeedback(Text.literal("Позёмка: "+config.enabled+", интенсивность "+config.intensity+"%"));return 1;})
   .then(literal("wind").executes(c->{c.getSource().sendFeedback(Text.literal(windSetting()));return 1;})
    .then(argument("percent",IntegerArgumentType.integer(0,100)).executes(c->{config.wind=IntegerArgumentType.getInteger(c,"percent");save();c.getSource().sendFeedback(Text.literal(windSetting()));return 1;})))
   .then(literal("stats").executes(c->{c.getSource().sendFeedback(Text.literal(diagnostics()));return 1;}))
   .then(literal("enabled").then(argument("value",BoolArgumentType.bool()).executes(c->{config.enabled=BoolArgumentType.getBool(c,"value");if(!config.enabled)clear();save();c.getSource().sendFeedback(Text.literal("Позёмка: "+config.enabled));return 1;})))
   .then(literal("intensity").then(argument("percent",IntegerArgumentType.integer(0,100)).executes(c->{config.intensity=IntegerArgumentType.getInteger(c,"percent");clear();save();c.getSource().sendFeedback(Text.literal("Интенсивность позёмки: "+config.intensity+"%"));return 1;})))));
 }
 /** The highest non-air block may be a 1-layer snow cover, even when it is not
  * a solid collision surface. WORLD_SURFACE is intentional: MOTION_BLOCKING
  * can skip such covers and return the block UNDER the snow instead. */
 private record Surface(double height,BlockPos air){}
 private static Surface surface(ClientWorld w,int x,int z){
  BlockPos column=new BlockPos(x,0,z);
  if(!w.isChunkLoaded(column))return null;
  int groundY=w.getTopY(Heightmap.Type.WORLD_SURFACE,x,z)-1;
  BlockPos ground=new BlockPos(x,groundY,z);
  if(!w.isInBuildLimit(ground))return null;
  BlockState state=w.getBlockState(ground);
  boolean snow=state.isOf(Blocks.SNOW)||state.isOf(Blocks.SNOW_BLOCK)||state.isOf(Blocks.POWDER_SNOW)
    ||(state.contains(SnowyBlock.SNOWY)&&state.get(SnowyBlock.SNOWY));
  if(!snow)return null;
  var shape=state.getCollisionShape(w,ground);
  double top=state.isOf(Blocks.POWDER_SNOW)?1:state.isOf(Blocks.SNOW)?state.get(SnowBlock.LAYERS)/8.0
    :shape.isEmpty()?1:shape.getMax(Direction.Axis.Y);
  // Check the AIR ABOVE the snow for sky/roof cover, not the snow's own
  // occupied block. Sky checks inside thin snow often incorrectly fail.
  BlockPos air=ground.up();
  if(!w.isInBuildLimit(air)||!w.getBlockState(air).getCollisionShape(w,air).isEmpty())return null;
  return new Surface(groundY+top,air);
 }
 // A one-layer snow cover lies inside its block, while a full block's
 // particle is in the air above. Try both to keep thin Y-limited zones valid.
 private static Zone zoneAtSurface(Surface ground,double x,double z,String dim){
  BlockPos particle=BlockPos.ofFloored(x,ground.height()+.14,z);
  Zone zone=WindowExposure.weather(particle,dim);
  return zone!=null?zone:WindowExposure.weather(ground.air(),dim);
 }

 private static void tick(MinecraftClient mc){
  if(mc.world!=previous){clear();previous=mc.world;}
  if(mc.world==null||mc.player==null){reason="нет мира или игрока";return;}
  if(mc.isPaused()){reason="пауза";return;}
  if(Bridge.snapshot==null||Bridge.snapshot.zones==null){reason="ожидание данных зон";return;}
  long now=mc.world.getTime();live.removeIf(p->{if(now-p.born>65)p.markDead();return !p.isAlive();});
  Vec3d camera=mc.gameRenderer.getCamera().getPos();
  String dim=mc.world.getRegistryKey().getValue().toString();
  cameraWeather=WindowExposure.weather(BlockPos.ofFloored(camera),dim);
  double cameraStorm=cameraWeather==null?0:WeatherState.storm;
  sampleWind(dim,now,camera.x,camera.y,camera.z,cameraStorm);
  lastWind=sampledWind.strength;lastGust=sampledWind.gust;lastX=sampledWind.x;lastZ=sampledWind.z;windSource=sampledSource;
  lastSpawn=GroundSnowPolicy.intensity(cameraWeather==null?0:cameraWeather.blizzard?1:cameraWeather.snow,lastWind,config.intensity/100.0);
  if(!config.enabled||config.intensity==0){lastSpawn=0;reason="выключено настройками";return;}
  if(!GroundSnowWindSource.connected()&&config.wind==0){reason="штиль";return;}
  ParticlesMode mode=mc.options.getParticles().getValue();int cap=mode==ParticlesMode.MINIMAL?24:mode==ParticlesMode.DECREASED?80:MAX;
  if(live.size()>=cap){reason="лимит частиц";return;}
  int attempts=mode==ParticlesMode.MINIMAL?1:mode==ParticlesMode.DECREASED?3:8;
  reason="нет подходящей снежной поверхности";
  for(int n=0;n<attempts&&live.size()<cap;n++){
   attemptsTotal++;
   double x=camera.x+(mc.world.random.nextDouble()-.5)*24;
   double z=camera.z+(mc.world.random.nextDouble()-.5)*24;
   Surface ground=surface(mc.world,MathHelper.floor(x),MathHelper.floor(z));
   if(ground==null||Math.abs(ground.height()-camera.y)>5){rejectedSurface++;continue;}
   if(!mc.world.isSkyVisible(ground.air())){rejectedSky++;reason="нет открытого неба";continue;}
   double y=ground.height()+.12;
   Zone weather=zoneAtSurface(ground,x,z,dim);
   // No weather zone is ordinary clear weather, not a reason to suppress wind.
   if(weather==null)rejectedZone++;
   double storm=weather!=null&&weather.blizzard?1:0,snow=weather==null?0:weather.blizzard?1:weather.snow;
   if(weather!=null&&weather==cameraWeather)storm=WeatherState.storm;
   sampleWind(dim,now,x,y,z,storm);
   double wind=sampledWind.strength;
   double amount=GroundSnowPolicy.intensity(snow,wind,config.intensity/100.0);
   reason="вероятность спавна";
   if(amount<=0){rejectedWeather++;reason="штиль или нет данных общего ветра";continue;}
   if(mc.world.random.nextDouble()>=amount){rejectedWeather++;continue;}
   if(WeatherState.sheltered(x,y,z,dim)){rejectedShelter++;reason="укрытие";continue;}
   // Short, intermittent gusts are preferable to a solid fog carpet.
   int group=mode!=ParticlesMode.MINIMAL && mc.world.random.nextDouble()<GroundSnowPolicy.groupChance(wind)?2:1;
   double wx=sampledWind.x,wz=sampledWind.z;
   double length=Math.sqrt(wx*wx+wz*wz);
   for(int i=0;i<group&&live.size()<cap;i++){
    double offset=i*.19;
    double px=x-(wx/length)*offset,pz=z-(wz/length)*offset;
    // Do not sample a different block without checking its snow top.
    Surface part=surface(mc.world,MathHelper.floor(px),MathHelper.floor(pz));
    if(part==null||Math.abs(part.height()-ground.height())>.30||!mc.world.isSkyVisible(part.air()))continue;
    if(zoneAtSurface(part,px,pz,dim)!=weather||WeatherState.sheltered(px,part.height()+.1,pz,dim))continue;
    // Carry lifetime input explicitly: the particle factory must not depend on scratch state.
    Particle p=mc.particleManager.addParticle(PowerDevices.GROUND_SNOW_PARTICLE,
      px,part.height()+.065+mc.world.random.nextDouble()*.045,pz,storm,wind,0);
    if(p instanceof Flake flake){live.add(flake);spawnedTotal++;reason="создаются шлейфы";}
   }
  }
 }
 private static String windSetting(){return "Автономный ветер позёмки: "+config.wind+"%"+
  (GroundSnowWindSource.connected()?"; подключён общий источник: это значение не применяется, плотность регулируется intensity":"; движение игрока не меняется");}
 private static void sampleWind(String dim,long tick,double x,double y,double z,double storm){
  if(GroundSnowWindSource.connected()){
   boolean ready=GroundSnowWindSource.read(dim,tick,x,y,z,sampledWind);
   sampledSource=ready?"общий провайдер":"общий провайдер: данные недоступны";
  }else{
   sampledSource="автономный, НЕ синхронизирован";
   double length=Math.hypot(10.8,3.3);
   double gust=GroundSnowPolicy.gustAt(tick);
   sampledWind.set(10.8/length,3.3/length,GroundSnowPolicy.strength(config.wind/100.0,storm,gust),gust);
  }
 }
 private static final class Flake extends SpriteBillboardParticle {
  final long born;final double variation,phase;double storm,renderGround;
  Flake(ClientWorld w,double x,double y,double z,double storm,double wind,SpriteProvider sprites){
   super(w,x,y,z);born=w.getTime();this.storm=storm;variation=.65+random.nextDouble()*.6;phase=random.nextDouble()*Math.PI*2;
   maxAge=GroundSnowPolicy.lifetime(wind)+random.nextInt(9);scale=.42f+random.nextFloat()*.31f;
   collidesWithWorld=true;setBoundingBoxSpacing(.028f,.025f);
   renderGround=y-.065;
   // Dedicated translucent wisps: alpha is zero outside the narrow feathered streak.
   setSprite(sprites);setColor(.90f,.94f,1.0f);setAlpha(0);
  }
  @Override public ParticleTextureSheet getType(){return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;}
  @Override public void buildGeometry(net.minecraft.client.render.VertexConsumer out,
    net.minecraft.client.render.Camera camera,float tickDelta){
   Vec3d eye=camera.getPos();
   double px=MathHelper.lerp(tickDelta,prevPosX,x)-eye.x;
   double py=Math.max(MathHelper.lerp(tickDelta,prevPosY,y),renderGround+.035)-eye.y;
   double pz=MathHelper.lerp(tickDelta,prevPosZ,z)-eye.z;
   double yaw=Math.toRadians(camera.getYaw());
   double tilt=GroundSnowBillboard.tilt(camera.getPitch());
   double rx=-Math.cos(yaw)*scale,rz=-Math.sin(yaw)*scale;
   double height=scale*.35;
   double ux=-Math.sin(yaw)*Math.sin(tilt)*height;
   double uy=Math.cos(tilt)*height,uz=Math.cos(yaw)*Math.sin(tilt)*height;
   int light=GroundSnowBillboard.softLight(getBrightness(tickDelta));
   // The supplied sprites contain a thin horizontal wisp only in rows 16..44/64.
   // Trim fully transparent padding, not the soft edge; otherwise a low quad is squeezed twice.
   float v0=MathHelper.lerp(16/64f,getMinV(),getMaxV());
   float v1=MathHelper.lerp(44/64f,getMinV(),getMaxV());
   // Bottom anchored above the sampled snow top; never rotate a large quad below its centre.
   // The same four vertices are submitted regardless of wind or viewing angle.
   vertex(out,px-rx,py,pz-rz,getMaxU(),v1,light);
   vertex(out,px+rx,py,pz+rz,getMinU(),v1,light);
   vertex(out,px+rx+ux,py+uy,pz+rz+uz,getMinU(),v0,light);
   vertex(out,px-rx+ux,py+uy,pz-rz+uz,getMaxU(),v0,light);
  }
  private void vertex(net.minecraft.client.render.VertexConsumer out,double x,double y,double z,
    float u,float v,int light){
   out.vertex((float)x,(float)y,(float)z).texture(u,v).color(red,green,blue,alpha).light(light);
  }
  @Override public void tick(){
   prevPosX=x;prevPosY=y;prevPosZ=z;
   if(age++>=maxAge||!config.enabled||world!=previous||Bridge.snapshot==null||Bridge.snapshot.zones==null){markDead();return;}
   var mc=MinecraftClient.getInstance();if(mc.player==null||mc.gameRenderer.getCamera().getPos().squaredDistanceTo(x,y,z)>24*24){markDead();return;}
   String dim=world.getRegistryKey().getValue().toString();
   Surface current=surface(world,MathHelper.floor(x),MathHelper.floor(z));
   if(current==null||!world.isSkyVisible(current.air())){markDead();return;}
   if(age%4==0){
    Zone weather=zoneAtSurface(current,x,z,dim);
    storm=weather!=null&&weather==cameraWeather?WeatherState.storm:weather!=null&&weather.blizzard?1:0;
   }
   sampleWind(dim,world.getTime(),x,y,z,storm);
   double strength=sampledWind.strength;
   double speed=GroundSnowPolicy.speed(strength)*variation/20;
   double dx=sampledWind.x*speed,dz=sampledWind.z*speed;
   Surface next=surface(world,MathHelper.floor(x+dx),MathHelper.floor(z+dz));
   if(next==null||Math.abs(next.height()+.12-y)>.55
     ||WeatherState.sheltered(x+dx,next.height()+.14,z+dz,dim)
     ||!world.isSkyVisible(next.air())){markDead();return;}
   // Render-only clearance for an upward snow-layer step during position interpolation.
   renderGround=Math.max(current.height(),next.height());
   // Stay only a few centimetres above the terrain rather than floating as cards.
   double target=next.height()+.085+.027*Math.sin(phase+age*.27);
   move(dx,MathHelper.clamp(target-y,-.055,.055),dz);
   if(Math.abs(x-prevPosX-dx)>.01||Math.abs(z-prevPosZ-dz)>.01){markDead();return;}
   // Soft envelope: individual wisps should blend into the storm, not pop in/out.
   setAlpha(.72f*(float)Math.min(1,strength/.08)*Math.min(1,age/7f)*Math.min(1,(maxAge-age)/9f));
  }
 }
}
