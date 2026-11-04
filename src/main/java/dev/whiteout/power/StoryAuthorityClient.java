package dev.whiteout.power;

import java.util.function.*;
import dev.whiteout.client.Bridge;
import dev.whiteout.client.director.DirectorPlayer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;

/** Presentation follows acknowledgement. Never speculatively changes Bridge.snapshot. */
public final class StoryAuthorityClient {
 private static final StoryReceipts.Session SESSION=new StoryReceipts.Session();
 private static long tick;
 private static Object world;
 private static BooleanSupplier valid,ready,present;
 private static String key,deferredKey,earlyAgreement;private static Runnable deferred;
 private static Consumer<Boolean> finished;
 private StoryAuthorityClient(){}
 public static void register(){
  ClientPlayNetworking.registerGlobalReceiver(StoryAuthorityNetwork.Reply.ID,(p,c)->c.client().execute(()->SESSION.accept(new StoryReceipts.Reply(p.token(),p.outcome()),tick)));
  ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{reset();deferred=null;deferredKey=null;});
  DuoPresentation.register();
  ClientTickEvents.END_CLIENT_TICK.register(c->{
   tick++;
   if(c.world==null||c.player==null||c.world!=world){reset();return;}
   if(!SESSION.busy())return;
   var retry=SESSION.retry(tick);if(retry!=null)send(retry);
   var outcome=SESSION.outcome();if(outcome==null)return;
   if(outcome==StoryReceipts.Outcome.WAITING){
    var check=valid;var action=present;var expected=world;var cb=finished;deferredKey=key;
    deferred=()->{if(c.world==expected&&check.getAsBoolean()){boolean ok=action.getAsBoolean();if(cb!=null)cb.accept(ok);}};
    boolean alreadyAgreed=key.equals(earlyAgreement);reset();
    if(alreadyAgreed)agreed(deferredKey);else Bridge.message("Выбор сохранён. Ожидание решения напарника.");return;
   }
   if(outcome!=StoryReceipts.Outcome.COMMITTED){finish(false);return;}
   if(!valid.getAsBoolean()||SESSION.presentationExpired(tick)){finish(false);return;}
   if(!ready.getAsBoolean())return;
   var action=present;var callback=finished;reset();
   boolean success=false;
   try{success=action.getAsBoolean();}catch(RuntimeException failure){Bridge.message("Whiteout: "+failure.getClass().getSimpleName());}
   if(callback!=null)callback.accept(success);
  });
 }
 static void agreed(String k){if(k.equals(deferredKey)&&deferred!=null){var run=deferred;deferred=null;deferredKey=null;run.run();}else if(SESSION.busy()&&k.equals(key))earlyAgreement=k;}
 private static void send(StoryReceipts.Request request){
  try{ClientPlayNetworking.send(new StoryAuthorityNetwork.Request(request.token(),request.key()));}
  catch(RuntimeException failure){finish(false);}
 }
 private static void finish(boolean value){var callback=finished;reset();if(callback!=null)callback.accept(value);}
 public static boolean busy(){return SESSION.busy();}
 static void cancel(){reset();deferred=null;deferredKey=null;}
 public static void reset(){SESSION.clear();earlyAgreement=null;world=null;valid=ready=present=null;finished=null;}
 public static boolean request(String key,BooleanSupplier stillValid,BooleanSupplier canPresent,BooleanSupplier action,Consumer<Boolean> callback){
  var mc=MinecraftClient.getInstance();
  if(mc==null||mc.player==null||mc.world==null||SESSION.busy()||!ClientPlayNetworking.canSend(StoryAuthorityNetwork.Request.ID))return false;
  StoryReceipts.Request request;
  try{request=SESSION.begin(key,tick);}catch(IllegalArgumentException invalid){return false;}
  deferred=null;deferredKey=null;StoryAuthorityClient.key=key;
  world=mc.world;valid=stillValid;ready=canPresent;present=action;finished=callback;
  send(request);return true;
 }
 public static boolean fire(String key,BooleanSupplier action,Consumer<Boolean> callback){
  var mc=MinecraftClient.getInstance();
  if(mc==null||mc.currentScreen!=null||DirectorPlayer.playing())return false;
  return request(key,()->true,()->mc.currentScreen==null&&!DirectorPlayer.playing(),action,callback);
 }
}
