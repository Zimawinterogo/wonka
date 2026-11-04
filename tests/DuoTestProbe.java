package dev.whiteout.power;

import dev.whiteout.power.lobby.LobbyState;
import dev.whiteout.power.lobby.PhotoTransfer;
import java.nio.file.*;
import java.util.*;

public final class DuoTestProbe {
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void run()throws Exception{
  var directory=Files.createTempDirectory(Path.of("."),"duo-probe-");
  var user=UUID.randomUUID();var bot=DuoTestMode.BOT;
  var point=new ScopedStory.Position("minecraft:overworld",0,64,0,0,0);
  var example=new StoryEventCatalog(Path.of("../../docs/examples/duo-final-events.json"));
  check(!example.invalid(),example.error());
  try{
   for(var expected:List.of("loss","mercy","letter")){
    var file=directory.resolve(expected+".json");var t=new DuoTestMode(user,file,point);
    t.humanFirst=!expected.equals("mercy");t.prepare();
    try{t.prepare();throw new AssertionError("duplicate prepare");}catch(IllegalStateException ok){}
    try{t.launch();throw new AssertionError("started without ready");}catch(IllegalStateException ok){}
    t.ready=true;t.launch();var d=LobbyState.get(t.store);
    check(d.mode()==LobbyState.Mode.DUO&&d.second().equals(t.humanFirst?bot:user),"role mapping");
    var first=d.first();var second=d.second();var region=example.find("cutscene_trigger:final_meeting").area();
    check(DuoTestMode.inside(region,point),"inside");
    check(!DuoTestMode.inside(region,new ScopedStory.Position(point.dimension(),4,64,0,0,0)),"upper boundary");
    check(!DuoTestMode.inside(region,new ScopedStory.Position("minecraft:the_nether",0,64,0,0,0)),"dimension");
    check(!t.vote(user,"yes")&&!t.vote(bot,"no")&&t.vote(bot,"yes"),"waiting, disagreement, agreement");
    var camera=UUID.randomUUID();t.loadDelay=-1;
    check(!t.botLoaded(camera,10)&&!t.botLoaded(camera,209)&&t.botLoaded(camera,210),"silent client timeout");
    t.connected=false;check(!t.botLoaded(camera,211),"disconnected client");t.connected=true;t.loadDelay=4;
    camera=UUID.randomUUID();check(!t.botLoaded(camera,220)&&t.botLoaded(camera,224),"delayed reconnect");
    var letter=example.find("dialogue_choice:letter:0:0");
    if(expected.equals("letter")){
     ScopedStory.apply(t.store,second,example.find("cutscene_trigger:letter_area"),point);
     ScopedStory.apply(t.store,second,letter,point);
     check(ScopedStory.read(t.store.data,ScopedStory.Scope.PLAYER,second).objectives.get("test_letter").status==dev.whiteout.ObjectiveRules.Status.COMPLETED,"private objective completed");
     check(!ScopedStory.snapshot(t.store,first).objectives.containsKey("test_letter"),"secret hidden from first");
    }
    var choice=example.find("dialogue_choice:final_second:0:"+(expected.equals("loss")?0:1));
    var ending=Narrative.chooseEnding(t.store,d,choice.narrative().endings());check(ending.id().equals(expected),"eligible ending");
    var exit=example.find(ending.event());check(Narrative.finish(t.store,choice,ending,exit,new SceneDelivery.Ledger())==StoryCommitTransaction.Result.COMMITTED,"finale commit");
    var reopened=new DuoTestMode(user,file,point);check(!reopened.prepared&&!reopened.started(),"disabled after restart");reopened.prepare();
    check(reopened.started()&&SharedScene.current(reopened.store).spec().scene().equals("final_"+expected),"explicit resume");
    check(reopened.store.data.completedEvents.contains(Narrative.FINISHED)&&reopened.store.data.completedEvents.contains("whiteout:ending/"+expected),"durable ending");
   }
   var retries=new PhotoTransfer.RetrySchedule();var due=new ArrayList<Integer>();for(int tick=1;tick<=1000;tick++)if(retries.due())due.add(tick);
   check(due.equals(List.of(85,170,255)),"bounded retry schedule");
   Set<Integer> inFlight=new HashSet<>();check(!PhotoTransfer.reserve(inFlight,0,()->false)&&inFlight.isEmpty(),"rate limit does not consume slot");
   check(PhotoTransfer.reserve(inFlight,0,()->true)&&!PhotoTransfer.reserve(inFlight,0,()->true),"single transfer");
   inFlight.remove(0);check(PhotoTransfer.reserve(inFlight,0,()->true),"failed transfer can retry");
  }finally{try(var files=Files.list(directory)){for(var f:files.toList())Files.delete(f);}Files.delete(directory);}
  System.out.println("PASS isolated Duo: stable roles, readiness, vote, zone boundaries, photo retry, scene timeout, private objective, three committed endings and explicit restart");
 }
}
