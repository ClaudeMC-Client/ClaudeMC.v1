package com.claudemc.mixin;

import com.claudemc.module.impl.misc.AutoMine;
import com.claudemc.module.impl.movement.AutoWalk;
import com.claudemc.module.impl.movement.InventoryMove;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    /**
     * Two movement overrides injected at the TAIL of KeyboardInput.tick() so they run
     * after vanilla key-state has been applied to {@link Input#playerInput}.
     *
     * InventoryMove: restores WASD while a screen is open.
     * AutoMine:      drives the player forward/back while mining (no screen restriction).
     *
     * 1.21.x replaced the mutable movementForward/movementSideways/jumping/sneaking fields
     * with an immutable {@link PlayerInput} record, and tick() no longer takes parameters.
     */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void claudemc$inputOverride(CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        var self   = (Input) (Object) this;

        // ── InventoryMove ─────────────────────────────────────────────────
        if (InventoryMove.INSTANCE != null && InventoryMove.INSTANCE.isEnabled()
                && client.currentScreen != null) {
            var opts = client.options;

            boolean fwd   = isKeyDown(client, opts.forwardKey);
            boolean back  = isKeyDown(client, opts.backKey);
            boolean left  = isKeyDown(client, opts.leftKey);
            boolean right = isKeyDown(client, opts.rightKey);
            boolean jump  = InventoryMove.INSTANCE.allowJump() && isKeyDown(client, opts.jumpKey);
            boolean sneak = isKeyDown(client, opts.sneakKey);

            self.playerInput = new PlayerInput(fwd, back, left, right, jump, sneak,
                                               self.playerInput.sprint());
            return; // no screen → AutoMine branch unreachable anyway
        }

        // ── AutoMine ──────────────────────────────────────────────────────
        if (AutoMine.INSTANCE != null && AutoMine.INSTANCE.isEnabled()) {
            PlayerInput cur = self.playerInput;
            if (AutoMine.INSTANCE.wantForward) {
                self.playerInput = new PlayerInput(true, false, cur.left(), cur.right(),
                                                   cur.jump(), cur.sneak(), cur.sprint());
            } else if (AutoMine.INSTANCE.wantBack) {
                self.playerInput = new PlayerInput(false, true, cur.left(), cur.right(),
                                                   cur.jump(), cur.sneak(), cur.sprint());
            }
        }

        // ── AutoWalk ──────────────────────────────────────────────────────
        if (AutoWalk.INSTANCE != null && AutoWalk.INSTANCE.isEnabled()) {
            PlayerInput cur = self.playerInput;
            self.playerInput = new PlayerInput(true, cur.backward(), cur.left(), cur.right(),
                                               cur.jump(), cur.sneak(), cur.sprint());
        }
    }

    private static boolean isKeyDown(MinecraftClient client, net.minecraft.client.option.KeyBinding key) {
        // Use the user's actual bound key (falls back to default if the accessor is unavailable)
        InputUtil.Key bound;
        try { bound = ((KeyBindingAccessor) (Object) key).claudemc$getBoundKey(); }
        catch (Throwable t) { bound = key.getDefaultKey(); }
        if (bound.getCategory() == InputUtil.Type.MOUSE)
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().getHandle(), bound.getCode())
                   == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(client.getWindow(), bound.getCode());
    }
}
