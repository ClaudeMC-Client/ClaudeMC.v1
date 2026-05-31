package com.claudemc.module.impl.misc;

import com.claudemc.ai.AIClient;
import com.claudemc.ai.AIConfig;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AutoMine — human-like strip miner.
 *
 * Pattern:
 *   1. Mines a 1×2 corridor straight forward (main tunnel).
 *   2. Every BranchEvery blocks it turns left/right 90° and mines a
 *      BranchLen-block side branch, then returns to the main tunnel.
 *   3. Branches alternate sides (left → right → left → …).
 *   4. While mining it scans the surrounding radius for target ores and
 *      deviates to collect them.  A configurable MissChance skips some
 *      ores entirely, so it looks like a real player.
 *   5. On staff detection (via AntiAFK) the module freezes, lets AntiAFK
 *      do its human-like look-around, then resumes when safe.
 *   6. Optional AI tip every ~32 forward blocks (requires API key).
 */
public class AutoMine extends Module {

    public static AutoMine INSTANCE;

    // ── Phases ────────────────────────────────────────────────────────────

    public enum Phase { IDLE, FORWARD, BRANCH, RETURNING }
    private volatile Phase phase = Phase.IDLE;

    // Spatial tracking
    private float mainYaw;          // cardinal yaw at start
    private float branchYaw;        // current branch direction
    private int   blocksForward;    // blocks advanced along main tunnel
    private int   blocksBranch;     // blocks advanced on current branch
    private int   returnBlocksLeft; // blocks to walk back on return trip
    private int   branchSide = 1;   // +1 = left (yaw−90°),  −1 = right (yaw+90°)

    // Mining target
    private BlockPos mineTarget;
    private boolean  miningOre;

    // Randomisation
    private final Random rng = new Random();
    private int pauseTicksLeft;
    private final Set<BlockPos> seenOres    = new HashSet<>();
    private final Set<BlockPos> skippedOres = new HashSet<>();

    // Staff-pause tracking
    private boolean wasPaused;

    // Movement flags — read by KeyboardInputMixin on the same tick
    public volatile boolean wantForward;
    public volatile boolean wantBack;

    // AI hints
    private final AtomicBoolean aiPending = new AtomicBoolean(false);
    private int aiTimer;

    // Position from the previous tick — used to count block advances
    private BlockPos lastPos;

    // ── Ore catalogue ─────────────────────────────────────────────────────

    private static final Map<String, Set<Block>> ORE_GROUPS = new LinkedHashMap<>();
    static {
        ORE_GROUPS.put("Diamond",       Set.of(Blocks.DIAMOND_ORE,   Blocks.DEEPSLATE_DIAMOND_ORE));
        ORE_GROUPS.put("Iron",          Set.of(Blocks.IRON_ORE,      Blocks.DEEPSLATE_IRON_ORE));
        ORE_GROUPS.put("Gold",          Set.of(Blocks.GOLD_ORE,      Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE));
        ORE_GROUPS.put("Emerald",       Set.of(Blocks.EMERALD_ORE,   Blocks.DEEPSLATE_EMERALD_ORE));
        ORE_GROUPS.put("Lapis",         Set.of(Blocks.LAPIS_ORE,     Blocks.DEEPSLATE_LAPIS_ORE));
        ORE_GROUPS.put("Redstone",      Set.of(Blocks.REDSTONE_ORE,  Blocks.DEEPSLATE_REDSTONE_ORE));
        ORE_GROUPS.put("AncientDebris", Set.of(Blocks.ANCIENT_DEBRIS));
        ORE_GROUPS.put("Coal",          Set.of(Blocks.COAL_ORE,      Blocks.DEEPSLATE_COAL_ORE));
        ORE_GROUPS.put("Copper",        Set.of(Blocks.COPPER_ORE,    Blocks.DEEPSLATE_COPPER_ORE));
    }

    // ── Constructor ───────────────────────────────────────────────────────

    public AutoMine() {
        super("AutoMine",
              "Human-like strip miner: branches, ore targeting with deliberate misses, staff-aware",
              Category.MISC);
        addMode("Ores",          "Diamond+Iron",
                "Diamond+Iron", "Diamond", "Iron", "All Valuable", "Everything");
        addNumber("BranchEvery", 16,  4,  64,  4,  true);
        addNumber("BranchLen",    8,  2,  32,  2,  true);
        addNumber("OreRadius",    3,  1,   5,  1,  true);
        addNumber("MissChance",  15,  0,  60,  5,  true);
        addNumber("PauseChance", 20,  0,  60,  5,  true);
        addNumber("MaxPause",    30,  5, 100,  5,  true);
        addBool("UseAI",         false);
        INSTANCE = this;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        phase = Phase.IDLE;
        blocksForward = blocksBranch = returnBlocksLeft = 0;
        branchSide = 1;
        mineTarget = null; miningOre = false;
        pauseTicksLeft = aiTimer = 0;
        seenOres.clear(); skippedOres.clear();
        wasPaused = wantForward = wantBack = false;
        lastPos = null;
    }

