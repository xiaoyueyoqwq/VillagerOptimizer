package me.xginko.villageroptimizer.modules.optimizations;

import me.xginko.villageroptimizer.VillagerCache;
import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.WrappedVillager;
import me.xginko.villageroptimizer.config.Config;
import me.xginko.villageroptimizer.enums.OptimizationType;
import me.xginko.villageroptimizer.enums.Permissions;
import me.xginko.villageroptimizer.modules.VillagerOptimizerModule;
import me.xginko.villageroptimizer.utils.CommonUtil;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class OptimizeByWorkstation implements VillagerOptimizerModule, Listener {

    private final VillagerCache villagerCache;
    private final long cooldown;
    private final double search_radius;
    private final boolean shouldLog, shouldNotifyPlayer;

    public OptimizeByWorkstation() {
        shouldEnable();
        this.villagerCache = VillagerOptimizer.getCache();
        Config config = VillagerOptimizer.getConfiguration();
        config.addComment("optimization-methods.workstation-optimization.enable", """
                When enabled, the closest villager near a matching workstation being placed will be optimized.\s
                If a nearby matching workstation is broken, the villager will become unoptimized again.""");
        this.search_radius = config.getDouble("optimization-methods.workstation-optimization.search-radius-in-blocks", 2.0, """
                The radius in blocks a villager can be away from the player when he places a workstation.\s
                The closest unoptimized villager to the player will be optimized.""") / 2;
        this.cooldown = config.getInt("optimization-methods.workstation-optimization.optimize-cooldown-seconds", 600, """
                Cooldown in seconds until a villager can be optimized again using a workstation.\s
                Here for configuration freedom. Recommended to leave as is to not enable any exploitable behavior.""") * 1000L;
        this.shouldNotifyPlayer = config.getBoolean("optimization-methods.workstation-optimization.notify-player", true,
                "Sends players a message when they successfully optimized a villager.");
        this.shouldLog = config.getBoolean("optimization-methods.workstation-optimization.log", false);
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
        return VillagerOptimizer.getConfiguration().getBoolean("optimization-methods.workstation-optimization.enable", false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        Villager.Profession workstationProfession = getWorkstationProfession(placed.getType());
        if (workstationProfession.equals(Villager.Profession.NONE)) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(Permissions.Optimize.WORKSTATION.get())) return;

        final Location workstationLoc = placed.getLocation();
        WrappedVillager closestOptimizableVillager = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : workstationLoc.getNearbyEntities(search_radius, search_radius, search_radius)) {
            if (!entity.getType().equals(EntityType.VILLAGER)) continue;
            Villager villager = (Villager) entity;
            if (!villager.getProfession().equals(workstationProfession)) continue;

            WrappedVillager wVillager = villagerCache.getOrAdd(villager);
            final double distance = entity.getLocation().distance(workstationLoc);

            if (distance < closestDistance) {
                final OptimizationType type = wVillager.getOptimizationType();
                if (type.equals(OptimizationType.NONE) || type.equals(OptimizationType.COMMAND)) {
                    closestOptimizableVillager = wVillager;
                    closestDistance = distance;
                }
            }
        }

        if (closestOptimizableVillager == null) return;

        if (closestOptimizableVillager.canOptimize(cooldown) || player.hasPermission(Permissions.Bypass.WORKSTATION_COOLDOWN.get())) {
            closestOptimizableVillager.setOptimization(OptimizationType.WORKSTATION);
            closestOptimizableVillager.saveOptimizeTime();
            if (shouldNotifyPlayer) {
                final String vilProfession = closestOptimizableVillager.villager().getProfession().toString().toLowerCase();
                final String workstation = placed.getType().toString().toLowerCase();
                VillagerOptimizer.getLang(player.locale()).workstation_optimize_success.forEach(line -> player.sendMessage(line
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%vil_profession%").replacement(vilProfession).build())
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%workstation%").replacement(workstation).build())
                ));
            }
            if (shouldLog)
                VillagerOptimizer.getLog().info(player.getName() + " optimized a villager using workstation: '" + placed.getType().toString().toLowerCase() + "'");
        } else {
            closestOptimizableVillager.villager().shakeHead();
            if (shouldNotifyPlayer) {
                final String timeLeft = CommonUtil.formatTime(closestOptimizableVillager.getOptimizeCooldownMillis(cooldown));
                VillagerOptimizer.getLang(player.locale()).nametag_on_optimize_cooldown.forEach(line -> player.sendMessage(line
                        .replaceText(TextReplacementConfig.builder().matchLiteral("%time%").replacement(timeLeft).build())
                ));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        Villager.Profession workstationProfession = getWorkstationProfession(broken.getType());
        if (workstationProfession.equals(Villager.Profession.NONE)) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(Permissions.Optimize.WORKSTATION.get())) return;

        final Location workstationLoc = broken.getLocation();
        WrappedVillager closestOptimizedVillager = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : workstationLoc.getNearbyEntities(search_radius, search_radius, search_radius)) {
            if (!entity.getType().equals(EntityType.VILLAGER)) continue;
            Villager villager = (Villager) entity;
            if (!villager.getProfession().equals(workstationProfession)) continue;

            WrappedVillager wVillager = villagerCache.getOrAdd(villager);
            final double distance = entity.getLocation().distance(workstationLoc);

            if (distance < closestDistance) {
                final OptimizationType type = wVillager.getOptimizationType();
                if (type.equals(OptimizationType.WORKSTATION) || type.equals(OptimizationType.COMMAND)) {
                    closestOptimizedVillager = wVillager;
                    closestDistance = distance;
                }
            }
        }

        if (closestOptimizedVillager == null) return;

        closestOptimizedVillager.setOptimization(OptimizationType.NONE);
        if (shouldNotifyPlayer) {
            final String vilProfession = closestOptimizedVillager.villager().getProfession().toString().toLowerCase();
            final String workstation = broken.getType().toString().toLowerCase();
            VillagerOptimizer.getLang(player.locale()).workstation_unoptimize_success.forEach(line -> player.sendMessage(line
                    .replaceText(TextReplacementConfig.builder().matchLiteral("%vil_profession%").replacement(vilProfession).build())
                    .replaceText(TextReplacementConfig.builder().matchLiteral("%workstation%").replacement(workstation).build())
            ));
        }
        if (shouldLog)
            VillagerOptimizer.getLog().info(player.getName() + " unoptimized a villager by breaking workstation: '" + broken.getType().toString().toLowerCase() + "'");
    }

    private Villager.Profession getWorkstationProfession(final Material workstation) {
        return switch (workstation) {
            case BARREL -> Villager.Profession.FISHERMAN;
            case CARTOGRAPHY_TABLE -> Villager.Profession.CARTOGRAPHER;
            case SMOKER -> Villager.Profession.BUTCHER;
            case SMITHING_TABLE -> Villager.Profession.TOOLSMITH;
            case GRINDSTONE -> Villager.Profession.WEAPONSMITH;
            case BLAST_FURNACE -> Villager.Profession.ARMORER;
            case CAULDRON -> Villager.Profession.LEATHERWORKER;
            case BREWING_STAND -> Villager.Profession.CLERIC;
            case COMPOSTER -> Villager.Profession.FARMER;
            case FLETCHING_TABLE -> Villager.Profession.FLETCHER;
            case LOOM -> Villager.Profession.SHEPHERD;
            case LECTERN -> Villager.Profession.LIBRARIAN;
            case STONECUTTER -> Villager.Profession.MASON;
            default -> Villager.Profession.NONE;
        };
    }
}