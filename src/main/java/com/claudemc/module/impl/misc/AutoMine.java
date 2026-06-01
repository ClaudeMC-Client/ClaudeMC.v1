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
import java.util.stream.Collectors;

public class AutoMine extends Module {

    public static AutoMine INSTANCE;

    public enum Phase { IDLE, FORWARD, BRANCH, RETURNING }
    private volatile Phase phase = Phase.IDLE;

    private float mainYaw, branchYaw;
    private int   blocksForward, blocksBranch, returnBlocksLeft;
    private int   branchSide = 1;

    private BlockPos mineTarget;
    private boolean  miningOre;
    private boolean  prevMineWasAir = true;

    private final Random rng = new Random();
    private int pauseTicksLeft;

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
        super("AutoMine", "Human-like strip miner with staff-aware evasion", Category.MISC);
        addMode("Ores", "Diamond+Iron", "Diamond+Iron", "Diamond", "Iron", "All Valuable", "Everything");
        addNumber("BranchEvery", 16,  4,  64,  4, true);
        addNumber("BranchLen",    8,  2,  32,  2, true);
        addNumber("OreRadius",    3,  1,   5,  1, true);
        addNumber("MissChance",  15,  0,  60,  5, true);
        addNumber("PauseChance", 20,  0,  60,  5, true);
        addNumber("MaxPause",    30,  5, 100,  5, true);
        addBool("UseAI", false);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        phase = Phase.IDLE;
        blocksForward = blocksBranch = returnBlocksLeft = 0;
        branchSide = 1;
        mineTarget = null; miningOre = false; prevMineWasAir = true;
        pauseTicksLeft = aiTimer = oreSnapTimer = 0;
        consecutiveOres = surpriseTicks = staffBreakLeft = elevatedMissTicks = 0;
        seenOres.clear(); skippedOres.clear(); ignoredOres.clear();
        knownOres.clear(); oreSnapInit = false;
        wasPaused = wantForward = wantBack = false;
        lastPos = null;
    }

    @Override
    public void onDisable() { wantForward = wantBack = false; }

    // ── Main tick ─────────────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        wantForward = wantBack = false;

        // ── Staff detection ───────────────────────────────────────────────
        boolean staffActive = AntiAFK.INSTANCE != null && AntiAFK.INSTANCE.isEnabled()
                              && AntiAFK.INSTANCE.isEvading();
        if (staffActive && !wasPaused) {
            wasPaused         = true;
            staffBreakLeft    = 20 + rng.nextInt(1180); // 1–60 s random break
            elevatedMissTicks = 12000;                  // 10 min elevated miss
            mineTarget        = null;
            seenOres.clear();
            // Snapshot look direction — player stays completely still during break.
            // Locking overrides AntiAFK's look-around (AutoMine ticks after AntiAFK).
            breakYaw   = client.player.getYaw();
            breakPitch = client.player.getPitch();
        }
        if (!staffActive && wasPaused) wasPaused = false;

        if (staffBreakLeft > 0) {
            staffBreakLeft--;
            // Stay absolutely still — don't move eyes, don't mine.
            // Random looking would look like xray scanning.
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
            lastPos = client.player.getBlockPos();
            seenOres.clear(); skippedOres.clear(); ignoredOres.clear();
            knownOres.clear(); oreSnapInit = false;
            phase = Phase.FORWARD;
            client.player.sendMessage(
                Text.literal("§a[AutoMine] §7Starting — " + dirName(yawToDir(mainYaw))), false);
        }

        checkOreSpawns(client);

        if (Boolean.parseBoolean(getSetting("UseAI")) && AIConfig.INSTANCE.isConfigured()
                && !aiPending.get() && ++aiTimer >= 640) { aiTimer = 0; requestAIHint(client); }

        switch (phase) {
            case FORWARD   -> tickForward(client);
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

        // Find ores that appeared this second (potential staff /setblock)
        List<BlockPos> newOres = current.stream()
            .filter(pos -> !knownOres.contains(pos) && !seenOres.contains(pos))
            .collect(Collectors.toList());

        if (newOres.size() >= 3) { // 3+ ores appearing at once = suspicious
            for (BlockPos pos : newOres) {
                if (isOffPath(client.player, pos)) {
                    // Off to the side — a normal player with no xray wouldn't notice
                    ignoredOres.add(pos);
                    seenOres.add(pos);
                    skippedOres.add(pos);
                }
                // In-path ores: let the tunnel mine them naturally — mark as seen
                // so scanForOre won't actively deviate toward them either
                else {
                    seenOres.add(pos); // mine if in path, but don't seek
                    skippedOres.add(pos);
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
        int interval = parseInt(getSetting("BranchEvery"), 16);
        if (blocksForward > 0 && blocksForward % interval == 0) { startBranch(p); return; }

        // Seek nearby ore
        BlockPos ore = scanForOre(client, dir);
        if (ore != null && (mineTarget == null || world.getBlockState(mineTarget).isAir()))
            { mineTarget = ore; miningOre = true; }

        if (miningOre && mineTarget != null) {
            boolean isAir = world.getBlockState(mineTarget).isAir();
            if (!prevMineWasAir && isAir) onOreMined(); // just mined one
            prevMineWasAir = isAir;
            if (!isAir) {
                lookAt(p, mineTarget);
                client.interactionManager.attackBlock(mineTarget, faceTo(p.getBlockPos(), mineTarget));
                return;
            }
            miningOre = false;
        } else { consecutiveOres = 0; }

        // Mine 2-tall forward path
        BlockPos ahead = p.getBlockPos().offset(dir), above = ahead.up();
        if (!world.getBlockState(ahead).isAir() && world.getBlockState(ahead).getHardness(world, ahead) >= 0)
            { lookAt(p, ahead); client.interactionManager.attackBlock(ahead, dir.getOpposite()); return; }
        if (!world.getBlockState(above).isAir() && world.getBlockState(above).getHardness(world, above) >= 0)
            { lookAt(p, above); client.interactionManager.attackBlock(above, dir.getOpposite()); return; }

        wantForward = true; smoothYaw(p, mainYaw);
        if (lastPos != null && movedIn(lastPos, p.getBlockPos(), dir)) { blocksForward++; maybeRandomPause(); }
    }

    // ── BRANCH ────────────────────────────────────────────────────────────

    private void startBranch(ClientPlayerEntity p) {
        branchYaw = normalYaw(mainYaw - branchSide * 90f);
        blocksBranch = 0; phase = Phase.BRANCH; p.setYaw(branchYaw);
    }

    private void tickBranch(MinecraftClient client) {
        var p = client.player; var world = client.world;
        Direction dir = yawToDir(branchYaw);
        if (blocksBranch >= parseInt(getSetting("BranchLen"), 8))
            { returnBlocksLeft = blocksBranch; phase = Phase.RETURNING; p.setYaw(normalYaw(branchYaw+180f)); return; }

        BlockPos ahead = p.getBlockPos().offset(dir), above = ahead.up();
        if (!world.getBlockState(ahead).isAir() && world.getBlockState(ahead).getHardness(world, ahead) >= 0)
            { lookAt(p, ahead); client.interactionManager.attackBlock(ahead, dir.getOpposite()); return; }
        if (!world.getBlockState(above).isAir() && world.getBlockState(above).getHardness(world, above) >= 0)
            { lookAt(p, above); client.interactionManager.attackBlock(above, dir.getOpposite()); return; }

        BlockPos ore = scanForOre(client, dir);
        if (ore != null && (mineTarget == null || world.getBlockState(mineTarget).isAir()))
            { mineTarget = ore; miningOre = true; }
        if (miningOre && mineTarget != null) {
            boolean isAir = world.getBlockState(mineTarget).isAir();
            if (!prevMineWasAir && isAir) onOreMined();
            prevMineWasAir = isAir;
            if (!isAir) { lookAt(p, mineTarget); client.interactionManager.attackBlock(mineTarget, faceTo(p.getBlockPos(), mineTarget)); return; }
            miningOre = false;
        }

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

    // ── Vein surprise ─────────────────────────────────────────────────────

    private void onOreMined() {
        consecutiveOres++;
        if (consecutiveOres >= 3 && rng.nextFloat() < 0.65f) {
            surpriseTicks   = 25 + rng.nextInt(40); // 1.25–3.25 s look-around
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
        // Elevated miss after staff detection: at least 55%, up to configured max
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
        return switch(d){case NORTH->"North";case SOUTH->"South";case EAST->"East";case WEST->"West";default->d.asString();};
    }

    private void maybeRandomPause() {
        if(rng.nextInt(100)<parseInt(getSetting("PauseChance"),20))
            pauseTicksLeft=rng.nextInt(parseInt(getSetting("MaxPause"),30))+1;
    }

    private int   parseInt(String s,int def)   {try{return Integer.parseInt(s.trim());}catch(Exception e){return def;}}
    private float parseFloat(String s,float d) {try{return Float.parseFloat(s.trim());}catch(Exception e){return d;}}
}