    @Override
    public void onDisable() {
        wantForward = wantBack = false;
    }

    // ── Main tick ─────────────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        wantForward = wantBack = false;

        // Staff detection: if AntiAFK is reacting to a check, freeze entirely
        if (AntiAFK.INSTANCE != null && AntiAFK.INSTANCE.isEnabled()
                && AntiAFK.INSTANCE.isEvading()) {
            if (!wasPaused) {
                wasPaused  = true;
                mineTarget = null;
                client.player.sendMessage(
                    Text.literal("§6[AutoMine] §7Paused — staff check detected"), false);
            }
            lastPos = client.player.getBlockPos();
            return;
        }
        if (wasPaused) {
            wasPaused = false;
            client.player.sendMessage(Text.literal("§a[AutoMine] §7Resuming"), false);
        }

        // Random human-like pause between actions
        if (pauseTicksLeft > 0) {
            pauseTicksLeft--;
            lastPos = client.player.getBlockPos();
            return;
        }

        // Initialise on the first active tick
        if (phase == Phase.IDLE) {
            mainYaw = snapToCardinal(client.player.getYaw());
            client.player.setYaw(mainYaw);
            blocksForward = blocksBranch = 0;
            branchSide    = 1;
            lastPos       = client.player.getBlockPos();
            seenOres.clear(); skippedOres.clear();
            phase = Phase.FORWARD;
            client.player.sendMessage(
                Text.literal("§a[AutoMine] §7Starting — facing "
                    + dirName(yawToDir(mainYaw))), false);
        }

        // Periodic AI tip (roughly every 32 forward blocks, counted in ticks)
        if (Boolean.parseBoolean(getSetting("UseAI")) && AIConfig.INSTANCE.isConfigured()
                && !aiPending.get() && ++aiTimer >= 640) {
            aiTimer = 0;
            requestAIHint(client);
        }

        switch (phase) {
            case FORWARD   -> tickForward(client);
            case BRANCH    -> tickBranch(client);
            case RETURNING -> tickReturn(client);
            default        -> {}
        }

