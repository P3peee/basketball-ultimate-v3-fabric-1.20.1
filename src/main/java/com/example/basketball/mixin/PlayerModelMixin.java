package com.example.basketball.mixin;

import com.example.basketball.pose.PoseState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Позы рук и ног: дриблинг (стойка/бег), кроссовер, бросок, пас, блок, перехват, три вида дэнка, падение. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;

    public PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void basketball$poses(LivingEntity entity, float limbAngle, float limbDistance,
                                  float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) return;

        long now = entity.getWorld().getTime();
        float delta = animationProgress - (float) Math.floor(animationProgress);
        int id = entity.getId();
        boolean changed = false;

        PoseState.Timed timed = PoseState.timed(id);
        if (timed != null) {
            float p = ((now - timed.start()) - 1 + delta) / PoseState.duration(timed.pose());
            if (p >= 0f && p <= 1f) changed = applyTimed(timed.pose(), p);
        }
        if (!changed && PoseState.dribbling(id, now)) {
            dribbleArms(now, delta, id, limbDistance);
            changed = true;
        }
        if (changed) {
            this.leftSleeve.copyTransform(this.leftArm);
            this.rightSleeve.copyTransform(this.rightArm);
            this.leftPants.copyTransform(this.leftLeg);
            this.rightPants.copyTransform(this.rightLeg);
        }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float ease(float k) { k = Math.max(0f, Math.min(1f, k)); return k * k * (3f - 2f * k); }

    // ---------- дриблинг ----------
    private void dribbleArms(long now, float delta, int id, float limbDistance) {
        float mv = Math.min(1f, limbDistance * 1.6f);              // 0 - стоит, 1 - бежит
        double tt = (now - 1) + delta;
        float b = PoseState.bounce(tt + 1.0);                        // рука опережает мяч на тик
        float wR = Math.max(0f, Math.min(1f, (PoseState.dribbleSide(id) + 1f) / 2f));
        float wL = 1f - wR;

        float push = -0.30f - 0.55f * b - 0.25f * mv;               // ведущая рука
        float guard = -0.95f - 0.25f * mv;                          // страхующая рука

        this.rightArm.pitch = lerp(guard, push, wR);
        this.rightArm.yaw = lerp(-0.4f, -0.15f, wR);
        this.leftArm.pitch = lerp(guard, push, wL);
        this.leftArm.yaw = lerp(0.4f, 0.15f, wL);

        // защитная стойка: ноги шире, пока игрок стоит
        float stance = (1f - mv) * 0.12f;
        this.rightLeg.roll = stance;
        this.leftLeg.roll = -stance;
    }

    // ---------- позы по времени ----------
    /** true - поза управляет руками; false - руки остаются как при дриблинге. */
    private boolean applyTimed(int pose, float p) {
        switch (pose) {
            case PoseState.SHOOT -> {
                if (p < 0.45f) {
                    float k = p / 0.45f;
                    this.rightArm.pitch = lerp(-1.4f, -3.0f, k);
                    this.leftArm.pitch = lerp(-1.4f, -2.4f, k);
                } else {
                    float k = (p - 0.45f) / 0.55f;
                    this.rightArm.pitch = lerp(-3.0f, -2.2f, k);
                    this.leftArm.pitch = lerp(-2.4f, -0.3f, k);
                }
                this.rightArm.yaw = -0.1f;
                this.leftArm.yaw = 0.3f;
                return true;
            }
            case PoseState.PASS -> {
                float s = (float) Math.sin(p * Math.PI);
                this.rightArm.pitch = -1.0f - 0.55f * s;
                this.leftArm.pitch = -1.0f - 0.55f * s;
                this.rightArm.yaw = -0.15f;
                this.leftArm.yaw = 0.15f;
                return true;
            }
            case PoseState.BLOCK -> {
                this.rightArm.pitch = -3.1f;
                this.leftArm.pitch = -3.1f;
                this.rightArm.roll = 0.12f;
                this.leftArm.roll = -0.12f;
                return true;
            }
            case PoseState.STEAL -> {
                this.rightArm.pitch = -1.4f - 0.4f * (float) Math.sin(p * Math.PI);
                this.rightArm.yaw = lerp(-0.9f, 0.9f, p);
                this.leftArm.pitch = -0.5f;
                this.leftArm.yaw = 0.4f;
                return true;
            }
            case PoseState.DUNK -> { twoHandDunk(p); return true; }
            case PoseState.DUNK_TOMA -> { tomahawkDunk(p); return true; }
            case PoseState.DUNK_WIND -> { windmillDunk(p); return true; }
            case PoseState.CATCH -> {
                this.rightArm.pitch = -1.3f + 0.3f * p;
                this.leftArm.pitch = -1.3f + 0.3f * p;
                this.rightArm.yaw = -0.2f;
                this.leftArm.yaw = 0.2f;
                return true;
            }
            case PoseState.SPIN -> {
                this.rightArm.pitch = -1.1f;
                this.leftArm.pitch = -1.1f;
                this.rightArm.yaw = -0.5f;
                this.leftArm.yaw = 0.5f;
                return true;
            }
            case PoseState.STUMBLE -> {
                float flail = (float) Math.sin(p * 14.0);
                this.rightArm.pitch = -2.2f + 0.5f * flail;
                this.leftArm.pitch = -2.2f - 0.5f * flail;
                this.rightArm.roll = 0.6f;
                this.leftArm.roll = -0.6f;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ---------- дэнки: замах (0-0.30), удар (0.30-0.42), висим на кольце (до 0.75), отпускаем ----------
    private void legTuck(float p, float right, float left) {
        float k = p < 0.30f ? ease(p / 0.30f) : p < 0.75f ? 1f : 1f - ease((p - 0.75f) / 0.25f);
        this.rightLeg.pitch = right * k;
        this.leftLeg.pitch = left * k;
    }

    /** Двумя руками: мяч над головой, мощный вдавливающий удар, висим на ободе. */
    private void twoHandDunk(float p) {
        float r;
        if (p < 0.30f) r = lerp(-1.0f, -3.2f, ease(p / 0.30f));
        else if (p < 0.42f) r = lerp(-3.2f, -2.7f, (p - 0.30f) / 0.12f);
        else if (p < 0.75f) r = -2.75f + 0.06f * (float) Math.sin((p - 0.42f) * 30.0);
        else r = lerp(-2.75f, -0.4f, ease((p - 0.75f) / 0.25f));
        this.rightArm.pitch = r;
        this.leftArm.pitch = r;
        this.rightArm.yaw = -0.1f;
        this.leftArm.yaw = 0.1f;
        legTuck(p, -0.9f, -0.5f);
    }

    /** Томагавк: рука с мячом уходит за голову и резко бьёт вниз, вторая рука балансирует. */
    private void tomahawkDunk(float p) {
        float r, l;
        if (p < 0.30f) {
            float k = ease(p / 0.30f);
            r = lerp(-1.0f, -3.7f, k);
            l = lerp(-1.0f, -1.8f, k);
        } else if (p < 0.40f) {
            float k = (p - 0.30f) / 0.10f;
            r = lerp(-3.7f, -2.5f, k * k);
            l = -1.8f;
        } else if (p < 0.75f) {
            r = -2.8f + 0.05f * (float) Math.sin((p - 0.40f) * 28.0);
            l = -1.8f;
        } else {
            float k = ease((p - 0.75f) / 0.25f);
            r = lerp(-2.8f, -0.4f, k);
            l = lerp(-1.8f, -0.4f, k);
        }
        this.rightArm.pitch = r;
        this.leftArm.pitch = l;
        this.rightArm.yaw = -0.1f;
        this.leftArm.roll = p < 0.75f ? -0.5f : 0f;
        legTuck(p, -0.8f, 0.4f);
    }

    /** Виндмилл: полный оборот руки с мячом перед ударом. */
    private void windmillDunk(float p) {
        float r;
        if (p < 0.38f) r = lerp(-0.5f, -9.18f, ease(p / 0.38f));    // 1.4 оборота, в конце рука над головой
        else if (p < 0.75f) r = -2.9f + 0.05f * (float) Math.sin((p - 0.38f) * 28.0);
        else r = lerp(-2.9f, -0.4f, ease((p - 0.75f) / 0.25f));
        this.rightArm.pitch = r;
        this.leftArm.pitch = p < 0.75f ? -1.3f : lerp(-1.3f, -0.4f, ease((p - 0.75f) / 0.25f));
        this.leftArm.roll = p < 0.75f ? -0.7f : 0f;
        legTuck(p, -0.7f, 0.6f);
    }
}
