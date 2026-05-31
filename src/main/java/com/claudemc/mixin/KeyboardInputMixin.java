package com.claudemc.mixin;

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

    /** Restores WASD movement inputs when InventoryMove is enabled and a screen is open. */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void claudemc$inventoryMove(boolean slowDown, float f, CallbackInfo ci) {
        if (InventoryMove.INSTANCE == null || !InventoryMove.INSTANCE.isEnabled()) return;
        var client = MinecraftClient.getInstance();
        if (client.currentScreen == null) return;

        long win = client.getWindow().getHandle();
        var opts = client.options;
        var self = (Input) (Object) this;

        boolean fwd   = isKeyDown(win, opts.forwardKey);
        boolean back  = isKeyDown(win, opts.backKey);
        boolean left  = isKeyDown(win, opts.leftKey);
        boolean right = isKeyDown(win, opts.rightKey);
        boolean jump  = InventoryMove.INSTANCE.allowJump()  && isKeyDown(win, opts.jumpKey);
        boolean sneak = isKeyDown(win, opts.sneakKey);

        self.movementForward  = (fwd  ? 1f : 0f) - (back  ? 1f : 0f);
        self.movementSideways = (right ? 1f : 0f) - (left  ? 1f : 0f);
        self.jumping  = jump;
        self.sneaking = sneak;

        // Normalise diagonal movement
        if (self.movementForward != 0 && self.movementSideways != 0) {
            float scale = 1f / (float) Math.sqrt(2);
            self.movementForward  *= scale;
            self.movementSideways *= scale;
        }
        if (slowDown) {
            self.movementForward  *= 0.3f;
            self.movementSideways *= 0.3f;
        }
    }

    private static boolean isKeyDown(long win, net.minecraft.client.option.KeyBinding key) {
        // Use the key's own isPressed which reads from GLFW state directly
        // We need raw GLFW check: key.getDefaultKey() gives the InputUtil.Key
        var bound = key.getDefaultKey();
        if (bound.getCategory() == net.minecraft.client.util.InputUtil.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, (int) bound.getCode())
                   == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(win, (int) bound.getCode());
    }
}
