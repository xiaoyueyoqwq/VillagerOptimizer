package me.xginko.villageroptimizer.modules.gameplay;

import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.WrappedVillager;
import me.xginko.villageroptimizer.config.Config;
import me.xginko.villageroptimizer.events.VillagerOptimizeEvent;
import me.xginko.villageroptimizer.events.VillagerUnoptimizeEvent;
import me.xginko.villageroptimizer.modules.VillagerOptimizerModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class RenameOptimizedVillagers implements VillagerOptimizerModule, Listener {

    private final VillagerOptimizer plugin;
    private final Component optimized_name;
    private final boolean overwrite_previous_name;

    public RenameOptimizedVillagers() {
        shouldEnable();
        this.plugin = VillagerOptimizer.getInstance();
        Config config = VillagerOptimizer.getConfiguration();
        config.addComment("gameplay.rename-optimized-villagers.enable", """
                Will change a villager's name to the name configured below when they are optimized.\s
                These names will be removed when unoptimized again if they were not changed in the meantime.
                """);
        this.optimized_name = MiniMessage.miniMessage().deserialize(config.getString("gameplay.rename-optimized-villagers.optimized-name", "<green>Optimized",
                "The name that will be used to mark optimized villagers. Uses MiniMessage format."));
        this.overwrite_previous_name = config.getBoolean("gameplay.rename-optimized-villagers.overwrite-existing-name", false,
                "If set to true, will rename even if the villager has already been named.");
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean shouldEnable() {
        return VillagerOptimizer.getConfiguration().getBoolean("gameplay.rename-optimized-villagers.enable", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onOptimize(VillagerOptimizeEvent event) {
        WrappedVillager wVillager = event.getWrappedVillager();
        Villager villager = wVillager.villager();

        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (overwrite_previous_name || villager.customName() == null) {
                villager.customName(optimized_name);
                wVillager.memorizeName(optimized_name);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onUnOptimize(VillagerUnoptimizeEvent event) {
        WrappedVillager wVillager = event.getWrappedVillager();
        Villager villager = wVillager.villager();

        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            final Component currentName = villager.customName();
            final Component memorizedName = wVillager.getMemorizedName();
            if (currentName != null && currentName.equals(memorizedName))
                villager.customName(null);
            if (memorizedName != null)
                wVillager.forgetName();
        }, 10L);
    }
}