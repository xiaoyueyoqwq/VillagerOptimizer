package me.xginko.villageroptimizer.modules.gameplay;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import me.xginko.villageroptimizer.VillagerCache;
import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.config.Config;
import me.xginko.villageroptimizer.modules.VillagerOptimizerModule;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;
import java.util.stream.Collectors;

public class PreventOptimizedDamage implements VillagerOptimizerModule, Listener {

    private final VillagerCache villagerCache;
    private final Set<EntityDamageEvent.DamageCause> damage_causes_to_cancel;
    private final boolean cancel_knockback;

    public PreventOptimizedDamage() {
        shouldEnable();
        this.villagerCache = VillagerOptimizer.getCache();
        Config config = VillagerOptimizer.getConfiguration();
        config.master().addComment(configPath() + ".enable",
                "Configure what kind of damage you want to cancel for optimized villagers here.");
        this.cancel_knockback = config.getBoolean(configPath() + ".prevent-knockback-from-entity", true,
                "Prevents optimized villagers from getting knocked back by an attacking entity");
        this.damage_causes_to_cancel = config.getList(configPath() + ".damage-causes-to-cancel",
                Arrays.stream(EntityDamageEvent.DamageCause.values()).map(Enum::name).sorted().collect(Collectors.toList()),
                "These are all current entries in the game. Remove what you do not need blocked.\n" +
                "If you want a description or need to add a previously removed type, refer to:\n" +
                "https://jd.papermc.io/paper/1.20/org/bukkit/event/entity/EntityDamageEvent.DamageCause.html")
                .stream()
                .map(configuredDamageCause -> {
                    try {
                        return EntityDamageEvent.DamageCause.valueOf(configuredDamageCause);
                    } catch (IllegalArgumentException e) {
                        warn("DamageCause '" + configuredDamageCause + "' not recognized. Please use correct DamageCause enums from: " +
                             "https://jd.papermc.io/paper/1.20/org/bukkit/event/entity/EntityDamageEvent.DamageCause.html");
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EntityDamageEvent.DamageCause.class)));
    }

    @Override
    public String configPath() {
        return "gameplay.prevent-damage-to-optimized";
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
        return VillagerOptimizer.getConfiguration().getBoolean(configPath() + ".enable", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onDamageByEntity(EntityDamageEvent event) {
        if (
                event.getEntityType().equals(EntityType.VILLAGER)
                && damage_causes_to_cancel.contains(event.getCause())
                && villagerCache.getOrAdd((Villager) event.getEntity()).isOptimized()
        ) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onKnockbackByEntity(EntityKnockbackByEntityEvent event) {
        if (
                cancel_knockback
                && event.getEntityType().equals(EntityType.VILLAGER)
                && villagerCache.getOrAdd((Villager) event.getEntity()).isOptimized()
        ) {
            event.setCancelled(true);
        }
    }
}