import dev.whiteout.power.*;
import static dev.whiteout.power.StoryReceipts.*;

public final class StoryReceiptsTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  var ledger=new StoryReceipts();int[] commits={0};
  var request=new Request(1,"repeatable");
  check(ledger.handle(request,0,()->{commits[0]++;return Outcome.COMMITTED;}).outcome()==Outcome.COMMITTED,"server commits");
  for(int i=0;i<100;i++)check(ledger.handle(request,1,()->{throw new AssertionError("retry executed twice");}).outcome()==Outcome.COMMITTED,"same reply");
  check(commits[0]==1,"one execution despite network retries");
  check(ledger.handle(new Request(1,"forged-key"),2,()->{throw new AssertionError();}).outcome()==Outcome.DENIED,"token cannot change key");
  for(int i=2;i<100;i++)ledger.handle(new Request(i,"repeatable"),i*20L,()->Outcome.COMMITTED);
  check(ledger.retained()==32,"bounded cache");
  check(ledger.handle(request,3000,()->{throw new AssertionError();}).outcome()==Outcome.DENIED,"evicted replay never executes");
  var flood=new StoryReceipts();for(int i=1;i<=16;i++)check(flood.handle(new Request(i,"a"),0,()->Outcome.COMMITTED).outcome()==Outcome.COMMITTED,"budget");
  check(flood.handle(new Request(17,"a"),0,()->{throw new AssertionError();}).outcome()==Outcome.DENIED,"flood denied");
  check(flood.handle(new Request(17,"a"),20,()->{throw new AssertionError();}).outcome()==Outcome.DENIED,"denied token never becomes accepted later");
  check(flood.handle(new Request(18,"a"),20,()->Outcome.CONDITIONS_FALSE).outcome()==Outcome.CONDITIONS_FALSE,"condition denial preserved");
  var session=new Session();var first=session.begin("scene",0);
  check(session.busy()&&session.begin("second",0)==null,"only one pending scene");
  check(session.retry(19)==null&&session.retry(20).equals(first),"retry same token");
  check(!session.accept(new Reply(first.token()+1,Outcome.COMMITTED),21),"foreign reply ignored");
  check(session.accept(new Reply(first.token(),Outcome.COMMITTED),22),"ack accepted");
  check(!session.accept(new Reply(first.token(),Outcome.DENIED),23)&&session.outcome()==Outcome.COMMITTED,"duplicate cannot replace result");
  check(session.retry(40)==null&&!session.presentationExpired(121)&&session.presentationExpired(122),"bounded presentation wait");
  session.clear();var next=session.begin("scene",30);
  check(next.token()>first.token()&&!session.accept(new Reply(first.token(),Outcome.COMMITTED),31),"disconnect/reset invalidates stale reply");
  check(session.retry(130)==null&&session.outcome()==Outcome.DENIED,"timeout has no success callback");
  session.clear();session.begin("scene",140);session.retry(1);check(session.outcome()==Outcome.DENIED,"clock rollback fails closed");
  System.out.println("PASS StoryReceiptsTest: "+checks+" request/replay/timeout/reset checks");
 }
}
