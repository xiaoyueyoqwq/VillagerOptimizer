package me.xginko.villageroptimizer.wrapper;

import me.xginko.villageroptimizer.VillagerOptimizer;
import me.xginko.villageroptimizer.enums.Keyring;
import me.xginko.villageroptimizer.enums.OptimizationType;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class MainVillagerDataHandlerImpl implements VillagerDataHandler {

    private final @NotNull Villager villager;
    private final @NotNull PersistentDataContainer dataContainer;

    MainVillagerDataHandlerImpl(@NotNull Villager villager) {
        this.villager = villager;
        this.dataContainer = villager.getPersistentDataContainer();
    }

    @Override
    public boolean isMain() {
        return true;
    }

    @Override
    public boolean isOptimized() {
        return dataContainer.has(Keyring.VillagerOptimizer.OPTIMIZATION_TYPE.getKey(), PersistentDataType.STRING);
    }

    @Override
    public boolean canOptimize(final long cooldown_millis) {
        return System.currentTimeMillis() > getLastOptimize() + cooldown_millis;
    }

    @Override
    public void setOptimizationType(final OptimizationType type) {
        VillagerOptimizer.getFoliaLib().getImpl().runAtEntityTimer(villager, setOptimization -> {
            // Keep repeating task until villager is no longer trading with a player
            if (villager.isTrading()) return;

            if (type == OptimizationType.NONE) {
                if (isOptimized()) {
                    dataContainer.remove(Keyring.VillagerOptimizer.OPTIMIZATION_TYPE.getKey());
                }
                villager.setAware(true);
                villager.setAI(true);
            } else {
                dataContainer.set(Keyring.VillagerOptimizer.OPTIMIZATION_TYPE.getKey(), PersistentDataType.STRING, type.name());
                villager.setAware(false);
            }

            // End repeating task once logic is finished
            setOptimization.cancel();
        }, 0L, 1L, TimeUnit.SECONDS);
    }

    @Override
    public @NotNull OptimizationType getOptimizationType() {
        if (!isOptimized()) {
            return OptimizationType.valueOf(dataContainer.get(Keyring.VillagerOptimizer.OPTIMIZATION_TYPE.getKey(), PersistentDataType.STRING));
        } else {
            return OptimizationType.NONE;
        }
    }

    @Override
    public void saveOptimizeTime() {
        dataContainer.set(Keyring.VillagerOptimizer.LAST_OPTIMIZE_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG, System.currentTimeMillis());
    }

    /**
     * @return The system time in millis when the villager was last optimized, 0L if the villager was never optimized.
     */
    public long getLastOptimize() {
        if (dataContainer.has(Keyring.VillagerOptimizer.LAST_OPTIMIZE_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG)) {
            return dataContainer.get(Keyring.VillagerOptimizer.LAST_OPTIMIZE_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG);
        }
        return 0L;
    }

    @Override
    public long getOptimizeCooldownMillis(final long cooldown_millis) {
        return Math.max(System.currentTimeMillis() - getLastOptimize(), cooldown_millis);
    }

    @Override
    public boolean canRestock(final long cooldown_millis) {
        return getLastRestock() + cooldown_millis <= System.currentTimeMillis();
    }

    @Override
    public void saveRestockTime() {
        dataContainer.set(Keyring.VillagerOptimizer.LAST_RESTOCK_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG, System.currentTimeMillis());
    }

    /**
     * @return The time when the entity was last restocked.
     */
    public long getLastRestock() {
        long lastRestock = 0L;
        if (dataContainer.has(Keyring.VillagerOptimizer.LAST_RESTOCK_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG)) {
            lastRestock = dataContainer.get(Keyring.VillagerOptimizer.LAST_RESTOCK_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG);
        }
        return lastRestock;
    }

    @Override
    public long getRestockCooldownMillis(final long cooldown_millis) {
        if (dataContainer.has(Keyring.VillagerOptimizer.LAST_RESTOCK_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG))
            return System.currentTimeMillis() - (dataContainer.get(Keyring.VillagerOptimizer.LAST_RESTOCK_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG) + cooldown_millis);
        return cooldown_millis;
    }

    @Override
    public boolean canLevelUp(final long cooldown_millis) {
        return System.currentTimeMillis() >= getLastLevelUpTime() + cooldown_millis;
    }

    @Override
    public void saveLastLevelUp() {
        dataContainer.set(Keyring.VillagerOptimizer.LAST_LEVELUP_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG, System.currentTimeMillis());
    }

    /**
     * @return The systime in millis when the entity was last leveled up.
     */
    public long getLastLevelUpTime() {
        if (dataContainer.has(Keyring.VillagerOptimizer.LAST_LEVELUP_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG))
            return dataContainer.get(Keyring.VillagerOptimizer.LAST_LEVELUP_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG);
        return 0L;
    }

    @Override
    public long getLevelCooldownMillis(final long cooldown_millis) {
        if (dataContainer.has(Keyring.VillagerOptimizer.LAST_LEVELUP_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG))
            return System.currentTimeMillis() - (dataContainer.get(Keyring.VillagerOptimizer.LAST_LEVELUP_SYSTIME_MILLIS.getKey(), PersistentDataType.LONG) + cooldown_millis);
        return cooldown_millis;
    }
}