package com.example.basketball.mixin;

import com.example.basketball.pose.PoseState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Движение всего тела: наклон и приседание при дриблинге (зависят от скорости), прокрут, дэнки, падение. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerTransformsMixin {
    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",
            at = @At("TAIL"))
    private void basketball$transforms(AbstractClientPlayerEntity player, MatrixStack m, float animationProgress,
                                       float bodyYaw, float tickDelta, CallbackInfo ci) {
        long now = player.getWorld().getTime();
        int id = player.getId();
        float lean = 0f;   // + наклон вперёд
        float roll = 0f;
        float spin = 0f;
        float shift = 0f;

        PoseState.Timed t = PoseState.timed(id);
        boolean dunking = false;
        if (t != null) {
            float p = ((now - t.start()) - 1 + tickDelta) / PoseState.duration(t.pose());
            if (p >= 0f && p <= 1f) {
                double s = Math.sin(Math.PI * p);
                if (PoseState.isDunk(t.pose())) {
                    dunking = true;
                    if (p < 0.30f) lean = 14f * ease(p / 0.30f);
                    else if (p < 0.42f) lean = lerp(14f, -4f, (p - 0.30f) / 0.12f);
                    else if (p < 0.75f) lean = -4f;
                    else lean = lerp(-4f, 0f, ease((p - 0.75f) / 0.25f));
                    if (t.pose() == PoseState.DUNK_WIND && p < 0.38f) roll = (float) (6.0 * Math.sin(Math.PI * p / 0.38));
                } else {
                    switch (t.pose()) {
                        case PoseState.SPIN -> spin = 360f * ease(p);
                        case PoseState.STEPBACK -> lean = (float) (-14.0 * s);
                        case PoseState.CROSS_R -> roll = (float) (10.0 * s);
                        case PoseState.CROSS_L -> roll = (float) (-10.0 * s);
                        case PoseState.SHOOT -> lean = (float) (-5.0 * s);
                        case PoseState.STUMBLE -> {
                            lean = (float) (-75.0 * s);
                            shift = (float) (-0.25 * s);
                        }
                        default -> { }
                    }
                }
            }
        }

        if (!dunking && PoseState.dribbling(id, now)) {
            double tt = (now - 1) + tickDelta;
            float b = PoseState.bounce(tt);
            double sp = Math.min(0.4, Math.hypot(player.getX() - player.prevX, player.getZ() - player.prevZ));
            float run = (float) (sp / 0.4);                       // 0 - стоит, 1 - быстро бежит
            lean += 4f + 12f * run;                               // на бегу корпус сильнее вперёд
            shift += -(0.05f + 0.04f * run) * (1f - b);           // приседание в момент толчка
            roll += 2.5f * PoseState.dribbleSide(id) * (float) Math.sin(Math.PI * tt / 5.0) * (1f - 0.6f * run); // покачивание плеч
        }

        if (shift != 0f) m.translate(0.0, shift, 0.0);
        if (spin != 0f) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        if (lean != 0f) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-lean));
        if (roll != 0f) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float ease(float k) { k = Math.max(0f, Math.min(1f, k)); return k * k * (3f - 2f * k); }
}
