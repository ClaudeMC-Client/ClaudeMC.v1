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
        register(new Velocity());
        register(new AutoTotem());
        register(new Criticals());
        register(new AutoCrystal());
        register(new Surround());
        register(new TriggerBot());
        register(new Reach());
        register(new AntiBot());

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
        register(new PacketFly());
        register(new InventoryMove());
        register(new BunnyHop());

        // Player
        register(new AutoEat());
        register(new FastBreak());
        register(new ChestStealer());
        register(new AntiHunger());
        register(new AutoArmor());
        register(new AutoFish());
        register(new AutoFarm());
        register(new FastPlace());
        register(new NoMiningFatigue());
        register(new InvManager());

        // Render
        register(new ESP());
        register(new BlockESP());
        register(new StorageESP());
        register(new Tracers());
        register(new Fullbright());
        register(new FreeCam());
        register(new Nametags());
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

        // World
        register(new Nuker());
        register(new Timer());
        register(new VeinMiner());
        register(new PacketMine());
        register(new AutoBuild());

        // Misc
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
        register(new AutoReply());
        register(new AntiAFK());
        register(new NameSpoof());
        register(new ChatSpammer());
        register(new PacketLogger());
        register(new AntiSpam());
        register(new FakePlayer());
        // AI modules
        register(new SmartReply());
        register(new ExploitAdvisor());
        register(new AIAssist());
        register(new AutoMine());
        register(new ServerFinder());
        register(new ForeachCmd());
        register(new AutoAuth());
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
