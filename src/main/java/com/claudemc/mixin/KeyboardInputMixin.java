package com.claudemc.mixin;

import com.claudemc.module.impl.misc.AutoMine;
import com.claudemc.module.impl.movement.InventoryMove;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    /**
     * Handles two module movement overrides, both injected at TAIL of KeyboardInput.tick()
     * so they run after vanilla key-state is applied and movement values are already set.
     *
     * InventoryMove: restores WASD while a screen is open.
     * AutoMine:      drives the player forward/back while mining (no screen restriction).
     * The two are mutually exclusive — you can't mine while a screen is open.
     */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void claudemc$inputOverride(boolean slowDown, float f, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        var self   = (Input)(Object)this;

        // ── InventoryMove ─────────────────────────────────────────────────
        if (InventoryMove.INSTANCE != null && InventoryMove.INSTANCE.isEnabled()
                && client.currentScreen != null) {
            long win  = client.getWindow().getHandle();
            var  opts = client.options;

            boolean fwd   = isKeyDown(win, opts.forwardKey);
            boolean back  = isKeyDown(win, opts.backKey);
            boolean left  = isKeyDown(win, opts.leftKey);
            boolean right = isKeyDown(win, opts.rightKey);
            boolean jump  = InventoryMove.INSTANCE.allowJump() && isKeyDown(win, opts.jumpKey);
            boolean sneak = isKeyDown(win, opts.sneakKey);

            self.movementForward  = (fwd  ? 1f : 0f) - (back  ? 1f : 0f);
            self.movementSideways = (right ? 1f : 0f) - (left  ? 1f : 0f);
            self.jumping  = jump;
            self.sneaking = sneak;

            if (self.movementForward != 0 && self.movementSideways != 0) {
                float scale = 1f / (float) Math.sqrt(2);
                self.movementForward  *= scale;
                self.movementSideways *= scale;
            }
            if (slowDown) {
                self.movementForward  *= 0.3f;
                self.movementSideways *= 0.3f;
            }
            return; // no screen → AutoMine branch unreachable anyway
        }

        // ── AutoMine ──────────────────────────────────────────────────────
        if (AutoMine.INSTANCE != null && AutoMine.INSTANCE.isEnabled()) {
            if (AutoMine.INSTANCE.wantForward) {
                self.movementForward  =  1.0f;
                self.movementSideways =  0.0f;
            } else if (AutoMine.INSTANCE.wantBack) {
                self.movementForward  = -1.0f;
                self.movementSideways =  0.0f;
            }
        }
    }

    private static boolean isKeyDown(long win, net.minecraft.client.option.KeyBinding key) {
        var bound = key.getDefaultKey();
        if (bound.getCategory() == InputUtil.Type.MOUSE)
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, (int) bound.getCode())
                   == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(win, (int) bound.getCode());
    }
}
