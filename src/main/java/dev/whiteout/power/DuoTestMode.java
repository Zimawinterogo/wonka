package dev.whiteout.power;

import dev.whiteout.Store;
import dev.whiteout.StoryRules;
import dev.whiteout.StoryStoreReader;
import dev.whiteout.net.WhiteoutNetwork;
import dev.whiteout.power.lobby.LobbyState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** A development participant, scoped to one consenting operator and a private Store. */
public final class DuoTestMode {
 public static final UUID BOT=UUID.nameUUIDFromBytes("whiteout:duo-test-bot".getBytes(java.nio.charset.StandardCharsets.UTF_8));
 private static final Map<MinecraftServer,DuoTestMode> sessions=new WeakHashMap<>();
 final UUID operator;
 final Store store;
 final Path path;
 boolean connected=true,follow=true,ready,prepared,botSkip;
 private boolean humanFirst=true;
 private ScopedStory.Position botPosition;
 private TriggerEvidence humanEvidence=new TriggerEvidence(),botEvidence=new TriggerEvidence();
 private final Map<UUID,String> ballots=new HashMap<>();
 private final Map<UUID,Reader> readers=new HashMap<>();
 private record Reader(UUID id,String script,boolean barrier,int since){}
 private UUID camera;private int cameraSince,loadDelay,readDelay=-1;private String manifest="";
 DuoTestMode(UUID operator,Path path,ScopedStory.Position position){
  this.operator=operator;this.path=path;this.store=StoryStoreReader.open(path);botPosition=position;
  if(store.error!=null)throw new IllegalStateException("Тестовое сохранение повреждено");
  var old=LobbyState.get(store);if(old!=null){humanFirst=operator.equals(old.first());if(!operator.equals(humanFirst?old.first():old.second())||!BOT.equals(humanFirst?old.second():old.first()))throw new IllegalArgumentException("Другой владелец сохранения");}
 }
 public static DuoTestMode active(ServerPlayerEntity p){var t=sessions.get(p.getServer());return t!=null&&t.operator.equals(p.getUuid())?t:null;}
 static ScopedStory.Position position(ServerPlayerEntity p){return new ScopedStory.Position(p.getServerWorld().getRegistryKey().getValue().toString(),p.getX(),p.getY(),p.getZ(),p.getYaw(),p.getPitch());}
 static boolean inside(StoryEventCatalog.Area area,ScopedStory.Position at){return area.contains(at.dimension(),at.x(),at.y(),at.z());}
 private static void need(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
 private static void tell(ServerPlayerEntity p,String line){p.sendMessage(Text.literal("[Duo Test] "+line));}
 public boolean started(){var d=LobbyState.get(store);return prepared&&d!=null&&d.started();}
 private void roster(boolean launch)throws Exception{
  var access=(LobbyState.Access)(Object)store.data;var old=access.whiteout$lobby();
  var card=new LobbyState.Card("Duo Test","Изолированный прогон","","","");
  var d=new LobbyState(card,humanFirst?operator:BOT,humanFirst?BOT:operator,launch,old==null?1:old.revision()+1);
  access.whiteout$lobby(d);boolean added=launch&&store.data.completedEvents.add(LobbyState.STARTED);
  try{store.save();}catch(Exception ex){access.whiteout$lobby(old);if(added)store.data.completedEvents.remove(LobbyState.STARTED);throw ex;}
 }
 void prepare()throws Exception{need(!prepared,"Сессия уже подготовлена");if(LobbyState.get(store)==null)roster(false);prepared=true;}
 public void launch()throws Exception{need(prepared&&connected&&ready&&!started(),"Нужны start, ready и незапущенная сессия");roster(true);}
 public dev.whiteout.power.lobby.Party.Status statusView(ServerPlayerEntity p,LobbyState actual){
  var point=position(p);return new dev.whiteout.power.lobby.Party.Status(actual==null?0:actual.revision(),started(),humanFirst?operator:BOT,humanFirst?BOT:operator,null,true,ready,prepared&&ready&&connected&&!started(),
   List.of(new dev.whiteout.power.lobby.Party.Person(operator,p.getGameProfile().getName()),new dev.whiteout.power.lobby.Party.Person(BOT,"Whiteout Test Bot")),point.dimension(),point.x(),point.y(),point.z(),null,null);
 }
 public Store.Data snapshot(Store.Data result){var state=ScopedStory.snapshot(store,operator);StoryObjectiveActions.State.capture(state).publish(result);result.checkpoints=state.checkpoints;result.storyResetEpoch=state.storyResetEpoch;return result;}
 private UUID role(Narrative.Role role,UUID actor){var d=LobbyState.get(store);return switch(role){case ANY->actor;case FIRST->d.first();case SECOND->d.second();};}
 private TriggerEvidence evidence(UUID id){return id.equals(BOT)?botEvidence:humanEvidence;}
 private ScopedStory.Position at(UUID id,ServerPlayerEntity p){return id.equals(BOT)?botPosition:position(p);}
 private boolean eligible(ServerPlayerEntity p,UUID actor,StoryEventCatalog.Event e){
  if(!started()||!connected||!p.isAlive()||p.isSpectator()||e==null||e.serverOnly()||store.data.completedEvents.contains(Narrative.FINISHED)||SharedScene.current(store)!=null)return false;
  var d=LobbyState.get(store);var n=e.narrative();var owner=actor;
  if(n!=null){
   if(n.mode()==Narrative.Mode.SOLO||!actor.equals(role(n.actor(),actor))||!Narrative.matches(store,d,n.when()))return false;
   if(n.together()&&(!inside(e.area(),position(p))||!inside(e.area(),botPosition)))return false;
   owner=role(n.recipient(),actor);
  }
  if(readers.values().stream().anyMatch(Reader::barrier)){var reader=readers.get(actor);if(reader==null||!e.key().startsWith("dialogue_choice:"+reader.script+":"))return false;}
  var data=ScopedStory.read(store.data,e.scope(),owner);
  if(e.once()&&data.completedEvents.contains(e.key())||!StoryRules.matches(data.storyVariables,e.conditions()))return false;
  var source=new StoryWorldSource(p,data);
  return StoryWorldConditions.evaluate(e.world(),node->{
   if(node.metric()==StoryWorldConditions.Metric.PLAYER_COUNT)return OptionalDouble.of(position(p).dimension().equals(botPosition.dimension())?2:1);
   if(actor.equals(BOT)&&!position(p).equals(botPosition))switch(node.metric()){
    case SHELTER_PROTECTION,COLD_LEVEL,BLIZZARD,WIND_STRENGTH,SNOWFALL,ZONE_ACTIVE,TIME_OF_DAY->{return OptionalDouble.empty();}default->{}
   }
   return source.sample(node);
  })==WinterCrisisConditions.Truth.TRUE;
 }
 private boolean admitted(ServerPlayerEntity p,UUID id,StoryEventCatalog.Event event){
  if(!eligible(p,id,event))return false;var point=at(id,p);var binding=event.trigger();
  if(binding==null)return inside(event.area(),point);
  var rule=new TriggerEvidence.Rule(event.key(),event.area(),binding);
  if(!evidence(id).permits(rule,p.getServer().getTicks()))return false;
  if(binding.zone())return point.dimension().equals(event.area().dimension())&&body(point).intersects(event.area())==(binding.type()==TriggerEvidence.Kind.ENTER_BOX);
  return event.area()==null||inside(event.area(),point);
 }
 static TriggerEvidence.Body body(ScopedStory.Position p){return new TriggerEvidence.Body(p.dimension(),p.x()-.3,p.y(),p.z()-.3,p.x()+.3,p.y()+1.8,p.z()+.3);}
 boolean vote(UUID who,String key){ballots.put(who,key);return key.equals(ballots.get(who.equals(BOT)?operator:BOT));}
 StoryReceipts.Outcome act(ServerPlayerEntity p,UUID actor,String key)throws Exception{
  var server=p.getServer();var e=StoryAuthority.definition(server,key);if(!admitted(p,actor,e))return StoryReceipts.Outcome.DENIED;
  var n=e.narrative();var peer=actor.equals(BOT)?operator:BOT;
  if(n!=null&&n.voteGroup()!=null&&(!vote(actor,key)||!admitted(p,peer,e)))return StoryReceipts.Outcome.WAITING;
  var owner=n==null?actor:role(n.recipient(),actor);
  for(var scene:e.scenes())if(!CrisisRuntime.requestable(server,scene))return StoryReceipts.Outcome.DENIED;
  var result=Narrative.commit(p,store,owner,e,at(actor,p));
  if(result!=StoryCommitTransaction.Result.COMMITTED)return StoryReceipts.Outcome.valueOf(result.name());
  if(e.trigger()!=null){var r=new TriggerEvidence.Rule(key,e.area(),e.trigger());evidence(actor).consume(r,server.getTicks());
   if(n!=null&&(n.together()||n.voteGroup()!=null||n.firstDialogue()!=null||n.secondDialogue()!=null))evidence(peer).consume(r,server.getTicks());}
  if(n!=null){
   if(n.voteGroup()!=null){ballots.clear();if(actor.equals(BOT))send(p,new DialogueBarrier.Cue(UUID.randomUUID(),"agreed",key));}
   for(var who:List.of(operator,BOT)){
    if(n.visibility()!=Narrative.Visibility.SHARED&&!who.equals(owner))continue;
    var script=who.equals(LobbyState.get(store).first())?n.firstDialogue():n.secondDialogue();
    if(script!=null){var reader=new Reader(UUID.randomUUID(),script,n.waitForReaders(),server.getTicks());readers.put(who,reader);
     if(who.equals(operator))send(p,new DialogueBarrier.Cue(reader.id,"dialogue",script));else tell(p,"Бот читает "+script);}
   }
  }
  WhiteoutNetwork.sync(p);return StoryReceipts.Outcome.COMMITTED;
 }
 private static void send(ServerPlayerEntity p,DialogueBarrier.Cue cue){if(ServerPlayNetworking.canSend(p,DialogueBarrier.Cue.ID))ServerPlayNetworking.send(p,cue);}
 void read(UUID id){var r=readers.get(operator);if(r!=null&&r.id.equals(id))readers.remove(operator);}
 boolean botLoaded(UUID id,int tick){
  if(!id.equals(camera)){camera=id;cameraSince=tick;botSkip=false;}
  return connected&&(tick-cameraSince>=200||loadDelay>=0&&tick-cameraSince>=loadDelay);
 }
 private void tick(ServerPlayerEntity p)throws Exception{
  if(!started())return;var server=p.getServer();int tick=server.getTicks();
  if(follow&&connected)botPosition=position(p);
  var r=readers.get(BOT);if(connected&&r!=null&&readDelay>=0&&tick-r.since>=readDelay)readers.remove(BOT);
  var catalog=StoryAuthority.catalog(server);var visible=new StringJoiner("\n");
  for(var rule:catalog.rules()){
   var e=catalog.bound(rule.key());
   if(tick%10==0)visible.add((eligible(p,operator,e)?"+":"-")+catalog.fingerprint(rule.key()));
   for(var who:List.of(operator,BOT)){
    if(!connected)break;
    evidence(who).observe(rule,body(at(who,p)),tick,k->eligible(p,who,e),k->{
     try{if(who.equals(BOT)){var outcome=act(p,who,k);if(outcome==StoryReceipts.Outcome.COMMITTED)tell(p,"Бот выполнил "+k);}
      else if(ServerPlayNetworking.canSend(p,TriggerAuthority.Notice.ID))ServerPlayNetworking.send(p,new TriggerAuthority.Notice(false,k));}
     catch(Exception ex){tell(p,"Событие отклонено: "+k);}
    });
   }
  }
  if(tick%10==0&&!visible.toString().equals(manifest)&&ServerPlayNetworking.canSend(p,TriggerAuthority.Notice.ID)){manifest=visible.toString();ServerPlayNetworking.send(p,new TriggerAuthority.Notice(true,manifest));}
  SharedScene.tick(server,store,this);
 }
 private void detach(ServerPlayerEntity p){
  var playing=SharedScene.current(store);if(playing!=null)send(p,new DialogueBarrier.Cue(playing.id(),"finish",""));
  SharedScene.forget(store);sessions.remove(p.getServer());TriggerAuthority.reset(p);WhiteoutNetwork.sync(p);
 }
 private String status(){var r=readers.get(BOT);return "Подготовлен="+prepared+", запуск="+started()+", оператор="+(humanFirst?"FIRST":"SECOND")+", бот="+(connected?"онлайн":"офлайн")+", готов="+ready+", позиция="+botPosition+", диалог="+(r==null?"—":r.script)+", голоса="+ballots+", камера="+SharedScene.current(store);}
 private static final String[] COMMANDS={"enable confirm","disable","start","stop","role first","role second","follow","stay","here","ready","launch","disconnect","reconnect","skip","status","reset confirm","read","reading manual","reading 60","load normal","load none","load 60","state","choices","choose","act"};
 private static int command(ServerPlayerEntity p,String input){
  try{
   need(p.hasPermissionLevel(2),"Нужны права оператора");var server=p.getServer();var t=active(p);String[] words=input.strip().split("\\s+",2);String op=words[0],arg=words.length==2?words[1]:"";
   if(input.equals("enable confirm")){
    need(!sessions.containsKey(server),"Тест уже включён");need(!dev.whiteout.power.lobby.Party.member(LobbyState.get(Store.get(server)),p.getUuid())||!LobbyState.get(Store.get(server)).started(),"Завершите обычное прохождение или используйте копию мира");
    var dir=server.getSavePath(WorldSavePath.ROOT).resolve("whiteout-duo-tests");Files.createDirectories(dir);
    var path=dir.resolve(p.getUuid()+".json");sessions.put(server,new DuoTestMode(p.getUuid(),path,position(p)));TriggerAuthority.reset(p);tell(p,"Тест включён. Используйте start");return 1;
   }
   need(t!=null,"Сначала выполните enable confirm");
   switch(op){
    case "start"->t.prepare();
    case "disable","stop"->{t.detach(p);tell(p,"Сессия закрыта. Сохранён только отдельный тестовый файл");return 1;}
    case "role"->{need(!t.started()&&Set.of("first","second").contains(arg),"Выберите first или second перед запуском");t.humanFirst=arg.equals("first");t.roster(false);}
    case "ready"->{need(t.prepared&&t.connected,"Бот недоступен");t.ready=true;}
    case "launch"->t.launch();
    case "follow","here","stay"->{need(t.connected,"Бот отключён");if(!op.equals("stay"))t.botPosition=position(p);if(!op.equals("here"))t.follow=op.equals("follow");}
    case "disconnect","reconnect"->{boolean online=op.equals("reconnect");need(t.connected!=online,"Состояние уже установлено");t.connected=online;t.ballots.clear();t.botSkip=false;t.camera=null;if(!t.started())t.ready=false;}
    case "read"->{need(t.connected&&t.readers.remove(BOT)!=null,"Бот не читает диалог");}
    case "reading"->t.readDelay=arg.equals("manual")?-1:delay(arg);
    case "load"->t.loadDelay=arg.equals("normal")?0:arg.equals("none")?-1:delay(arg);
    case "skip"->{var scene=SharedScene.current(t.store);need(scene!=null&&t.botLoaded(scene.id(),server.getTicks()),"Бот не загрузил сцену");t.botSkip=true;}
    case "choose","act"->{var e=StoryAuthority.definition(server,arg);need(!op.equals("choose")||e.trigger()!=null&&e.trigger().type()==TriggerEvidence.Kind.CHOICE,"Нужен ключ варианта диалога");tell(p,"Результат действия бота: "+t.act(p,BOT,arg));return 1;}
    case "choices"->{var keys=new ArrayList<String>();for(var rule:StoryAuthority.catalog(server).rules())if(rule.binding().type()==TriggerEvidence.Kind.CHOICE&&t.admitted(p,BOT,StoryAuthority.definition(server,rule.key())))keys.add(rule.key());tell(p,"Допустимые ответы: "+keys);return 1;}
    case "state"->{for(var who:List.of(t.operator,BOT)){var data=ScopedStory.read(t.store.data,ScopedStory.Scope.PLAYER,who);tell(p,(who.equals(BOT)?"Бот":"Игрок")+" · переменные "+Store.GSON.toJson(data.storyVariables)+" · события "+data.completedEvents+" · цели "+data.objectives.keySet()+" · точки "+data.checkpoints.keySet());}tell(p,"Общие события "+t.store.data.completedEvents);return 1;}
    case "reset"->{need(arg.equals("confirm"),"Нужно reset confirm");t.detach(p);for(var suffix:List.of("",".bak",".tmp"))Files.deleteIfExists(t.path.resolveSibling(t.path.getFileName()+suffix));sessions.put(server,new DuoTestMode(p.getUuid(),t.path,position(p)));tell(p,"Удалён только ваш тестовый прогресс");return 1;}
    case "status"->{}
    default->throw new IllegalArgumentException("Доступные действия: "+String.join(", ",COMMANDS));
   }
   tell(p,t.status());return 1;
  }catch(Exception failure){tell(p,"Действие отклонено. "+Objects.toString(failure.getMessage(),"Проверьте параметры"));return 0;}
 }
 private static int delay(String value){try{int ticks=Integer.parseInt(value);if(ticks>=0&&ticks<=1200)return ticks;}catch(NumberFormatException ignored){}throw new IllegalArgumentException("Задержка от 0 до 1200 тиков");}
 static void register(){
  CommandRegistrationCallback.EVENT.register((cmd,reg,env)->cmd.register(literal("whiteout").then(literal("test-duo").requires(src->src.hasPermissionLevel(2)).executes(c->{c.getSource().sendFeedback(()->Text.literal(String.join(", ",COMMANDS)),false);return 1;}).then(argument("действие",StringArgumentType.greedyString()).suggests((c,b)->{for(var a:COMMANDS)if(a.startsWith(b.getRemaining()))b.suggest(a);return b.buildFuture();}).executes(c->command(c.getSource().getPlayerOrThrow(),StringArgumentType.getString(c,"действие")))))));
  ServerTickEvents.END_SERVER_TICK.register(s->{var t=sessions.get(s);if(t==null)return;var p=s.getPlayerManager().getPlayer(t.operator);if(p==null)return;if(!p.hasPermissionLevel(2)){t.detach(p);return;}try{t.tick(p);}catch(Exception ex){if(s.getTicks()%200==0)tell(p,"Ошибка теста: "+ex.getMessage());}});
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{var t=active(h.getPlayer());if(t!=null){SharedScene.forget(t.store);sessions.remove(s);}});
  ServerLifecycleEvents.SERVER_STOPPED.register(sessions::remove);
 }
}