        lastPos = client.player.getBlockPos();
    }

    // ── FORWARD phase ─────────────────────────────────────────────────────

    private void tickForward(MinecraftClient client) {
        ClientPlayerEntity p     = client.player;
        var                world = client.world;
        Direction          dir   = yawToDir(mainYaw);

        // Trigger branch when we've walked BranchEvery blocks
        int interval = parseInt(getSetting("BranchEvery"), 16);
        if (blocksForward > 0 && blocksForward % interval == 0) {
            startBranch(p);
            return;
        }

        // Collect nearby ores (with miss chance)
        BlockPos ore = scanForOre(client, dir);
        if (ore != null && (mineTarget == null || world.getBlockState(mineTarget).isAir())) {
            mineTarget = ore;
            miningOre  = true;
        }
        if (miningOre && mineTarget != null && !world.getBlockState(mineTarget).isAir()) {
            lookAt(p, mineTarget);
            client.interactionManager.attackBlock(mineTarget, faceTo(p.getBlockPos(), mineTarget));
            return;
        }
        miningOre = false;

        // Mine the 2-tall forward path
        BlockPos ahead = p.getBlockPos().offset(dir);
        BlockPos above = ahead.up();
        if (!world.getBlockState(ahead).isAir()
                && world.getBlockState(ahead).getHardness(world, ahead) >= 0) {
            lookAt(p, ahead);
            client.interactionManager.attackBlock(ahead, dir.getOpposite());
            return;
        }
        if (!world.getBlockState(above).isAir()
                && world.getBlockState(above).getHardness(world, above) >= 0) {
            lookAt(p, above);
            client.interactionManager.attackBlock(above, dir.getOpposite());
            return;
        }

        // Path clear — walk forward
        wantForward = true;
        smoothYaw(p, mainYaw);
        if (lastPos != null && movedIn(lastPos, p.getBlockPos(), dir)) {
            blocksForward++;
            maybeRandomPause();
        }
    }

    // ── BRANCH phase ──────────────────────────────────────────────────────

    private void startBranch(ClientPlayerEntity p) {
        // branchSide +1 = left = subtract 90° from yaw; −1 = right = add 90°
        branchYaw    = normalYaw(mainYaw - branchSide * 90f);
        blocksBranch = 0;
        phase        = Phase.BRANCH;
        p.setYaw(branchYaw);
    }

    private void tickBranch(MinecraftClient client) {
        ClientPlayerEntity p     = client.player;
        var                world = client.world;
        Direction          dir   = yawToDir(branchYaw);

        int branchLen = parseInt(getSetting("BranchLen"), 8);
        if (blocksBranch >= branchLen) {
            returnBlocksLeft = blocksBranch;
            phase            = Phase.RETURNING;
            p.setYaw(normalYaw(branchYaw + 180f));
            return;
        }

        // Mine branch tunnel
        BlockPos ahead = p.getBlockPos().offset(dir);
        BlockPos above = ahead.up();
        if (!world.getBlockState(ahead).isAir()
                && world.getBlockState(ahead).getHardness(world, ahead) >= 0) {
            lookAt(p, ahead);
            client.interactionManager.attackBlock(ahead, dir.getOpposite());
            return;
        }
        if (!world.getBlockState(above).isAir()
                && world.getBlockState(above).getHardness(world, above) >= 0) {
            lookAt(p, above);
            client.interactionManager.attackBlock(above, dir.getOpposite());
            return;
        }

        // Side ores while branching
        BlockPos ore = scanForOre(client, dir);
        if (ore != null && (mineTarget == null || world.getBlockState(mineTarget).isAir())) {
            mineTarget = ore; miningOre = true;
        }
        if (miningOre && mineTarget != null && !world.getBlockState(mineTarget).isAir()) {
            lookAt(p, mineTarget);
            client.interactionManager.attackBlock(mineTarget, faceTo(p.getBlockPos(), mineTarget));
            return;
        }
        miningOre = false;

        wantForward = true;
        smoothYaw(p, branchYaw);
        if (lastPos != null && movedIn(lastPos, p.getBlockPos(), dir)) {
            blocksBranch++;
            maybeRandomPause();
        }
    }

    // ── RETURNING phase ───────────────────────────────────────────────────

    private void tickReturn(MinecraftClient client) {
        ClientPlayerEntity p       = client.player;
        var                world   = client.world;
        float              retYaw  = normalYaw(branchYaw + 180f);
        Direction          dir     = yawToDir(retYaw);

        if (returnBlocksLeft <= 0) {
            branchSide = -branchSide;   // alternate left/right
            phase      = Phase.FORWARD;
            p.setYaw(mainYaw);
            return;
        }

        // Path was already mined going out; mine anything that regenerated (rare)
        BlockPos ahead = p.getBlockPos().offset(dir);
        if (!world.getBlockState(ahead).isAir()
                && !isTargetOre(world.getBlockState(ahead).getBlock())
                && world.getBlockState(ahead).getHardness(world, ahead) >= 0) {
            lookAt(p, ahead);
            client.interactionManager.attackBlock(ahead, dir.getOpposite());
            return;
        }

        wantForward = true;
        smoothYaw(p, retYaw);
        if (lastPos != null && movedIn(lastPos, p.getBlockPos(), dir)) {
            returnBlocksLeft--;
            maybeRandomPause();
        }
    }

    // ── AI hint ───────────────────────────────────────────────────────────

    private void requestAIHint(MinecraftClient client) {
        aiPending.set(true);
        int y = client.player.getBlockY();
        String prompt = "Strip mining in Minecraft at Y=" + y + ", " + blocksForward
            + " blocks deep, targeting: " + getSetting("Ores")
            + ". One sentence tip to maximise ore yield.";
        String savedSys = AIConfig.INSTANCE.systemPrompt;
        AIConfig.INSTANCE.systemPrompt = "You are a Minecraft mining advisor. One sentence, no markdown.";
        AIClient.INSTANCE.ask(prompt,
            resp -> {
                AIConfig.INSTANCE.systemPrompt = savedSys;
                aiPending.set(false);
                var mc = MinecraftClient.getInstance();
                if (mc.player != null)
                    mc.player.sendMessage(Text.literal("§b[AutoMine AI] §7" + resp), false);
            },
            err -> { AIConfig.INSTANCE.systemPrompt = savedSys; aiPending.set(false); }
        );
    }

    // ── Ore scanning ─────────────────────────────────────────────────────

    private BlockPos scanForOre(MinecraftClient client, Direction mainDir) {
        int   radius     = parseInt(getSetting("OreRadius"), 3);
        float missChance = parseFloat(getSetting("MissChance"), 15f) / 100f;
        BlockPos p = client.player.getBlockPos();
        List<BlockPos> candidates = new ArrayList<>();

        for (int x = p.getX() - radius; x <= p.getX() + radius; x++) {
            for (int y = p.getY() - radius; y <= p.getY() + radius; y++) {
                for (int z = p.getZ() - radius; z <= p.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (skippedOres.contains(pos)) continue;
                    Block block = client.world.getBlockState(pos).getBlock();
                    if (!isTargetOre(block)) continue;
                    if (p.getSquaredDistance(pos) > 16.0) continue; // stay within reach

                    // Roll miss chance exactly once per ore
                    if (!seenOres.contains(pos)) {
                        seenOres.add(pos);
                        if (rng.nextFloat() < missChance) {
                            skippedOres.add(pos);
                            continue;
                        }
                    }
                    candidates.add(pos);
                }
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(p::getSquaredDistance));
        return candidates.get(0);
    }

    private boolean isTargetOre(Block block) {
        String mode = getSetting("Ores");
        Set<Block> d = ORE_GROUPS.get("Diamond"), i = ORE_GROUPS.get("Iron"),
                   g = ORE_GROUPS.get("Gold"),    e = ORE_GROUPS.get("Emerald"),
                   a = ORE_GROUPS.get("AncientDebris");
        return switch (mode) {
            case "Diamond"        -> d.contains(block);
            case "Iron"           -> i.contains(block);
            case "Diamond+Iron"   -> d.contains(block) || i.contains(block);
            case "All Valuable"   -> d.contains(block) || i.contains(block) || g.contains(block)
                                     || e.contains(block) || a.contains(block);
            default /* Everything */ -> ORE_GROUPS.values().stream().anyMatch(s -> s.contains(block));
        };
    }

    // ── Geometry / utility ────────────────────────────────────────────────

    private void lookAt(ClientPlayerEntity p, BlockPos target) {
        Vec3d eye  = p.getEyePos();
        Vec3d diff = Vec3d.ofCenter(target).subtract(eye);
        double h   = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        p.setYaw((float) Math.toDegrees(Math.atan2(-diff.x, diff.z)));
        p.setPitch((float) Math.max(-89.9, Math.min(89.9, Math.toDegrees(-Math.atan2(diff.y, h)))));
    }

    private Direction faceTo(BlockPos from, BlockPos target) {
        // Direction of the face on 'target' that is closest to 'from'
        BlockPos d  = from.subtract(target);
        int ax = Math.abs(d.getX()), ay = Math.abs(d.getY()), az = Math.abs(d.getZ());
        if (ax >= ay && ax >= az) return d.getX() > 0 ? Direction.EAST  : Direction.WEST;
        if (ay >= ax && ay >= az) return d.getY() > 0 ? Direction.UP    : Direction.DOWN;
        return d.getZ() > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private Direction yawToDir(float yaw) {
        float y = normalYaw(yaw);
        if (y < 45 || y >= 315) return Direction.SOUTH;
        if (y < 135)             return Direction.WEST;
        if (y < 225)             return Direction.NORTH;
        return Direction.EAST;
    }

    private float snapToCardinal(float yaw) {
        float y = normalYaw(yaw);
        if (y < 45 || y >= 315) return 0f;
        if (y < 135) return 90f;
        if (y < 225) return 180f;
        return 270f;
    }

    private float normalYaw(float yaw) { return ((yaw % 360) + 360) % 360; }

    private boolean movedIn(BlockPos from, BlockPos to, Direction dir) {
        if (from.equals(to)) return false;
        return switch (dir) {
            case NORTH -> to.getZ() < from.getZ();
            case SOUTH -> to.getZ() > from.getZ();
            case EAST  -> to.getX() > from.getX();
            case WEST  -> to.getX() < from.getX();
            default    -> false;
        };
    }

    private void smoothYaw(ClientPlayerEntity p, float target) {
        float cur  = p.getYaw();
        float diff = target - cur;
        while (diff > 180)  diff -= 360;
        while (diff < -180) diff += 360;
        p.setYaw(cur + (Math.abs(diff) > 15 ? Math.signum(diff) * 8 : diff));
    }

    private String dirName(Direction d) {
        return switch (d) {
            case NORTH -> "North (−Z)"; case SOUTH -> "South (+Z)";
            case EAST  -> "East (+X)";  case WEST  -> "West (−X)";
            default    -> d.getName();
        };
    }

    private void maybeRandomPause() {
        if (rng.nextInt(100) < parseInt(getSetting("PauseChance"), 20))
            pauseTicksLeft = rng.nextInt(parseInt(getSetting("MaxPause"), 30)) + 1;
    }

    private int   parseInt(String s, int def)     { try { return Integer.parseInt(s.trim());  } catch (Exception e) { return def; } }
    private float parseFloat(String s, float def) { try { return Float.parseFloat(s.trim());  } catch (Exception e) { return def; } }
}
