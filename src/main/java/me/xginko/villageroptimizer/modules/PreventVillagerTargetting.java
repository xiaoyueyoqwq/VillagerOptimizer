package me.xginko.villageroptimizer.modules;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.cache.VillagerManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

public class PreventVillagerTargetting implements VillagerOptimizerModule, Listener {

    private final VillagerManager villagerManager;

    protected PreventVillagerTargetting() {
        this.villagerManager = VillagerOptimizer.getVillagerManager();
    }

    @Override
    public void enable() {
        VillagerOptimizer plugin = VillagerOptimizer.getInstance();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public boolean shouldEnable() {
        return VillagerOptimizer.getConfiguration().getBoolean("optimization.behavior.optimized-villagers-dont-get-targeted", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Villager villager && villagerManager.getOrAdd(villager).isOptimized()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onEntityTargetVillager(EntityPathfindEvent event) {
        if (event.getTargetEntity() instanceof Villager villager && villagerManager.getOrAdd(villager).isOptimized()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onEntityAttackVillager(EntityDamageByEntityEvent event) {
        if (
                event.getEntityType().equals(EntityType.VILLAGER)
                && event.getDamager() instanceof Mob attacker
                && villagerManager.getOrAdd((Villager) event.getEntity()).isOptimized()
        ) {
            attacker.setTarget(null);
        }
    }
 }
