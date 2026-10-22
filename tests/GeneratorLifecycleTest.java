import dev.whiteout.power.*;
import java.util.*;

public final class GeneratorLifecycleTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static final class Sound {boolean stopped;int stops;void stop(){stopped=true;stops++;}}
 public static void main(String[] args){
  var m=new GeneratorMotion();check(!m.tick(false,true),"idle load");
  check(m.tick(true,true),"single start burst");
  check(!m.loopReady(),"loop must wait for start sample");
  for(int i=1;i<83;i++){check(!m.tick(true,true),"no repeated burst");check(!m.loopReady(),"no double engine");}
  m.tick(true,true);check(m.loopReady(),"84 ticks start sample");
  for(int i=0;i<40;i++)m.tick(true,true);
  check(m.loopVolume(true)==1,"two-second loop fade");
  for(int i=0;i<100;i++){
   m.tick(true,true);
   check(Math.abs(m.vibration(true,.5f,123))<=.00601,"worn motion bound");
   check(Math.abs(m.vibration(false,.5f,123))<=.00251,"modern motion bound");
   check(m.indicator(true,.5f,123)>=.85&&m.indicator(true,.5f,123)<=1,"subtle indicator");
  }
  m.tick(false,true);check(!m.loopReady()&&m.loopVolume(true)==0,"sound stops immediately");
  check(m.amount(1)>0,"visual coast-down");
  check(Math.abs(m.rock(true,.5f,123))<=.13001f,"worn rocking bound");
  check(Math.abs(m.rock(false,.5f,123))<=.05501f,"modern rocking bound");
  for(int i=0;i<20;i++)m.tick(false,true);
  check(m.amount(1)==0&&m.indicator(true,1,0)==1,"stop settles");
  check(m.tick(true,true)&&!m.loopReady(),"restart waits again");
  var loaded=new GeneratorMotion();check(!loaded.tick(true,true)&&loaded.loopReady(),"loaded running engine does not replay ignition");
  var modern=new GeneratorMotion();modern.tick(false,false);
  check(modern.tick(true,false)&&!modern.loopReady(),"modern ignition starts once");
  for(int i=1;i<23;i++){check(!modern.tick(true,false),"no repeated modern ignition");check(!modern.loopReady(),"modern ignition handoff waits");}
  modern.tick(true,false);check(modern.loopReady(),"modern 24-tick audio handoff");
  for(int i=0;i<12;i++)modern.tick(true,false);
  check(modern.loopVolume(false)==1,"modern 12-tick loop fade");
  modern.tick(false,false);check(!modern.loopReady()&&modern.loopVolume(false)==0,"modern stop immediate");
  check(modern.tick(true,false)&&!modern.loopReady(),"modern restart ignition");
  check(GeneratorMotion.nearFade(0)==1,"close meshes retain cosmetic motion");
  check(GeneratorMotion.nearFade(18*18)==1,"motion fade begins at 18 blocks");
  check(GeneratorMotion.nearFade(24*24)==0,"motion stops before far LOD");
  check(GeneratorMotion.nearFade(21*21)>0&&GeneratorMotion.nearFade(21*21)<1,"LOD transition is gradual");
  var envelope=new GeneratorMotion();envelope.tick(false,false);
  float previous=0;
  for(int i=0;i<16;i++){
   envelope.tick(true,false);float current=envelope.amount(1);
   check(current>=previous&&current<=1,"startup has monotonic cubic acceleration");previous=current;
  }
  check(envelope.amount(1)==1,"startup reaches full speed after 16 ticks");
  for(int i=0;i<18;i++){
   envelope.tick(false,false);float current=envelope.amount(1);
   check(current<=previous&&current>=0,"coast-down is monotonic");previous=current;
  }
  check(envelope.amount(1)<.001,"engine comes to a full stop");
  var modernLoaded=new GeneratorMotion();check(!modernLoaded.tick(true,false)&&modernLoaded.loopReady(),"loaded modern motor skips ignition");

  var cache=new TransientInstances<String,Object,Sound>();Object owner=new Object();
  Sound a=cache.acquire("world1:pos1",owner,s->s.stopped,Sound::new,Sound::stop);
  for(int i=0;i<200;i++)check(cache.acquire("world1:pos1",owner,s->s.stopped,Sound::new,Sound::stop)==a,"no duplicate per tick");
  Sound b=cache.acquire("world1:pos2",new Object(),s->s.stopped,Sound::new,Sound::stop);
  Sound replacement=cache.acquire("world1:pos1",new Object(),s->s.stopped,Sound::new,Sound::stop);
  check(a.stops==1&&replacement!=a&&cache.size()==2,"replacement at same position stops old owner");
  cache.removeIf((key,s)->key.equals("world1:pos2"),Sound::stop);
  check(b.stops==1&&cache.size()==1,"unload/destroy/disable cleanup predicate");
  cache.acquire("world2:pos1",new Object(),s->s.stopped,Sound::new,Sound::stop);
  cache.removeIf((key,s)->!key.startsWith("world2:"),Sound::stop);
  check(replacement.stops==1&&cache.size()==1,"dimension identity");
  cache.clear(Sound::stop);check(cache.size()==0,"disconnect");
  System.out.println("PASS GeneratorLifecycleTest: old 84/40 and modern 24/12 audio ticks, ownership, replace, cleanup, reconnect");
 }
}
