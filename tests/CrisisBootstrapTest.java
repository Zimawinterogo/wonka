package dev.whiteout.power;

/** Dedicated-side class loading/registration probe, not a running dedicated server. */
public final class CrisisBootstrapTest {
 public static void main(String[] args){
  CrisisRuntime.register();CrisisRuntime.register();
  System.out.println("CrisisBootstrapTest PASS: Fabric SERVER class loading and repeat registration (no live server)");
 }
}
