package dev.whiteout.power;

/** Per-owner error gate: first/new failure immediately, unchanged failures every minute. */
public final class RepeatedFailure {
 private String last;
 private long next;
 public boolean report(String message,long now){
  if(!java.util.Objects.equals(last,message)||now-next>=0){last=message;next=now+60_000_000_000L;return true;}
  return false;
 }
 public void clear(){last=null;next=0;}
}
