package me.xginko.villageroptimizer.modules.optimization;

import me.xginko.villageroptimizer.VillagerCache;
import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.wrapper.WrappedVillager;
import me.xginko.villageroptimizer.config.Config;
import me.xginko.villageroptimizer.enums.OptimizationType;
import me.xginko.villageroptimizer.enums.Permissions;
import me.xginko.villageroptimizer.events.VillagerOptimizeEvent;
import me.xginko.villageroptimizer.events.VillagerUnoptimizeEvent;
import me.xginko.villageroptimizer.modules.VillagerOptimizerModule;
import me.xginko.villageroptimizer.utils.GenericUtil;
import me.xginko.villageroptimizer.utils.KyoriUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class OptimizeByBlock implements VillagerOptimizerModule, Listener {

    private final VillagerCache villagerCache;
    private final Set<Material> blocks_that_disable;
    private final long cooldown_millis;
    private final double search_radius;
    private final boolean only_while_sneaking, notify_player, log_enabled;

    public OptimizeByBlock() {
        shouldEnable();
        this.villagerCache = VillagerOptimizer.getCache();
        Config config = VillagerOptimizer.getConfiguration();
        config.master().addComment(configPath() + ".enable",
                "When enabled, the closest villager standing near a configured block being placed will be optimized.\n" +
                "If a configured block is broken nearby, the closest villager will become unoptimized again.");
        this.blocks_that_disable = config.getList(configPath() + ".materials", Arrays.asList(
                "LAPIS_BLOCK", "GLOWSTONE", "IRON_BLOCK"
        ), "Values here need to be valid bukkit Material enums for your server version.")
                .stream()
                .map(configuredMaterial -> {
                    try {
                        return Material.valueOf(configuredMaterial);
                    } catch (IllegalArgumentException e) {
                        warn("Material '" + configuredMaterial + "' not recognized. Please use correct Material enums from: " +
                             "https://jd.papermc.io/paper/1.20/org/bukkit/Material.html");
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Material.class)));
        this.cooldown_millis = TimeUnit.SECONDS.toMillis(
                config.getInt(configPath() + ".optimize-cooldown-seconds", 600,
                "Cooldown in seconds until a villager can be optimized again by using specific blocks.\n" +
                "Here for configuration freedom. Recommended to leave as is to not enable any exploitable behavior."));
        this.search_radius = config.getDouble(configPath() + ".search-radius-in-blocks", 2.0,
                "The radius in blocks a villager can be away from the player when he places an optimize block.\n" +
                "The closest unoptimized villager to the player will be optimized.") / 2;
        this.only_while_sneaking = config.getBoolean(configPath() + ".only-when-sneaking", true,
                "Only optimize/unoptimize by block when player is sneaking during place or break.");
        this.notify_player = config.getBoolean(configPath() + ".notify-player", true,
                "Sends players a message when they successfully optimized or unoptimized a villager.");
        this.log_enabled = config.getBoolean(configPath() + ".log", false);
    }

    @Override
    public String configPath() {
        return "optimization-methods.block-optimization";
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
        return VillagerOptimizer.getConfiguration().getBoolean(configPath() + ".enable", false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onBlockPlace(BlockPlaceEvent event) {
        final Block placed = event.getBlock();
        if (!blocks_that_disable.contains(placed.getType())) return;
        final Player player = event.getPlayer();
        if (!player.hasPermission(Permissions.Optimize.BLOCK.get())) return;
        if (only_while_sneaking && !player.isSneaking()) return;

        final Location blockLoc = placed.getLocation().toCenterLocation();
        WrappedVillager closestOptimizableVillager = null;
        double closestDistance = Double.MAX_VALUE;

        for (Villager villager : blockLoc.getNearbyEntitiesByType(Villager.class, search_radius)) {
            final Villager.Profession profession = villager.getProfession();
            if (profession.equals(Villager.Profession.NONE) || profession.equals(Villager.Profession.NITWIT)) continue;
            final double distance = villager.getLocation().distanceSquared(blockLoc);
            if (distance >= closestDistance) continue;

            final WrappedVillager wVillager = villagerCache.getOrAdd(villager);
            if (wVillager.canOptimize(cooldown_millis)) {
                closestOptimizableVillager = wVillager;
                closestDistance = distance;
            }
        }

        if (closestOptimizableVillager == null) return;

        if (closestOptimizableVillager.canOptimize(cooldown_millis) || player.hasPermission(Permissions.Bypass.BLOCK_COOLDOWN.get())) {
            VillagerOptimizeEvent optimizeEvent = new VillagerOptimizeEvent(
                    closestOptimizableVillager,
                    OptimizationType.BLOCK,
                    player,
                    event.isAsynchronous()
            );

            if (!optimizeEvent.callEvent()) return;
            closestOptimizableVillager.setOptimizationType(optimizeEvent.getOptimizationType());
            closestOptimizableVillager.saveOptimizeTime();

            if (notify_player) {
                final TextReplacementConfig vilProfession = TextReplacementConfig.builder()
                        .matchLiteral("%vil_profession%")
                        .replacement(closestOptimizableVillager.villager().getProfession().toString().toLowerCase())
                        .build();
                final TextReplacementConfig placedMaterial = TextReplacementConfig.builder()
                        .matchLiteral("%blocktype%")
                        .replacement(placed.getType().toString().toLowerCase())
                        .build();
                VillagerOptimizer.getLang(player.locale()).block_optimize_success
                        .forEach(line -> KyoriUtil.sendMessage(player, line.replaceText(vilProfession).replaceText(placedMaterial)));
            }

            if (log_enabled) {
                VillagerOptimizer.getPrefixedLogger().info(Component.text(player.getName() + " optimized villager by block at " +
                                                                          GenericUtil.formatLocation(closestOptimizableVillager.villager().getLocation())).color(GenericUtil.COLOR));
            }
        } else {
            closestOptimizableVillager.sayNo();
            if (notify_player) {
                final TextReplacementConfig timeLeft = TextReplacementConfig.builder()
                        .matchLiteral("%time%")
                        .replacement(GenericUtil.formatDuration(Duration.ofMillis(closestOptimizableVillager.getOptimizeCooldownMillis(cooldown_millis))))
                        .build();
                VillagerOptimizer.getLang(player.locale()).block_on_optimize_cooldown
                        .forEach(line -> KyoriUtil.sendMessage(player, line.replaceText(timeLeft)));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onBlockBreak(BlockBreakEvent event) {
        final Block broken = event.getBlock();
        if (!blocks_that_disable.contains(broken.getType())) return;
        final Player player = event.getPlayer();
        if (!player.hasPermission(Permissions.Optimize.BLOCK.get())) return;
        if (only_while_sneaking && !player.isSneaking()) return;

        final Location blockLoc = broken.getLocation().toCenterLocation();
        WrappedVillager closestOptimizedVillager = null;
        double closestDistance = Double.MAX_VALUE;

        for (Villager villager : blockLoc.getNearbyEntitiesByType(Villager.class, search_radius)) {
            final double distance = villager.getLocation().distanceSquared(blockLoc);
            if (distance >= closestDistance) continue;

            final WrappedVillager wVillager = villagerCache.getOrAdd(villager);
            if (wVillager.isOptimized()) {
                closestOptimizedVillager = wVillager;
                closestDistance = distance;
            }
        }

        if (closestOptimizedVillager == null) return;

        VillagerUnoptimizeEvent unOptimizeEvent = new VillagerUnoptimizeEvent(
                closestOptimizedVillager,
                player,
                OptimizationType.BLOCK,
                event.isAsynchronous()
        );

        if (!unOptimizeEvent.callEvent()) return;
        closestOptimizedVillager.setOptimizationType(OptimizationType.NONE);

        if (notify_player) {
            final TextReplacementConfig vilProfession = TextReplacementConfig.builder()
                    .matchLiteral("%vil_profession%")
                    .replacement(closestOptimizedVillager.villager().getProfession().toString().toLowerCase())
                    .build();
            final TextReplacementConfig brokenMaterial = TextReplacementConfig.builder()
                    .matchLiteral("%blocktype%")
                    .replacement(broken.getType().toString().toLowerCase())
                    .build();
            VillagerOptimizer.getLang(player.locale()).block_unoptimize_success
                    .forEach(line -> KyoriUtil.sendMessage(player, line.replaceText(vilProfession).replaceText(brokenMaterial)));
        }

        if (log_enabled) {
            VillagerOptimizer.getPrefixedLogger().info(Component.text(player.getName() + " unoptimized villager by block at " +
                                                                      GenericUtil.formatLocation(closestOptimizedVillager.villager().getLocation())).color(GenericUtil.COLOR));
        }
    }
}