package com.claudemc.module;

import com.claudemc.module.impl.AimAssistModule;
import com.claudemc.module.impl.combat.*;
import com.claudemc.module.impl.misc.*;
import com.claudemc.module.impl.misc.ForceOP;
import com.claudemc.module.impl.misc.ForceCreative;
import com.claudemc.module.impl.movement.*;
import com.claudemc.module.impl.player.*;
import com.claudemc.module.impl.render.*;
import com.claudemc.module.impl.world.*;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        // Combat
        register(new AimAssistModule());
        register(new KillAura());
        register(new KillauraLegit());
        register(new MultiAura());
        register(new ClickAura());
        register(new Velocity());
        register(new AutoTotem());
        register(new Criticals());
        register(new AutoCrystal());
        register(new AnchorAura());
        register(new Surround());
        register(new TriggerBot());
        register(new Reach());
        register(new AntiBot());
        register(new BowAimbot());
        register(new BlockHit());
        register(new ArrowDMG());
        register(new MaceDMG());
        register(new FeedAura());
        register(new BonemealAura());
        register(new TrollPotion());

        // Movement
        register(new Flight());
        register(new Speed());
        register(new NoFall());
        register(new Jesus());
        register(new Step());
        register(new Sprint());
        register(new Scaffold());
        register(new SafeWalk());
        register(new ElytraFlight());
        register(new ExtraElytra());
        register(new PacketFly());
        register(new InventoryMove());
        register(new BunnyHop());
        register(new AutoWalk());
        register(new HighJump());
        register(new Spider());
        register(new Dolphin());
        register(new Glide());
        register(new Jetpack());
        register(new BoatFly());
        register(new NoClip());
        register(new Phase());
        register(new Blink());
        register(new Parkour());
        register(new Sneak());
        register(new SnowShoe());
        register(new NoSlowdown());
        register(new NoWeb());
        register(new CameraNoClip());
        register(new AntiCactus());
        register(new AntiFire());
        register(new AntiEntityPush());
        register(new AntiWaterPush());
        register(new AntiWobble());
        register(new MountBypass());

        // Player
        register(new AutoEat());
        register(new FastBreak());
        register(new FastEat());
        register(new FastPlace());
        register(new FastBow());
        register(new FastLadder());
        register(new ChestStealer());
        register(new AntiHunger());
        register(new AntiBlind());
        register(new AntiPotion());
        register(new AutoArmor());
        register(new AutoFish());
        register(new AutoFarm());
        register(new AutoSwitch());
        register(new AutoSword());
        register(new AutoTool());
        register(new AutoDrop());
        register(new AutoSign());
        register(new AutoSoup());
        register(new AutoSwim());
        register(new AutoLeave());
        register(new AutoPotion());
        register(new AirPlace());
        register(new Liquids());
        register(new Throw());
        register(new Restock());
        register(new Protect());
        register(new Regen());
        register(new PotionSaver());
        register(new ItemGenerator());
        register(new NoMiningFatigue());
        register(new NoHurtcam());
        register(new NoLevitation());
        register(new NoPumpkin());
        register(new NoShieldOverlay());
        register(new NoVignette());
        register(new NoBackground());
        register(new NameProtect());
        register(new SkinDerp());
        register(new InvManager());

        // Render
        register(new ESP());
        register(new BlockESP());
        register(new StorageESP());
        register(new BarrierESP());
        register(new PortalESP());
        register(new ItemESP());
        register(new MobSpawnESP());
        register(new CaveFinder());
        register(new BaseFinder());
        register(new NewChunks());
        register(new OpenWaterESP());
        register(new Search());
        register(new XRay());
        register(new ProphuntESP());
        register(new TrueSight());
        register(new Tracers());
        register(new Fullbright());
        register(new FreeCam());
        register(new Nametags());
        register(new HealthTags());
        register(new AntiInvis());
        register(new Trajectories());
        register(new HoleESP());
        register(new OreESP());
        register(new Chams());
        register(new Breadcrumbs());
        register(new LogoutSpots());
        register(new Zoom());
        register(new Radar());
        register(new TimeChanger());
        register(new WeatherChanger());
        register(new NoRender());
        register(new RemoteView());
        register(new Overlay());
        register(new RainbowUI());
        register(new LSD());
        register(new PlayerFinder());
        register(new Headless());

        // World
        register(new Nuker());
        register(new NukerLegit());
        register(new SpeedNuker());
        register(new Timer());
        register(new VeinMiner());
        register(new PacketMine());
        register(new AutoBuild());
        register(new Excavator());
        register(new Tillaura());
        register(new TreeBot());
        register(new InstaBuild());
        register(new InstantBunker());
        register(new BuildRandom());
        register(new Kaboom());
        register(new Follow());
        register(new Navigator());
        register(new Panic());

        // Misc / Exploit / Chat
        register(new AutoRespawn());
        register(new BookDupe());
        register(new VanishDetect());
        register(new RecordProof());
        register(new NoPacketKick());
        register(new ForceOP());
        register(new ForceCreative());
        register(new AuctionDupe());
        register(new MiniMessageExploit());
        register(new ServerCrash());
        register(new NocomCrash());
        register(new OPSign());
        register(new CMDBlock());
        register(new ForcePush());
        register(new MassTPA());
        register(new AutoReply());
        register(new AntiAFK());
        register(new NameSpoof());
        register(new ChatSpammer());
        register(new InfiniChat());
        register(new FancyChat());
        register(new AutoComplete());
        register(new PacketLogger());
        register(new AntiSpam());
        register(new FakePlayer());
        register(new Derp());
        register(new HeadRoll());
        register(new MileyCyrus());
        // AI modules
        register(new SmartReply());
        register(new ExploitAdvisor());
        register(new AIAssist());
        register(new AutoMine());
        register(new ServerFinder());
        register(new ForeachCmd());
        register(new AutoAuth());
        register(new AuthMeBypass());
        register(new BookColors());
        register(new AutoReconnect());
    }

    private void register(Module m) { modules.add(m); }

    public void onTick(MinecraftClient client) {
        for (Module m : modules) if (m.isEnabled()) m.onTick(client);
    }

    public List<Module> getModules() { return modules; }

    public List<Module> getByCategory(Category cat) {
        return modules.stream().filter(m -> m.getCategory() == cat).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> c) {
        for (Module m : modules) if (m.getClass() == c) return (T) m;
        return null;
    }
}
