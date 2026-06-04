package com.claudemc.module.impl.combat;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.BoolSetting;
import com.claudemc.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;

/**
 * ArrowDMG — increases arrow/crossbow/trident damage via packet manipulation.
 * Adapted from Wurst's ArrowDmgHack.
 *
 * NOTE: Requires a mixin to hook into the StopUsingItem event (ItemUseMixin).
 * When the player stops using a bow, this module sends fake movement packets
 * to make the server calculate higher arrow velocity/damage.
 *
 * The actual packet-sending logic needs a mixin that calls ArrowDMG.onStopUsingItem().
 * See: mixin/ArrowDMGMixin.java (to be added separately).
 */
public class ArrowDMG extends Module {

    public static ArrowDMG INSTANCE;

    private final NumberSetting strengthSetting;
    private final BoolSetting   yeetTridentsSetting;

    // Tracks whether we need to fire on next use-stop
    private boolean bowWasBeingUsed = false;

    public ArrowDMG() {
        super("ArrowDMG", "Increases arrow damage by manipulating velocity packets", Category.COMBAT);
        INSTANCE = this;
        strengthSetting     = addNumber("Strength",    10.0, 0.1, 10.0, 0.1, false);
        yeetTridentsSetting = addBool("YeetTridents",  false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        boolean isUsingBow = client.player.isUsingItem() && isValidItem(client);
        if (bowWasBeingUsed && !isUsingBow) {
            // Player just released the bow — trigger the damage boost
            onStopUsingItem(client);
        }
        bowWasBeingUsed = isUsingBow;
    }

    /**
     * Called when the player releases/stops using a bow or trident.
     * Sends fake position packets to boost arrow speed server-side.
     *
     * See ServerGamePacketListenerImpl.handleMovePlayer() for the server-side check
     * this exploits.
     */
    public void onStopUsingItem(MinecraftClient client) {
        if (client.player == null) return;
        if (!isValidItem(client)) return;

        var network = client.getNetworkHandler();
        if (network == null) return;

        double strength = strengthSetting.get();
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();

        // Calculate look-vector offset scaled by strength
        double adjustedStrength = strength / 10.0 * Math.sqrt(500);
        var lookVec = client.player.getRotationVec(1.0f).multiply(adjustedStrength);

        // Send fake position packets to make the server think we moved fast
        // This exploits how the server calculates arrow velocity from player movement
        for (int i = 0; i < 4; i++) {
            sendPos(network, x, y, z, true);
        }
        sendPos(network, x - lookVec.x, y, z - lookVec.z, true);
        sendPos(network, x, y, z, false);
    }

    private void sendPos(net.minecraft.client.network.ClientPlayNetworkHandler network,
                         double x, double y, double z, boolean onGround) {
        // NOTE: Packet sending for position requires a mixin or access to the packet class.
        // This is a stub — the actual implementation needs:
        //   network.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround));
        // Yarn 1.21.11: net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
        // TODO: Add mixin/packet access for full functionality
    }

    private boolean isValidItem(MinecraftClient client) {
        if (client.player == null) return false;
        var item = client.player.getMainHandStack().getItem();
        if (yeetTridentsSetting.get() && item == Items.TRIDENT) return true;
        return item == Items.BOW || item == Items.CROSSBOW;
    }
}
