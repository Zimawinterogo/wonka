package dev.whiteout.power;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;

public final class PowerDiagnosticsClient {
 private static final SnapshotChunks.Receiver RECEIVER=new SnapshotChunks.Receiver();
 private static int requested,receiving=-1;
 private PowerDiagnosticsClient(){}
 public static void register(){
  ClientPlayConnectionEvents.DISCONNECT.register((handler,mc)->{RECEIVER.clear();receiving=-1;requested=0;});
  ClientPlayNetworking.registerGlobalReceiver(PowerDiagnostics.Part.ID,(p,c)->c.client().execute(()->{
   if(c.client().player==null||(p.token()!=0&&p.token()!=requested))return;
   try{
    if(p.index()==0){RECEIVER.clear();receiving=p.token();}
    if(receiving!=p.token())return;
    String json=RECEIVER.accept(p.index(),p.total(),p.json());if(json==null)return;
    var snapshot=new Gson().fromJson(json,DiagnosticData.Snapshot.class);
    if(snapshot==null||snapshot.rows()==null||snapshot.rows().size()>DiagnosticData.MAX_ROWS)return;
    if(c.client().currentScreen instanceof PowerDiagnosticsScreen screen)screen.update(snapshot);
    else if(p.token()==0)c.client().setScreen(new PowerDiagnosticsScreen(snapshot));
   }catch(RuntimeException ex){RECEIVER.clear();receiving=-1;}
  }));
 }
 public static void refresh(){
  if(MinecraftClient.getInstance().getNetworkHandler()==null)return;
  requested=requested==Integer.MAX_VALUE?1:requested+1;
  RECEIVER.clear();receiving=-1;
  ClientPlayNetworking.send(new PowerDiagnostics.Request(requested));
 }
}
