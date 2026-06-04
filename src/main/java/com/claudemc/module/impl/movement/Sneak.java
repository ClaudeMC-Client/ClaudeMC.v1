package com.claudemc.module.impl.movement;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Sneak – always sneak / auto-sneak.
 * Forces the sneak input in the PlayerInput record via the KeyboardInputMixin flag.
 *
 * Note: Full override of the sneak key requires a mixin injection.
 * This module sets isSneaking via the player's pose directly each tick.
 */
public class Sneak extends Module {

    public static Sneak INSTANCE;

    public Sneak() {
        super("Sneak", "Always sneak / auto-sneak", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        // Force sneaking pose each tick
        // The sneak input is normally handled by KeyboardInput,
        // but we can force the sneaking state directly.
        // In 1.21.x, sneaking is controlled via playerInput.sneak()
        // We override it here via direct field if accessible, otherwise
        // note: KeyboardInputMixin would need to check Sneak.INSTANCE.
        var input = client.player.input;
        if (input != null) {
            // Force sneak in playerInput record
            var cur = input.playerInput;
            if (!cur.sneak()) {
                input.playerInput = new net.minecraft.util.PlayerInput(
                    cur.forward(), cur.backward(), cur.left(), cur.right(),
                    cur.jump(), true, cur.sprint()
                );
            }
        }
    }
}
