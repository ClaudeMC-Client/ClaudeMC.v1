package com.claudemc.module.impl.render;

import com.claudemc.module.Category;
import com.claudemc.module.Module;
import com.claudemc.module.setting.BoolSetting;
import com.claudemc.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Predicts and draws the flight path of shootable / throwable items (bows, crossbows,
 * tridents, snowballs, eggs, ender pearls, potions, experience bottles).
 *
 * The arc is integrated with the same constant-gravity / drag model the vanilla projectile
 * entities use, and is clipped against world geometry via a per-segment ray-cast so the line
 * terminates where the projectile would actually land, with an impact marker.
 *
 *  - "Self"   draws the path for the item you are holding / drawing.
 *  - "Others" draws the path for other entities that are actively aiming a bow/trident or
 *             holding a loaded crossbow (incoming-projectile warning), in red.
 */
public class Trajectories extends Module {

    public static Trajectories INSTANCE;

    private static final int    MAX_STEPS    = 240;
    private static final double MAX_DISTANCE = 256.0;
    private static final double OTHERS_RANGE = 64.0;

    private final BoolSetting self;
    private final BoolSetting others;

    /** Physics parameters for one projectile type. */
    private record Spec(double speed, double gravity, double drag, double pitchOffset) {}

    public Trajectories() {
        super("Trajectories", "Predicts arrow / throwable flight paths", Category.RENDER);
        self   = addBool("Self",   true);
        others = addBool("Others", true);
        INSTANCE = this;

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (!isEnabled()) return;
            var client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            var cam       = ctx.camera().getPos();
            var matrices  = ctx.matrixStack();
            if (matrices == null) return;
            var consumers = ctx.consumers();
            if (consumers == null) return;

            if (self.get()) {
                drawFor(client, client.player, cam, matrices, consumers,
                        0.4f, 0.9f, 1.0f);   // cyan
            }
            if (others.get()) {
                for (Entity e : client.world.getEntities()) {
                    if (e == client.player || !(e instanceof LivingEntity le)) continue;
                    if (!le.isAlive() || le.distanceTo(client.player) > OTHERS_RANGE) continue;
                    drawFor(client, le, cam, matrices, consumers,
                            1.0f, 0.45f, 0.2f);   // orange-red
                }
            }
        });
    }

    private void drawFor(MinecraftClient client, LivingEntity e, Vec3d cam,
                         net.minecraft.client.util.math.MatrixStack matrices,
                         net.minecraft.client.render.VertexConsumerProvider consumers,
                         float r, float g, float b) {
        boolean isSelf = e == client.player;
        Spec spec = specFor(e, isSelf);
        if (spec == null) return;

        List<Vec3d> world = simulate(client.world, e,
            e.getEyePos(), e.getYaw(), e.getPitch() + spec.pitchOffset(), spec);
        if (world.size() < 2) return;

        // Convert to camera-relative space for rendering.
        List<Vec3d> rel = new ArrayList<>(world.size());
        for (Vec3d p : world) rel.add(p.subtract(cam));
        RenderUtils.drawLineStrip(matrices, consumers, rel, r, g, b, 0.9f);

        // Impact marker box.
        Vec3d hit = world.get(world.size() - 1).subtract(cam);
        Box box = new Box(hit.x - 0.15, hit.y - 0.15, hit.z - 0.15,
                          hit.x + 0.15, hit.y + 0.15, hit.z + 0.15);
        RenderUtils.drawOutlinedBox(matrices, consumers, box, r, g, b, 1f);
    }

    /** Determine the projectile physics for what {@code e} is holding/aiming, or null. */
    private Spec specFor(LivingEntity e, boolean self) {
        // Actively drawing a bow or trident
        if (e.isUsingItem()) {
            Item active = e.getActiveItem().getItem();
            if (active == Items.BOW) {
                double pull = bowPull(e);
                if (pull < 0.1) return null;
                return arrow(pull * 3.0);
            }
            if (active == Items.TRIDENT) return arrow(2.5);
        }
        // Held items (main hand, then off hand)
        for (ItemStack s : new ItemStack[]{ e.getMainHandStack(), e.getOffHandStack() }) {
            Item it = s.getItem();
            if (it == Items.CROSSBOW && crossbowLoaded(s)) return arrow(3.15);
            if (!self) continue;   // for other entities, only show actively-aimed projectiles
            if (it == Items.TRIDENT) return arrow(2.5);
            if (it == Items.SNOWBALL || it == Items.EGG || it == Items.ENDER_PEARL) return thrown(1.5);
            if (it == Items.SPLASH_POTION || it == Items.LINGERING_POTION)          return potion(0.5);
            if (it == Items.EXPERIENCE_BOTTLE)                                      return potion(0.7);
        }
        return null;
    }

    // Vanilla-matching physics constants.
    private static Spec arrow(double speed)  { return new Spec(speed, 0.05, 0.99,   0.0); }
    private static Spec thrown(double speed) { return new Spec(speed, 0.03, 0.99,   0.0); }
    private static Spec potion(double speed) { return new Spec(speed, 0.03, 0.99, -20.0); }

    private static double bowPull(LivingEntity e) {
        int used = e.getItemUseTime();          // ticks the bow has been drawn
        float f = used / 20.0f;
        f = (f * f + f * 2.0f) / 3.0f;
        return Math.min(1.0f, f);
    }

    private static boolean crossbowLoaded(ItemStack stack) {
        var charged = stack.get(net.minecraft.component.DataComponentTypes.CHARGED_PROJECTILES);
        return charged != null && !charged.isEmpty();
    }

    /** Integrate the arc, clipping against blocks. Returns world-space points (start → impact). */
    private List<Vec3d> simulate(World world, Entity shooter, Vec3d start,
                                 double yaw, double pitch, Spec spec) {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        Vec3d dir = new Vec3d(
            -Math.sin(yr) * Math.cos(pr),
            -Math.sin(pr),
             Math.cos(yr) * Math.cos(pr)).normalize();
        Vec3d vel = dir.multiply(spec.speed());
        Vec3d pos = start;

        List<Vec3d> pts = new ArrayList<>();
        pts.add(pos);

        for (int i = 0; i < MAX_STEPS; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult hit = world.raycast(new RaycastContext(
                pos, next, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, shooter));
            if (hit.getType() == HitResult.Type.BLOCK) {
                pts.add(hit.getPos());
                break;
            }
            pos = next;
            pts.add(pos);
            vel = vel.multiply(spec.drag()).subtract(0, spec.gravity(), 0);
            if (pos.distanceTo(start) > MAX_DISTANCE) break;
        }
        return pts;
    }

    @Override public void onTick(MinecraftClient client) {}
}
