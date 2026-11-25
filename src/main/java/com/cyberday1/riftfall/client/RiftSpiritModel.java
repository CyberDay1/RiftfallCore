package com.cyberday1.riftfall.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.ArmedModel;
import com.cyberday1.riftfall.skill.RiftSummon;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RiftSpiritModel extends HierarchicalModel<RiftSummon> implements ArmedModel {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart head;

    public RiftSpiritModel(ModelPart p_171045_) {
        super(RenderType::entityTranslucent);
        this.root = p_171045_.getChild("root");
        this.body = this.root.getChild("body");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        this.rightWing = this.body.getChild("right_wing");
        this.leftWing = this.body.getChild("left_wing");
        this.head = this.root.getChild("head");
    }

    public void setupAnim(RiftSummon p_104028_, float p_104029_, float p_104030_, float p_104031_, float p_104032_, float p_104033_) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.head.yRot = p_104032_ * (float) (Math.PI / 180.0);
        this.head.xRot = p_104033_ * (float) (Math.PI / 180.0);
        float f = Mth.cos(p_104031_ * 5.5F * (float) (Math.PI / 180.0)) * 0.1F;
        this.rightArm.zRot = (float) (Math.PI / 5) + f;
        this.leftArm.zRot = -((float) (Math.PI / 5) + f);
        if (p_104028_.attackAnimationState.isStarted() || p_104028_.spellCastAnimationState.isStarted()) {
            this.body.xRot = 0.0F;
            this.setArmsCharging(p_104028_.getMainHandItem(), p_104028_.getOffhandItem(), f);
        } else {
            this.body.xRot = (float) (Math.PI / 20);
        }

        this.leftWing.yRot = 1.0995574F + Mth.cos(p_104031_ * 45.836624F * (float) (Math.PI / 180.0)) * (float) (Math.PI / 180.0) * 16.2F;
        this.rightWing.yRot = -this.leftWing.yRot;
        this.leftWing.xRot = 0.47123888F;
        this.leftWing.zRot = -0.47123888F;
        this.rightWing.xRot = 0.47123888F;
        this.rightWing.zRot = 0.47123888F;
    }

    private void setArmsCharging(ItemStack p_265484_, ItemStack p_265329_, float p_265125_) {
        if (p_265484_.isEmpty() && p_265329_.isEmpty()) {
            this.rightArm.xRot = -1.2217305F;
            this.rightArm.yRot = (float) (Math.PI / 12);
            this.rightArm.zRot = -0.47123888F - p_265125_;
            this.leftArm.xRot = -1.2217305F;
            this.leftArm.yRot = (float) (-Math.PI / 12);
            this.leftArm.zRot = 0.47123888F + p_265125_;
        } else {
            if (!p_265484_.isEmpty()) {
                this.rightArm.xRot = (float) (Math.PI * 7.0 / 6.0);
                this.rightArm.yRot = (float) (Math.PI / 12);
                this.rightArm.zRot = -0.47123888F - p_265125_;
            }

            if (!p_265329_.isEmpty()) {
                this.leftArm.xRot = (float) (Math.PI * 7.0 / 6.0);
                this.leftArm.yRot = (float) (-Math.PI / 12);
                this.leftArm.zRot = 0.47123888F + p_265125_;
            }
        }
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void translateToHand(HumanoidArm p_259770_, PoseStack p_260351_) {
        boolean flag = p_259770_ == HumanoidArm.RIGHT;
        ModelPart modelpart = flag ? this.rightArm : this.leftArm;
        this.root.translateAndRotate(p_260351_);
        this.body.translateAndRotate(p_260351_);
        modelpart.translateAndRotate(p_260351_);
        p_260351_.scale(0.55F, 0.55F, 0.55F);
        this.offsetStackPosition(p_260351_, flag);
    }

    private void offsetStackPosition(PoseStack p_263343_, boolean p_263414_) {
        if (p_263414_) {
            p_263343_.translate(0.046875, -0.15625, 0.078125);
        } else {
            p_263343_.translate(-0.046875, -0.15625, 0.078125);
        }
    }
}
