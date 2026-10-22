package dev.whiteout.power;

public final class WorldStateCacheTest {
 static int checks;
 static void ok(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 public static void main(String[] args){
  WorldStateCache<String,Integer> cache=new WorldStateCache<>(2,2);
  int[] calls={0};
  ok(cache.get("a",10,()->++calls[0])==1,"first read");
  ok(cache.get("a",11,()->++calls[0])==1&&calls[0]==1,"cache hit");
  ok(cache.get("a",12,()->++calls[0])==2,"bounded expiry without event");
  cache.clear();ok(cache.get("a",12,()->++calls[0])==3,"same-tick outage invalidation");
  cache.get("b",12,()->4);cache.get("c",12,()->5);
  ok(cache.size()==2,"bounded capacity");
  ok(cache.get("a",12,()->6)==6,"eviction");
  ok(cache.get("a",0,()->7)==7,"clock reset does not retain stale world");
  cache.clear();ok(cache.size()==0,"unload/stop clears references");
  WorldStateCache<String,Integer> reloaded=new WorldStateCache<>(2,2);
  ok(reloaded.get("a",0,()->8)==8,"new lifecycle has no previous cache");
  try{cache.get("recursive",0,()->cache.get("recursive",0,()->9));throw new AssertionError("recursion accepted");}
  catch(IllegalStateException expected){checks++;}
  ok(cache.get("recursive",0,()->9)==9,"recovery after failure");
  cache.clear();
  cache.get("a",0,()->{cache.clear();return 10;});
  ok(cache.size()==0,"invalidation during read cannot publish stale snapshot");
  try{cache.get("failure",0,()->{throw new IllegalArgumentException();});}catch(IllegalArgumentException expected){checks++;}
  ok(cache.get("failure",0,()->11)==11,"failed load is not cached");
  System.out.println("WorldStateCacheTest PASS: "+checks);
 }
}
