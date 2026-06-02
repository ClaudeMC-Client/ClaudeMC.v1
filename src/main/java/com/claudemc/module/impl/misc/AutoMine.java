package com.claudemc.module.impl.misc;

import com.claudemc.ai.AIClient;
import com.claudemc.ai.AIConfig;
import com.claudemc.module.Category;
import com.claudemc.module.Module;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class AutoMine extends Module {

    public static AutoMine INSTANCE;

    public enum Phase { IDLE, FORWARD, POKE, BRANCH, RETURNING }
    private volatile Phase phase = Phase.IDLE;

    private float mainYaw, branchYaw;
    private int   blocksForward, blocksBranch, returnBlocksLeft;
    private int   branchSide = 1;
    private int   lastBranchAt = 0; // blocksForward value when last branch was started
    private int   lastPokeAt   = 0; // blocksForward value when last poke-hole was dug

    // Poke-hole sub-state: we dig left side then right side at the current position.
    private int     pokeStep = 0;       // 0=left, 1=right, 2=done
    private BlockPos pokeTarget = null;

    private BlockPos mineTarget;
    private boolean  miningOre;
    private boolean  prevMineWasAir = true;

    private final Random rng = new Random();
    private int pauseTicksLeft;

    // ── Safety ──────────────────────────────────────────────────────────────
    private float prevHealth = 20f;
    private int   sealCooldown = 0;

    // ── Ore tracking ──────────────────────────────────────────────────────
    private final Set<BlockPos> seenOres    = new HashSet<>();
    private final Set<BlockPos> skippedOres = new HashSet<>();
    private final Set<BlockPos> ignoredOres = new HashSet<>(); // staff-spawned off-path ores
    private final Set<BlockPos> knownOres   = new HashSet<>(); // ore snapshot for spawn detection
    private boolean oreSnapInit = false;
    private int     oreSnapTimer = 0;

    // ── Vein surprise ─────────────────────────────────────────────────────
    private int consecutiveOres = 0;
    private int surpriseTicks   = 0;

    // ── Staff detection ───────────────────────────────────────────────────
    private boolean wasPaused        = false;
    private int     staffBreakLeft   = 0;
    private int     elevatedMissTicks = 0;
    private float   breakYaw, breakPitch; // locked look during break

    // ── Movement flags read by KeyboardInputMixin ─────────────────────────
    public volatile boolean wantForward, wantBack;

    private final AtomicBoolean aiPending = new AtomicBoolean(false);
    private int aiTimer;
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

    public AutoMine() {
        super("AutoMine", "Human-like strip miner: pokehole/branch layouts with lava, water & fall safety", Category.UTILITY);
        addMode("Ores", "Diamond+Iron", "Diamond+Iron", "Diamond", "Iron", "All Valuable", "Everything");
        // Layout: how the side strips are dug.
        //  Pokehole = 2x1 main tunnel, dig 1x1 holes left+right every "PokeEvery" blocks (xisuma style)
        //  Branch   = long perpendicular branches every "BranchEvery" blocks (antennae style)
        //  Tunnel   = straight 2x1 only, no side work
        addMode("Layout", "Pokehole", "Pokehole", "Branch", "Tunnel");
        addNumber("PokeEvery",    4,  2,  12,  1, true);  // blocks between poke-holes
        addNumber("PokeDepth",    2,  1,   4,  1, true);  // how deep each poke-hole goes
        addNumber("BranchEvery", 11,  4,  64,  1, true);  // blocks between full branches (~6 gap = good)
        addNumber("BranchLen",   32,  4,  64,  2, true);
        addNumber("OreRadius",    3,  1,   5,  1, true);
        addNumber("MissChance",  15,  0,  60,  5, true);
        addNumber("PauseChance", 20,  0,  60,  5, true);
        addNumber("MaxPause",    30,  5, 100,  5, true);
        addBool("Safety",  true);   // avoid falls, lava and water
        addBool("Bridge",  true);   // bridge >2 block drops by placing blocks
        addBool("UseAI", false);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        phase = Phase.IDLE;
        blocksForward = blocksBranch = returnBlocksLeft = 0;
        branchSide = 1; lastBranchAt = 0; lastPokeAt = 0;
        pokeStep = 0; pokeTarget = null;
        mineTarget = null; miningOre = false; prevMineWasAir = true;
        pauseTicksLeft = aiTimer = oreSnapTimer = 0;
        consecutiveOres = surpriseTicks = staffBreakLeft = elevatedMissTicks = 0;
        seenOres.clear(); skippedOres.clear(); ignoredOres.clear();
        knownOres.clear(); oreSnapInit = false;
        wasPaused = wantForward = wantBack = false;
        lastPos = null; sealCooldown = 0;
        var c = MinecraftClient.getInstance();
        if (c.player != null) prevHealth = c.player.getHealth();
    }

    @Override
    public void onDisable() { wantForward = wantBack = false; }

    // ── Main tick ─────────────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        wantForward = wantBack = false;
        if (sealCooldown > 0) sealCooldown--;

        // ── Damage protection: if we just took damage and are in/near a fluid,
        //    seal the flow with blocks before doing anything else. ────────────
        if (Boolean.parseBoolean(getSetting("Safety"))) {
            float hp = client.player.getHealth();
            if (hp < prevHealth - 0.01f) {
                if (protectFromFluid(client)) { prevHealth = hp; lastPos = client.player.getBlockPos(); return; }
            }
            prevHealth = hp;
        }

        // ── Staff detection ───────────────────────────────────────────────
        boolean staffActive = AntiAFK.INSTANCE != null && AntiAFK.INSTANCE.isEnabled()
                              && AntiAFK.INSTANCE.isEvading();
        if (staffActive && !wasPaused) {
            wasPaused         = true;
            staffBreakLeft    = 20 + rng.nextInt(1180); // 1–60 s random break
            elevatedMissTicks = 12000;                  // 10 min elevated miss
            mineTarget        = null;
            seenOres.clear();
            breakYaw   = client.player.getYaw();
            breakPitch = client.player.getPitch();
        }
        if (!staffActive && wasPaused) wasPaused = false;

        if (staffBreakLeft > 0) {
            staffBreakLeft--;
            client.player.setYaw(breakYaw);
            client.player.setPitch(breakPitch);
            lastPos = client.player.getBlockPos();
            return;
        }

        if (elevatedMissTicks > 0) elevatedMissTicks--;

        // ── Surprise look after big vein ──────────────────────────────────
        if (surpriseTicks > 0) {
            surpriseTicks--;
            if (surpriseTicks % 6 == 0) {
                client.player.setYaw(client.player.getYaw() + (rng.nextFloat() - 0.5f) * 40f);
                client.player.setPitch(Math.max(-30f, Math.min(30f,
                    client.player.getPitch() + (rng.nextFloat() - 0.5f) * 20f)));
            }
            lastPos = client.player.getBlockPos();
            return;
        }

        // ── Random pause ──────────────────────────────────────────────────
        if (pauseTicksLeft > 0) { pauseTicksLeft--; lastPos = client.player.getBlockPos(); return; }

        // ── Init ──────────────────────────────────────────────────────────
        if (phase == Phase.IDLE) {
            mainYaw = snapToCardinal(client.player.getYaw());
            client.player.setYaw(mainYaw);
            blocksForward = blocksBranch = 0; branchSide = 1;
            lastBranchAt = 0; lastPokeAt = 0;
            lastPos = client.player.getBlockPos();
            seenOres.clear(); skippedOres.clear(); ignoredOres.clear();
            knownOres.clear(); oreSnapInit = false;
            phase = Phase.FORWARD;
            client.player.sendMessage(
                Text.literal("§a[AutoMine] §7Starting — " + dirName(yawToDir(mainYaw))
                    + " §8(" + getSetting("Layout") + ")"), false);
        }

        checkOreSpawns(client);

        if (Boolean.parseBoolean(getSetting("UseAI")) && AIConfig.INSTANCE.isConfigured()
                && !aiPending.get() && ++aiTimer >= 640) { aiTimer = 0; requestAIHint(client); }

        switch (phase) {
            case FORWARD   -> tickForward(client);
            case POKE      -> tickPoke(client);
            case BRANCH    -> tickBranch(client);
            case RETURNING -> tickReturn(client);
            default        -> {}
        }
        lastPos = client.player.getBlockPos();
    }

    // ── Ore spawn detection ───────────────────────────────────────────────

    private void checkOreSpawns(MinecraftClient client) {
        if (++oreSnapTimer < 20) return;
        oreSnapTimer = 0;
        int r = parseInt(getSetting("OreRadius"), 3) + 3;
        BlockPos p = client.player.getBlockPos();
        Set<BlockPos> current = new HashSet<>();
        for (int x = p.getX()-r; x <= p.getX()+r; x++)
        for (int y = p.getY()-r; y <= p.getY()+r; y++)
        for (int z = p.getZ()-r; z <= p.getZ()+r; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isTargetOre(client.world.getBlockState(pos).getBlock())) current.add(pos);
        }
        if (!oreSnapInit) { oreSnapInit = true; knownOres.addAll(current); return; }

        List<BlockPos> newOres = current.stream()
            .filter(pos -> !knownOres.contains(pos) && !seenOres.contains(pos))
            .collect(Collectors.toList());

        if (newOres.size() >= 3) {
            for (BlockPos pos : newOres) {
                if (isOffPath(client.player, pos)) {
                    ignoredOres.add(pos); seenOres.add(pos); skippedOres.add(pos);
                } else {
                    seenOres.add(pos); skippedOres.add(pos);
                }
            }
        }
        knownOres.clear(); knownOres.addAll(current);
    }

    private boolean isOffPath(ClientPlayerEntity p, BlockPos ore) {
        Direction dir = yawToDir(phase == Phase.BRANCH ? branchYaw : mainYaw);
        BlockPos diff = ore.subtract(p.getBlockPos());
        int perp = switch (dir) {
            case NORTH, SOUTH -> Math.abs(diff.getX());
            case EAST,  WEST  -> Math.abs(diff.getZ());
            default           -> 0;
        };
        return perp > 2;
    }

    // ── FORWARD ───────────────────────────────────────────────────────────

    private void tickForward(MinecraftClient client) {
        var p = client.player; var world = client.world;
        Direction dir = yawToDir(mainYaw);
        String layout = getSetting("Layout");

        // Time for a poke-hole or a full branch?
        if ("Pokehole".equals(layout) && blocksForward > 0
                && (blocksForward - lastPokeAt) >= parseInt(getSetting("PokeEvery"), 4)) {
            lastPokeAt = blocksForward; pokeStep = 0; pokeTarget = null;
            phase = Phase.POKE; return;
        }
        if ("Branch".equals(layout) && blocksForward > 0
                && (blocksForward - lastBranchAt) >= parseInt(getSetting("BranchEvery"), 11)) {
            startBranch(p); return;
        }

        // Seek nearby ore
        if (mineExposedOre(client, dir)) return;

        // Safety: refuse to step into a fall or a fluid; turn around if blocked.
        if (Boolean.parseBoolean(getSetting("Safety")) && !ensureSafeStep(client, dir, mainYaw)) return;

        // Mine the 2-tall forward path
        if (mineColumnAhead(client, dir)) return;

        wantForward = true; smoothYaw(p, mainYaw);
        if (lastPos != null && movedIn(lastPos, p.getBlockPos(), dir)) { blocksForward++; maybeRandomPause(); }
    }

    // ── POKE (1x1 holes left & right at current position) ──────────────────

    private void tickPoke(MinecraftClient client) {
        var p = client.player; var world = client.world;
        Direction fwd = yawToDir(mainYaw);
        int depth = parseInt(getSetting("PokeDepth"), 2);

        // Determine the side direction for this step
        Direction side = (pokeStep == 0)
            ? rotate(fwd, branchSide)        // left
            : rotate(fwd, -branchSide);      // right
        if (pokeStep >= 2) { phase = Phase.FORWARD; return; }

        // Mine `depth` blocks out to the side at foot + head level.
        BlockPos foot = p.getBlockPos();
        for (int d = 1; d <= depth; d++) {
            BlockPos at  = foot.offset(side, d);
            BlockPos top = at.up();
            // Don't breach into lava/water — seal instead and stop this poke.
            if (Boolean.parseBoolean(getSetting("Safety")) && (isFluidAround(world, at) || isFluidAround(world, top))) {
                pokeStep++; return;
            }
            if (mineIfSolid(client, at, side.getOpposite()))  return;
            if (mineIfSolid(client, top, side.getOpposite())) return;
        }
        // This side fully cleared — advance to next step
        pokeStep++;
        if (pokeStep >= 2) phase = Phase.FORWARD;
    }

    // ── BRANCH ────────────────────────────────────────────────────────────

    private void startBranch(ClientPlayerEntity p) {
        lastBranchAt = blocksForward;
        branchYaw = normalYaw(mainYaw - branchSide * 90f);
        blocksBranch = 0; phase = Phase.BRANCH; p.setYaw(branchYaw);
    }

    private void tickBranch(MinecraftClient client) {
        var p = client.player; var world = client.world;
        Direction dir = yawToDir(branchYaw);
        if (blocksBranch >= parseInt(getSetting("BranchLen"), 32)) {
            returnBlocksLeft = blocksBranch; phase = Phase.RETURNING;
            p.setYaw(normalYaw(branchYaw+180f)); return;
        }

        if (mineExposedOre(client, dir)) return;

        if (Boolean.parseBoolean(getSetting("Safety")) && !ensureSafeStep(client, dir, branchYaw)) {
            // Can't continue branch safely — return early.
            returnBlocksLeft = blocksBranch; phase = Phase.RETURNING;
            p.setYaw(normalYaw(branchYaw+180f)); return;
        }

        if (mineColumnAhead(client, dir)) return;

        wantForward = true; smoothYaw(p, branchYaw);
        if (lastPos != null && movedIn(lastPos, p.getBlockPos(), dir)) { blocksBranch++; maybeRandomPause(); }
    }

    // ── RETURNING ─────────────────────────────────────────────────────────

    private void tickReturn(MinecraftClient client) {
        var p = client.player; var world = client.world;
        float retYaw = normalYaw(branchYaw + 180f); Direction dir = yawToDir(retYaw);
        if (returnBlocksLeft <= 0) { branchSide = -branchSide; phase = Phase.FORWARD; p.setYaw(mainYaw); return; }
        BlockPos ahead = p.getBlockPos().offset(dir);
        if (!world.getBlockState(ahead).isAir() && !isTargetOre(world.getBlockState(ahead).getBlock())
                && world.getBlockState(ahead).getHardness(world, ahead) >= 0)
            { lookAt(p, ahead); client.interactionManager.attackBlock(ahead, dir.getOpposite()); return; }
        wantForward = true; smoothYaw(p, retYaw);
        if (lastPos != null && movedIn(lastPos, p.getBlockPos(), dir)) { returnBlocksLeft--; maybeRandomPause(); }
    }

    // ── Mining helpers ──────────────────────────────────────────────────────

    /** Mines the 2-tall column directly ahead. Returns true if it issued a mine action. */
    private boolean mineColumnAhead(MinecraftClient client, Direction dir) {
        var p = client.player; var world = client.world;
        BlockPos ahead = p.getBlockPos().offset(dir), above = ahead.up();
        if (mineIfSolid(client, ahead, dir.getOpposite())) return true;
        if (mineIfSolid(client, above, dir.getOpposite())) return true;
        return false;
    }

    /** If the block is solid (and not a fluid), look at it and start breaking it. */
    private boolean mineIfSolid(MinecraftClient client, BlockPos pos, Direction face) {
        var world = client.world;
        var state = world.getBlockState(pos);
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;  // never "mine" a fluid
        if (state.getHardness(world, pos) < 0) return false;  // unbreakable (bedrock)
        lookAt(client.player, pos);
        client.interactionManager.attackBlock(pos, face);
        return true;
    }

    private boolean mineExposedOre(MinecraftClient client, Direction dir) {
        var world = client.world;
        BlockPos ore = scanForOre(client, dir);
        if (ore != null && (mineTarget == null || world.getBlockState(mineTarget).isAir()))
            { mineTarget = ore; miningOre = true; }
        if (miningOre && mineTarget != null) {
            boolean isAir = world.getBlockState(mineTarget).isAir();
            if (!prevMineWasAir && isAir) onOreMined();
            prevMineWasAir = isAir;
            if (!isAir) {
                lookAt(client.player, mineTarget);
                client.interactionManager.attackBlock(mineTarget, faceTo(client.player.getBlockPos(), mineTarget));
                return true;
            }
            miningOre = false;
        } else { consecutiveOres = 0; }
        return false;
    }

    // ── Safety: falls, lava & water ──────────────────────────────────────────

    /**
     * Ensures it is safe to step forward in `dir`. Returns true if safe to proceed,
     * false if it handled a hazard this tick (caller should return).
     */
    private boolean ensureSafeStep(MinecraftClient client, Direction dir, float yaw) {
        var p = client.player; var world = client.world;
        BlockPos foot   = p.getBlockPos();
        BlockPos ahead  = foot.offset(dir);
        BlockPos above  = ahead.up();
        BlockPos floor  = ahead.down();

        // 1. Fluid directly ahead or above → seal the opening instead of mining into it.
        if (isFluid(world, ahead) || isFluid(world, above)) {
            BlockPos fluidPos = isFluid(world, ahead) ? ahead : above;
            if (!placeAgainstNeighbor(client, fluidPos)) {
                // Can't seal — turn around to avoid swimming into it.
                turnAround(yaw);
            }
            return false;
        }

        // 2. Fall detection: if standing floor ahead is air for >2 blocks down.
        if (world.getBlockState(floor).isAir()
                && world.getBlockState(floor.down()).isAir()
                && world.getBlockState(floor.down(2)).isAir()) {
            // Lava/water at the bottom of the drop? definitely avoid.
            if (Boolean.parseBoolean(getSetting("Bridge")) && placeFloor(client, floor, dir)) {
                return false; // bridged this tick
            }
            // Can't/won't bridge → turn around so we don't walk off the edge.
            turnAround(yaw);
            return false;
        }
        return true;
    }

    private void turnAround(float yaw) {
        if (phase == Phase.FORWARD) {
            // Flip the main heading 90° to start a fresh tunnel away from the hazard.
            mainYaw = normalYaw(yaw + 90f);
            lastBranchAt = blocksForward; lastPokeAt = blocksForward;
        }
    }

    /** Place a block at `floor` (a gap below the step-ahead) to bridge it. */
    private boolean placeFloor(MinecraftClient client, BlockPos floor, Direction dir) {
        // Support: the floor block under the player (behind the gap).
        return placeAgainstNeighbor(client, floor);
    }

    /**
     * When taking damage near a fluid, wall it off: place blocks on every horizontal
     * neighbour of the player that is currently a fluid source/flow.
     */
    private boolean protectFromFluid(MinecraftClient client) {
        if (sealCooldown > 0) return false;
        var p = client.player; var world = client.world;
        BlockPos foot = p.getBlockPos();
        BlockPos[] around = {
            foot, foot.up(), foot.down(),
            foot.north(), foot.south(), foot.east(), foot.west(),
            foot.up().north(), foot.up().south(), foot.up().east(), foot.up().west()
        };
        for (BlockPos pos : around) {
            if (isFluid(world, pos)) {
                if (placeAgainstNeighbor(client, pos)) { sealCooldown = 4; return true; }
            }
        }
        return false;
    }

    // ── Block placement ──────────────────────────────────────────────────────

    /** Finds a solid neighbour of `target` and places a held block against it to fill `target`. */
    private boolean placeAgainstNeighbor(MinecraftClient client, BlockPos target) {
        var p = client.player; var world = client.world;
        if (!world.getBlockState(target).isAir() && world.getBlockState(target).getFluidState().isEmpty()) return false;
        int slot = findBuildingBlock(client);
        if (slot < 0) return false;

        for (Direction d : Direction.values()) {
            BlockPos neighbor = target.offset(d);
            var nState = world.getBlockState(neighbor);
            if (nState.isAir() || !nState.getFluidState().isEmpty()) continue;
            // Hit the face of `neighbor` that points toward `target`.
            Direction face = d.getOpposite();
            Vec3d hit = Vec3d.ofCenter(neighbor).add(Vec3d.of(face.getVector()).multiply(0.5));
            int prevSlot = p.getInventory().selectedSlot;
            p.getInventory().selectedSlot = slot;
            lookAt(p, target);
            BlockHitResult bhr = new BlockHitResult(hit, face, neighbor, false);
            var result = client.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
            p.swingHand(Hand.MAIN_HAND);
            p.getInventory().selectedSlot = prevSlot;
            return result.isAccepted();
        }
        return false;
    }

    /** Returns a hotbar slot (0-8) holding a safe, full building block, or -1. */
    private int findBuildingBlock(MinecraftClient client) {
        var inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BlockItem bi)) continue;
            Block b = bi.getBlock();
            // Avoid gravity blocks, fluids, and non-full blocks for a reliable seal.
            if (b == Blocks.SAND || b == Blocks.GRAVEL || b == Blocks.RED_SAND
                || b == Blocks.ANVIL) continue;
            return i;
        }
        return -1;
    }

    // ── Vein surprise ─────────────────────────────────────────────────────

    private void onOreMined() {
        consecutiveOres++;
        if (consecutiveOres >= 3 && rng.nextFloat() < 0.65f) {
            surpriseTicks   = 25 + rng.nextInt(40);
            consecutiveOres = 0;
        }
    }

    // ── AI hint ───────────────────────────────────────────────────────────

    private void requestAIHint(MinecraftClient client) {
        aiPending.set(true);
        AIClient.INSTANCE.ask(
            "Minecraft mining advisor. One sentence, no markdown.",
            "Strip mining at Y=" + client.player.getBlockY() + ", " + blocksForward
            + " blocks deep, ore target=" + getSetting("Ores") + ". One tip.",
            resp -> { aiPending.set(false);
                var mc = MinecraftClient.getInstance();
                if (mc.player != null) mc.player.sendMessage(Text.literal("§b[AutoMine AI] §7" + resp), false); },
            err  -> aiPending.set(false)
        );
    }

    // ── Ore scanning ─────────────────────────────────────────────────────

    private BlockPos scanForOre(MinecraftClient client, Direction mainDir) {
        int   radius = parseInt(getSetting("OreRadius"), 3);
        float miss = elevatedMissTicks > 0
            ? Math.max(0.55f, parseFloat(getSetting("MissChance"), 15f) / 100f)
            : parseFloat(getSetting("MissChance"), 15f) / 100f;
        BlockPos p = client.player.getBlockPos();
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = p.getX()-radius; x <= p.getX()+radius; x++)
        for (int y = p.getY()-radius; y <= p.getY()+radius; y++)
        for (int z = p.getZ()-radius; z <= p.getZ()+radius; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (skippedOres.contains(pos) || ignoredOres.contains(pos)) continue;
            if (!isTargetOre(client.world.getBlockState(pos).getBlock())) continue;
            if (p.getSquaredDistance(pos) > 16.0) continue;
            if (!seenOres.contains(pos)) {
                seenOres.add(pos);
                if (rng.nextFloat() < miss) { skippedOres.add(pos); continue; }
            }
            candidates.add(pos);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(p::getSquaredDistance));
        return candidates.get(0);
    }

    private boolean isTargetOre(Block block) {
        String m = getSetting("Ores");
        var d=ORE_GROUPS.get("Diamond"); var i=ORE_GROUPS.get("Iron");
        var g=ORE_GROUPS.get("Gold");   var e=ORE_GROUPS.get("Emerald");
        var a=ORE_GROUPS.get("AncientDebris");
        return switch(m) {
            case "Diamond"      -> d.contains(block);
            case "Iron"         -> i.contains(block);
            case "Diamond+Iron" -> d.contains(block)||i.contains(block);
            case "All Valuable" -> d.contains(block)||i.contains(block)||g.contains(block)||e.contains(block)||a.contains(block);
            default             -> ORE_GROUPS.values().stream().anyMatch(s->s.contains(block));
        };
    }

    // ── Geometry ─────────────────────────────────────────────────────────

    private void lookAt(ClientPlayerEntity p, BlockPos t) {
        Vec3d diff = Vec3d.ofCenter(t).subtract(p.getEyePos());
        double h = Math.sqrt(diff.x*diff.x+diff.z*diff.z);
        p.setYaw((float)Math.toDegrees(Math.atan2(-diff.x,diff.z)));
        p.setPitch((float)Math.max(-89.9,Math.min(89.9,Math.toDegrees(-Math.atan2(diff.y,h)))));
    }

    private Direction faceTo(BlockPos from, BlockPos target) {
        BlockPos d=from.subtract(target); int ax=Math.abs(d.getX()),ay=Math.abs(d.getY()),az=Math.abs(d.getZ());
        if(ax>=ay&&ax>=az) return d.getX()>0?Direction.EAST:Direction.WEST;
        if(ay>=ax&&ay>=az) return d.getY()>0?Direction.UP:Direction.DOWN;
        return d.getZ()>0?Direction.SOUTH:Direction.NORTH;
    }

    /** Rotate a cardinal direction 90°: side>0 = left of forward, side<0 = right. */
    private Direction rotate(Direction fwd, int side) {
        return side > 0 ? fwd.rotateYCounterclockwise() : fwd.rotateYClockwise();
    }

    private boolean isFluid(net.minecraft.world.World world, BlockPos pos) {
        return !world.getBlockState(pos).getFluidState().isEmpty();
    }

    /** True if any of the 6 neighbours (or the block itself) is a fluid. */
    private boolean isFluidAround(net.minecraft.world.World world, BlockPos pos) {
        if (isFluid(world, pos)) return true;
        for (Direction d : Direction.values()) if (isFluid(world, pos.offset(d))) return true;
        return false;
    }

    private Direction yawToDir(float yaw) {
        float y=normalYaw(yaw);
        if(y<45||y>=315) return Direction.SOUTH;
        if(y<135)        return Direction.WEST;
        if(y<225)        return Direction.NORTH;
        return Direction.EAST;
    }

    private float snapToCardinal(float yaw) {
        float y=normalYaw(yaw);
        if(y<45||y>=315) return 0f;
        if(y<135) return 90f;
        if(y<225) return 180f;
        return 270f;
    }

    private float normalYaw(float y) { return ((y%360)+360)%360; }

    private boolean movedIn(BlockPos from, BlockPos to, Direction dir) {
        if(from.equals(to)) return false;
        return switch(dir){
            case NORTH->to.getZ()<from.getZ(); case SOUTH->to.getZ()>from.getZ();
            case EAST ->to.getX()>from.getX(); case WEST ->to.getX()<from.getX();
            default->false;
        };
    }

    private void smoothYaw(ClientPlayerEntity p, float target) {
        float cur=p.getYaw(),diff=target-cur;
        while(diff>180)diff-=360; while(diff<-180)diff+=360;
        p.setYaw(cur+(Math.abs(diff)>15?Math.signum(diff)*8:diff));
    }

    private String dirName(Direction d) {
        return switch(d){case NORTH->"North";case SOUTH->"South";case EAST->"East";case WEST->"West";default->d.getName();};
    }

    private void maybeRandomPause() {
        if(rng.nextInt(100)<parseInt(getSetting("PauseChance"),20))
            pauseTicksLeft=rng.nextInt(parseInt(getSetting("MaxPause"),30))+1;
    }

    private int   parseInt(String s,int def)   {try{return (int)Double.parseDouble(s.trim());}catch(Exception e){return def;}}
    private float parseFloat(String s,float d) {try{return Float.parseFloat(s.trim());}catch(Exception e){return d;}}
}
