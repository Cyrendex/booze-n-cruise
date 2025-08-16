package me.Cyrendex.boozeNCruise;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static me.Cyrendex.boozeNCruise.AlcoholManager.clamp01;

public class IntoxicationEffects {

    public enum Stage {
        SOBER,
        S1,
        S2,
        S3,
        S4,
        S5
    }

    // Base thresholds for BAC (tolerance value will shift them right)
    private static final double[] BASES = {0.00, 0.02, 0.06, 0.12, 0.20, 0.30};
    private static final double TOL_SLOPE = 0.75; // Tolerance amplifier

    // Instant death over fatal BAC (default will be false)
    private static final boolean KILL_OVER_45 = false;

    private final AlcoholManager mgr;
    private final Map<UUID, Stage> lastStage = new HashMap<>();

    public IntoxicationEffects(AlcoholManager mgr) {
        this.mgr = mgr;
    }

    public void tick(Player p) {
        final UUID id = p.getUniqueId();

        double bac = mgr.getBac(id);
        double tol = mgr.getTolerance(id);

        if (KILL_OVER_45 && bac > 0.45) {
            p.setHealth(0.0);
            return;
        }

        Stage stage = computeStage(bac, tol);
        Stage prev = lastStage.get(id);

        if  (prev != stage) {
            applyStage(p, stage);
            lastStage.put(id, stage);
        }
    }

    private Stage computeStage(double bac, double tolerance) {
        double shift = 1.0 + TOL_SLOPE * clamp01(tolerance);
        double t1 = BASES[1] * shift;
        double t2  = BASES[2] * shift;
        double t3 = BASES[3] * shift;
        double t4 = BASES[4] * shift;
        double t5 = BASES[5] * shift;

        if (bac < t1) return Stage.SOBER;
        if (bac < t2) return Stage.S1;
        if (bac < t3) return Stage.S2;
        if (bac < t4) return Stage.S3;
        if (bac < t5) return Stage.S4;
        return Stage.S5;
    }

    private void applyStage(Player p, Stage stage) {
        clearManaged(p); // Wipe current effects

        switch (stage) {
            case SOBER -> { /* Nothing here */ }
            case S1 -> {    // Slight positives
                add(p, PotionEffectType.SPEED, 1, 8);
                add(p, PotionEffectType.NAUSEA, 0, 6);
            }
            case S2 -> { // Mediocre benefits
                add(p, PotionEffectType.SPEED, 1, 8);
                add(p, PotionEffectType.NAUSEA, 0, 6);
                add(p, PotionEffectType.STRENGTH, 1, 8);
                add(p, PotionEffectType.HUNGER, 0, 8);
            }
            case S3 -> { // Uh oh stage
                add(p, PotionEffectType.SLOWNESS, 1, 8);
                add(p, PotionEffectType.NAUSEA, 1, 8);
                add(p, PotionEffectType.STRENGTH, 1, 8);
                add(p, PotionEffectType.HUNGER, 0, 8);
            }
            case S4 -> { // Remove all positive benefits
                add(p, PotionEffectType.SLOWNESS, 1, 8);
                add(p, PotionEffectType.NAUSEA, 1, 8);
                add(p, PotionEffectType.WEAKNESS, 1, 8);
                add(p, PotionEffectType.HUNGER, 1, 8);
            }
            case S5 -> { // Heavy consequences (thanks intelliJ for grammar policing?)
                add(p, PotionEffectType.SLOWNESS, 4, 8);
                add(p, PotionEffectType.WEAKNESS, 2, 8);
                add(p, PotionEffectType.BLINDNESS, 0, 8);
                add(p, PotionEffectType.POISON, 1, 4);
            }
        }
    }

    private void add(Player p, PotionEffectType type, int amplifier, int seconds) {
        p.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, true, false));
    }

    private void clearManaged(Player p) {
        // Remove the effects we manage
        PotionEffectType[] managed = {
                PotionEffectType.SPEED, PotionEffectType.NAUSEA,
                PotionEffectType.STRENGTH, PotionEffectType.HUNGER,
                PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
                PotionEffectType.BLINDNESS, PotionEffectType.POISON
        };

        for (PotionEffectType type : managed) p.removePotionEffect(type);
    }
}
