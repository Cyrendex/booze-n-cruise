package me.Cyrendex.boozeNCruise;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class AlcoholManager {

    // Constants
    private static final double BAC_SCALE_PER_GRAM      = 0.0025;
    private static final double ABSORB_RATE_BASE_PER_S  = 0.001;
    private static final double ABSORB_RATE_SCALE_PER_S = 0.04;
    private static final double ELIM_RATE_PER_HOUR      = 0.06; // BAC/hour

    // Runtime
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final Map<String, DrinkDef> drinks = new LinkedHashMap<>(); // id -> def

    public AlcoholManager() {
        registerDefaults();
    }

    // API
    public void ingest(Player player, String drinkId) {
        DrinkDef d = drinks.get(drinkId);
        if (d == null) return;
        Profile prof = profiles.computeIfAbsent(player.getUniqueId(), Profile::new);

        double grams = d.volumeMl * (d.abv / 100.0) * 0.789;
        double units = grams * BAC_SCALE_PER_GRAM;
        prof.absorb += units;

        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
    }

    /** Convert “alcohol in system” to BAC, then apply elimination. Call once per second. */
    public void tickOneSecond(UUID uuid) {
        Profile p = profiles.computeIfAbsent(uuid, Profile::new);

        // Absorption
        if (p.absorb > 0) {
            double absorbed = ABSORB_RATE_BASE_PER_S + ABSORB_RATE_SCALE_PER_S * p.absorb;
            if (absorbed > p.absorb) absorbed = p.absorb;
            p.absorb -= absorbed;
            p.bac += absorbed;
        }

        // Elimination
        double elimPerSec = ELIM_RATE_PER_HOUR / 3600.0;
        p.bac = Math.max(0.0, p.bac - elimPerSec);

        // Clamp
        if (p.bac > 1.0) p.bac = 1.0;
    }

    /** HUD string; null if no profile yet. */
    public String buildHud(UUID uuid) {
        Profile p = profiles.get(uuid);
        if (p == null) return null;
        return String.format("§eBAC: §6%.3f%% §7| §fAbsorb: §a%.3f", p.bac, p.absorb);
    }

    /** If the held item is a registered drink, return its id, null otherwise. */
    public String matchDrink(ItemStack item) {
        if (item == null) return null;
        for (DrinkDef d : drinks.values()) {
            if (item.getType() != d.material) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();

            if (cmd.getStrings().contains(d.customModelData)) {
                return d.id; // matched this drink
            }
        }
        return null;
    }

    /** Consume one item from the stack (basic helper). */
    public static void consumeOne(org.bukkit.entity.Player player,
                                  org.bukkit.inventory.EquipmentSlot hand,
                                  org.bukkit.inventory.ItemStack stack) {
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else {
            if (hand == org.bukkit.inventory.EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(null);
            } else if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }

    // Register drinks
    private void registerDefaults() {
        // Beer is just one drink definition. Add more via registerDrink(...)
        registerDrink(new DrinkDef(
                "beer",
                "Beer",
                5.0,                                // ABV %
                350,                                         // volume ml
                Material.POTION,
                "beer",    // CustomModelData must match the resourcepack
                true                                         // return empty bottle on drink
        ));
    }

    public void registerDrink(DrinkDef def) {
        drinks.put(def.id, def);
    }

    // Data types
    public static final class Profile {
        public final UUID uuid;
        public double bac;      // current BAC
        public double absorb;   // pool not yet in BAC
        public double tolerance; // tolerance to negative effects of intoxication
        public Profile(UUID uuid) { this.uuid = uuid; }
    }

    public static final class DrinkDef {
        public final String id;
        public final String name;
        public final double abv;
        public final int volumeMl;
        public final Material material;
        public final String customModelData;
        public final boolean giveEmptyBottle;

        public DrinkDef(String id, String name, double abv, int volumeMl,
                        Material material, String customModelData, boolean giveEmptyBottle) {
            this.id = id;
            this.name = name;
            this.abv = abv;
            this.volumeMl = volumeMl;
            this.material = material;
            this.customModelData = customModelData;
            this.giveEmptyBottle = giveEmptyBottle;
        }
    }

    public AlcoholManager.DrinkDef getDrink(String id) {
        return drinks.get(id);
    }

    /* Ugly Java getter/setters ew yuck :( */
    public double getBac(UUID id) {
        Profile p = profiles.get(id);
        return (p == null) ? 0.0 : p.bac; // Sober if fetch fails
    }

    public double getTolerance(UUID id) {
        Profile p = profiles.get(id);
        return (p == null) ? 0.0 : p.tolerance; // Set lowest if fetch fails
    }

    public void setTolerance(UUID id, double value) {
        Profile p = profiles.computeIfAbsent(id, Profile::new);
        p.tolerance = clamp01(value);
    }

    public void addTolerance(UUID id, double delta) {
        Profile p = profiles.computeIfAbsent(id, Profile::new);
        p.tolerance = clamp01(p.tolerance + delta);
    }

    // Clamp helper between 0-1
    private static double clamp01(double value) { return value < 0 ? 0: (value > 1 ? 1 : value); }
}
