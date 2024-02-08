package me.xginko.villageroptimizer.enums;

import net.kyori.adventure.key.Namespaced;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class Keyring {

    public enum Spaces implements Namespaced {

        VillagerOptimizer("VillagerOptimizer"),
        AntiVillagerLag("AntiVillagerLag");

        @Pattern("[a-z0-9_\\-.]+")
        private final @NotNull String namespace;

        Spaces(@NotNull @Pattern("[a-z0-9_\\-.]+") String pluginName) {
            this.namespace = pluginName.toLowerCase(Locale.ROOT);
        }

        @Override
        @Pattern("[a-z0-9_\\-.]+")
        public @NotNull String namespace() {
            return namespace;
        }
    }

    /**
     * Returns a NamespacedKey as if it was created by a specific plugin.
     * This is possible because they are created using {@link NamespacedKey#NamespacedKey(Plugin, String)},
     * meaning the Namespace is always the return of {@link Plugin#getName()} && {@link String#toLowerCase()}
     * using {@link Locale#ROOT}
     *
     * @param pluginName The plugin name as configured in plugin.yml, under section name
     * @param key The key name
     *
     * @return a {@link NamespacedKey} that can be used to test for and read data stored by plugins
     * from a {@link PersistentDataContainer}
     */
    @SuppressWarnings("deprecation")
    public static NamespacedKey getKey(@NotNull String pluginName, @NotNull String key) {
        return new NamespacedKey(pluginName.toLowerCase(Locale.ROOT), key);
    }

    public enum VillagerOptimizer implements Keyed {

        OPTIMIZATION_TYPE("optimization-type"),
        LAST_OPTIMIZE("last-optimize"),
        LAST_LEVELUP("last-levelup"),
        LAST_RESTOCK("last-restock"),
        LAST_OPTIMIZE_NAME("last-optimize-name");

        private final @NotNull NamespacedKey key;

        @SuppressWarnings("deprecation")
        VillagerOptimizer(@NotNull String key) {
            this.key = new NamespacedKey(Spaces.VillagerOptimizer.namespace(), key);
        }

        @Override
        public @NotNull NamespacedKey getKey() {
            return key;
        }
    }

    public enum AntiVillagerLag implements Keyed {

        NEXT_OPTIMIZATION_SYSTIME_SECONDS("cooldown"), // Returns LONG -> (System.currentTimeMillis() / 1000) + cooldown seconds
        LAST_RESTOCK_WORLDFULLTIME("time"), // Returns LONG -> villager.getWorld().getFullTime()
        NEXT_LEVELUP_SYSTIME_SECONDS("levelCooldown"), // Returns LONG -> (System.currentTimeMillis() / 1000) + cooldown seconds
        OPTIMIZED_ANY("Marker"), // Returns STRING -> "AVL"
        OPTIMIZED_BLOCK("disabledByBlock"), // Returns STRING -> key().toString()
        OPTIMIZED_WORKSTATION("disabledByWorkstation"); // Returns STRING -> key().toString()

        private final @NotNull NamespacedKey key;

        @SuppressWarnings("deprecation")
        AntiVillagerLag(@NotNull String avlKey) {
            this.key = new NamespacedKey(Spaces.AntiVillagerLag.namespace(), avlKey);
        }

        @Override
        public @NotNull NamespacedKey getKey() {
            return key;
        }
    }
}
