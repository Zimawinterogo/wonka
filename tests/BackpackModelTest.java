import dev.whiteout.power.backpack.*;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;

public final class BackpackModelTest {
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 static BipedEntityModel<LivingEntity> model(){
  return new BipedEntityModel<>(TexturedModelData.of(BipedEntityModel.getModelData(Dilation.NONE,0),64,32).createModel());
 }
 public static void main(String[] args){
  var untouched=model();float standing=untouched.head.pivotY;
  BackpackPoseModel.apply(untouched,0,0);
  check(untouched.head.pivotY==standing,"neutral pose unaffected");
  var seated=model();BackpackPoseModel.apply(seated,1,1);
  check(seated.head.pivotY-standing==BackpackPosePolicy.DROP*16,"head agrees with camera");
  check(seated.leftLeg.pitch< -1.4f&&seated.rightLeg.pitch< -1.4f,"both legs sit");
  check(seated.leftLeg.yaw<0&&seated.rightLeg.yaw>0,"legs open outward");
  check(seated.leftArm.yaw>0&&seated.rightArm.yaw<0,"hands reach inward to backpack");
  check(seated.leftLeg.pivotY+12*Math.cos(seated.leftLeg.pitch)<=24,"feet above floor");
  check(seated.hat.pivotY==seated.head.pivotY,"hat follows head");
  var armor=model();seated.copyBipedStateTo(armor);
  check(armor.head.pivotY==seated.head.pivotY&&armor.leftLeg.pitch==seated.leftLeg.pitch,"armor follows seated model");
  for(int t=0;t<=350;t+=5){
   var frame=model();float amount=BackpackPosePolicy.blend(0,1,t);BackpackPoseModel.apply(frame,amount,amount);
   check(frame.head.pivotY>=standing&&frame.head.pivotY<=standing+10,"bounded head trajectory");
  }
  System.out.println("PASS BackpackModelTest: real model parts, sitting legs, floor clearance, camera alignment, armor copy");
 }
}
