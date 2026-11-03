package dev.whiteout.power;

import java.util.*;
import java.util.function.Supplier;

/** Connection-local bounded replay protection; contains no world progress or entity references. */
public final class StoryReceipts {
 public enum Outcome { COMMITTED, ALREADY_COMPLETED, CONDITIONS_FALSE, DENIED }
 public record Request(long token,String key){}
 public record Reply(long token,Outcome outcome){}
 private record Cached(String key,Outcome outcome){}
 private final Map<Long,Cached> replies=new LinkedHashMap<>();
 private final StoryRequestPolicy.Budget budget=new StoryRequestPolicy.Budget();
 private long highest;
 public Reply handle(Request request,long tick,Supplier<Outcome> execute){
  if(request.token()<=0||request.key()==null||!request.key().matches("[a-zA-Z0-9_.:/-]{1,128}"))return new Reply(request.token(),Outcome.DENIED);
  Cached old=replies.get(request.token());
  if(old!=null)return new Reply(request.token(),old.key().equals(request.key())?old.outcome():Outcome.DENIED);
  // Evicted tokens never become new requests, including repeatable events.
  if(request.token()<=highest)return new Reply(request.token(),Outcome.DENIED);
  highest=request.token();
  Outcome outcome=Outcome.DENIED;
  if(budget.take(tick))try{outcome=Objects.requireNonNull(execute.get());}catch(RuntimeException failure){outcome=Outcome.DENIED;}
  if(replies.size()==32)replies.remove(replies.keySet().iterator().next());
  replies.put(request.token(),new Cached(request.key(),outcome));
  return new Reply(request.token(),outcome);
 }
 public int retained(){return replies.size();}

 /** One pending client request. Tokens survive reset to reject late replies from a prior world. */
 public static final class Session {
  private long sequence,started,lastSent,answered;
  private Request request;
  private Outcome outcome;
  public boolean busy(){return request!=null;}
  public Request begin(String key,long tick){
   if(busy())return null;
   if(key==null||!key.matches("[a-zA-Z0-9_.:/-]{1,128}")||sequence==Long.MAX_VALUE)throw new IllegalArgumentException("story key/token");
   request=new Request(++sequence,key);started=lastSent=tick;outcome=null;return request;
  }
  public boolean accept(Reply reply,long tick){
   if(request==null||request.token()!=reply.token()||outcome!=null)return false;
   outcome=Objects.requireNonNull(reply.outcome());answered=tick;return true;
  }
  public Request retry(long tick){
   if(request==null||outcome!=null)return null;
   if(tick<started||tick-started>=100){outcome=Outcome.DENIED;answered=tick;return null;}
   if(tick-lastSent<20)return null;lastSent=tick;return request;
  }
  public Outcome outcome(){return outcome;}
  public boolean presentationExpired(long tick){return outcome!=null&&(tick<answered||tick-answered>=100);}
  public void clear(){request=null;outcome=null;}
 }
}
