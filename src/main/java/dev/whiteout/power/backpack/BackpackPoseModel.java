package dev.whiteout.power.backpack;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;

public final class BackpackPoseModel {
 private BackpackPoseModel(){}
 public static void apply(BipedEntityModel<?> m,float sit,float reach){
  if(sit<=0)return;
  for(ModelPart part:new ModelPart[]{m.head,m.body,m.leftArm,m.rightArm,m.leftLeg,m.rightLeg})part.pivotY+=10*sit;
  m.body.pitch=m.body.pitch*(1-sit)+.12f*sit;
  m.leftLeg.pitch=m.leftLeg.pitch*(1-sit)-1.45f*sit;
  m.rightLeg.pitch=m.rightLeg.pitch*(1-sit)-1.45f*sit;
  m.leftLeg.yaw=-.16f*sit;m.rightLeg.yaw=.16f*sit;
  m.leftArm.pitch=m.leftArm.pitch*(1-sit)-(.65f+.45f*reach)*sit;
  m.rightArm.pitch=m.rightArm.pitch*(1-sit)-(.65f+.45f*reach)*sit;
  m.leftArm.yaw=.2f*sit;m.rightArm.yaw=-.2f*sit;
  m.hat.copyTransform(m.head);
 }
}
