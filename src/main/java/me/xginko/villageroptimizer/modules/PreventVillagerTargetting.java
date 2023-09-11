package me.xginko.villageroptimizer.modules;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.cache.VillagerManager;
import org.bukkit.entity.Entity;
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
        return VillagerOptimizer.getConfiguration().getBoolean("gameplay.prevent-targeting.enable", true,
                "Prevents hostile entities from targeting optimized villagers.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onTarget(EntityTargetLivingEntityEvent event) {
        // Yes, instanceof checks would look way more beautiful here but checking type is much faster
        Entity target = event.getTarget();
        if (
                target != null
                && target.getType().equals(EntityType.VILLAGER)
                && villagerManager.getOrAdd((Villager) target).isOptimized()
        ) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onEntityTargetVillager(EntityPathfindEvent event) {
        Entity target = event.getTargetEntity();
        if (
                target != null
                && target.getType().equals(EntityType.VILLAGER)
                && villagerManager.getOrAdd((Villager) target).isOptimized()
        ) {
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
