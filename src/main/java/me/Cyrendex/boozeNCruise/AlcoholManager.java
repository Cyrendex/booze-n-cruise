package me.Cyrendex.boozeNCruise;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.*;

public final class AlcoholManager {

    // Constants
    private static final double BAC_SCALE_PER_GRAM = 0.0025;
    private static final double ABSORB_RATE_BASE_PER_S = 0.001;
    private static final double ABSORB_RATE_SCALE_PER_S = 0.04;
    private static final double ELIM_RATE_PER_HOUR = 0.06; // BAC/hour
    private static final double TOLERANCE_SCALE = 40.0; // Around 40 "standard" drinks for ~0.63, 120 for ~0.95 tolerance
    private static final double STANDARD_DRINK_GRAM = 14.0; // US standard, feel free to customize

    // Runtime
    private final Map<UUID, Profile> profiles = new HashMap<>();

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

    public static DrinkDef lookupDrink(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        List<String> tags = cmd.getStrings();

        for (String t : tags) {
            DrinkDef def = DRINKS.get(t);
            if (def != null) return def;
        }
        return null;
    }

    // COPIED HERE FOR ONLY DEBUG PURPOSES WILL BE DELETED
    private static final double[] STAGE_BASES = { 0.00, 0.02, 0.06, 0.12, 0.20, 0.30 };
    private static final double SHIFT_SLOPE   = 0.75;
    // ==========================================================

    public void addDrink(UUID id, double volumeMl, double abvPercent) {
        Profile p = getOrCreateProfile(id);

        // ethanol grams
        double grams = volumeMl * (abvPercent / 100.0) * 0.789;

        // convert grams of ethanol into absorbable "units"
        double units = grams * BAC_SCALE_PER_GRAM;
        p.absorb += units;

        // lifetime standard drinks
        double std = grams / STANDARD_DRINK_GRAM;
        p.lifetimeDrinks += std;
        updateTolerance(p);

        // FROM HERE ON IS ALSO DEBUG
        Player pl = Bukkit.getPlayer(id);
        if (pl != null) {
            double tol   = clamp01(p.tolerance); // just in case
            double shift = 1.0 + SHIFT_SLOPE * tol; // how much thresholds are pushed right
            pl.sendMessage(String.format(
                    "§7[Alcohol] Tolerance now §e%.3f§7 | shift factor §ex%.2f",
                    tol, shift
            ));

            // Find the next threshold above current BAC (after shift)
            double bacNow = p.bac;
            int nextIndex = -1;
            double nextShifted = 0.0;
            for (int i = 1; i < STAGE_BASES.length; i++) {
                double th = STAGE_BASES[i] * shift;
                if (bacNow < th) {
                    nextIndex   = i;
                    nextShifted = th;
                    break;
                }
            }
            if (nextIndex == -1) {
                pl.sendMessage(String.format(
                        "§7[Alcohol] Next stage threshold: §e(≥ S5)§7 — already at/above highest threshold | BAC §e%.3f%%",
                        bacNow
                ));
            } else {
                pl.sendMessage(String.format(
                        "§7[Alcohol] Next stage threshold: §eS%d§7 at §e%.3f%%§7 (base §e%.3f%%§7) | BAC §e%.3f%%",
                        nextIndex, nextShifted, STAGE_BASES[nextIndex], bacNow
                ));
            }
        }
    }
    public static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); } // DEBUG HELPER

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

    // Data types
    public static final class Profile {
        public final UUID uuid;
        public double bac;      // current BAC
        public double absorb;   // pool not yet in BAC
        public double tolerance; // tolerance to negative effects of intoxication
        public double lifetimeDrinks; // accumulated drinks

        public Profile(UUID uuid) { this.uuid = uuid; }
    }

    public static final class DrinkDef {
        public final double abv;
        public final double volumeMl;

        public DrinkDef(double abv, double volumeMl) {
            this.abv = abv;
            this.volumeMl = volumeMl;
        }
    }

    /** Ugly Java getter/setters ew yuck :( **/
    public double getBac(UUID id) {
        Profile p = profiles.get(id);
        return (p == null) ? 0.0 : p.bac; // Sober if fetch fails
    }

    public double getTolerance(UUID id) {
        Profile p = profiles.get(id);
        return (p == null) ? 0.0 : p.tolerance; // Set lowest if fetch fails
    }

    /** Other Helpers **/
    // Registry keyed by CustomModelData *string* tags
    private static final Map<String, DrinkDef> DRINKS = Map.of(
            "beer", new DrinkDef(5.0, 350.0)
            // add more later: "wine", "vodka", etc
    );

    private void updateTolerance(Profile p) {
        p.tolerance = 1.0 - Math.exp(-p.lifetimeDrinks / TOLERANCE_SCALE);
    }

    private Profile getOrCreateProfile(UUID id) {
        return profiles.computeIfAbsent(id, Profile::new);
    }
}
