package me.Cyrendex.boozeNCruise;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

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
    private static final boolean KILL_OVER_45 = true;

    // long effect duration (apply on stage change only)
    private static final int EFFECT_SECONDS = 600; // 10 minutes
    // guard cadence (reapply only if missing/nerfed)
    private static final int GUARD_PERIOD_SECONDS = 5;

    private final AlcoholManager mgr;
    private final Map<UUID, Stage> lastStage = new HashMap<>();
    private final Map<UUID, Integer> guardTick = new HashMap<>();

    public static final class EffectSpec {
        public final PotionEffectType type;
        public final int amplifier;

        public EffectSpec(PotionEffectType type, int amplifier) {
            this.type = type;
            this.amplifier = amplifier;
        }
    }

    private Map<Stage, List<EffectSpec>> stageEffects = defaultEffects();
    private Set<PotionEffectType> managedTypes = rebuildManagedTypes(stageEffects);

    public IntoxicationEffects(AlcoholManager mgr) {
        this.mgr = mgr;
    }

    /**  We'll replace the effects table at runtime from configs or smth **/
    public void setStageEffects(Map<Stage, List<EffectSpec>> newEffects) {
        // Defensive copy
        EnumMap<Stage, List<EffectSpec>> copy = new EnumMap<>(Stage.class);
        for (Stage s : Stage.values()) {
            List<EffectSpec> list = newEffects.getOrDefault(s, List.of());
            copy.put(s, List.copyOf(list));
        }
        this.stageEffects = copy;
        this.managedTypes = rebuildManagedTypes(copy);
    }

    private static Set<PotionEffectType> rebuildManagedTypes(Map<Stage, List<EffectSpec>> table) {
        return table.values().stream()
                .flatMap(List::stream)
                .map(effectSpec -> effectSpec.type)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Map<Stage, List<EffectSpec>> defaultEffects() {
        return Map.of(
                Stage.SOBER, List.of(),
                Stage.S1, List.of(
                        new EffectSpec(PotionEffectType.SPEED, 0)
                        //new EffectSpec(PotionEffectType.NAUSEA, 0)
                ),
                Stage.S2, List.of(
                        new EffectSpec(PotionEffectType.SPEED, 1),
                        //new EffectSpec(PotionEffectType.NAUSEA, 0),
                        new EffectSpec(PotionEffectType.STRENGTH, 0),
                        new EffectSpec(PotionEffectType.HUNGER, 0)
                ),
                Stage.S3, List.of(
                        new EffectSpec(PotionEffectType.SLOWNESS, 0),
                        new EffectSpec(PotionEffectType.NAUSEA, 0),
                        new EffectSpec(PotionEffectType.STRENGTH, 0),
                        new EffectSpec(PotionEffectType.HUNGER, 0)
                ),
                Stage.S4, List.of(
                        new EffectSpec(PotionEffectType.SLOWNESS, 1),
                        new EffectSpec(PotionEffectType.NAUSEA, 3),
                        new EffectSpec(PotionEffectType.WEAKNESS, 1),
                        new EffectSpec(PotionEffectType.HUNGER, 0),
                        new EffectSpec(PotionEffectType.MINING_FATIGUE, 0)
                ),
                Stage.S5, List.of(
                        new EffectSpec(PotionEffectType.SLOWNESS, 4),
                        new EffectSpec(PotionEffectType.NAUSEA, 5),
                        new EffectSpec(PotionEffectType.WEAKNESS, 2),
                        new EffectSpec(PotionEffectType.HUNGER, 1),
                        new EffectSpec(PotionEffectType.MINING_FATIGUE, 1),
                        new EffectSpec(PotionEffectType.POISON, 0),
                        new EffectSpec(PotionEffectType.BLINDNESS, 0)
                )
        );
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

        if  (prev != stage) { // Can't reapply every cycle or nausea flickers
            applyStage(p, stage);
            lastStage.put(id, stage);
        } else { // Since we give long durations due to the previous issue, we check if player removed status effects
            maybeGuard(p, stage);
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

    private static double clamp01(double value) { return value < 0 ? 0: (value > 1 ? 1 : value); }

    private void applyStage(Player p, Stage stage) {
        clearManaged(p); // Wipe current effects
        for (EffectSpec ef : stageEffects.getOrDefault(stage, List.of())) {
            add(p, ef.type, ef.amplifier);
        }
    }

    private void maybeGuard(Player p, Stage stage) {
        int t = guardTick.merge(p.getUniqueId(), 1, Integer::sum);
        if (t % GUARD_PERIOD_SECONDS != 0) return; // We only run this every N seconds

        for (EffectSpec ef : stageEffects.getOrDefault(stage, List.of())) {
            ensurePresent(p, ef.type, ef.amplifier);
        }
    }

    private void ensurePresent(Player p, PotionEffectType type, int amp) {
        PotionEffect current = p.getPotionEffect(type);
        if (current == null || current.getAmplifier() < amp) {
            add(p, type, amp); // Re-add the nerfed/removed effect
        }
    }
    // Clear effects for many players
    public void clearAll(Iterable<? extends org.bukkit.entity.Player> players) {
        for (org.bukkit.entity.Player p : players) clearManaged(p);
        lastStage.clear();
    }
    private void add(Player p, PotionEffectType type, int amplifier) {
        PotionEffect effect = new PotionEffect(type, EFFECT_SECONDS  * 20, amplifier, true, false, true);
        p.addPotionEffect(effect);
    }

    private void clearManaged(Player p) {
        for (PotionEffectType type : managedTypes) {
            p.removePotionEffect(type);
        }
    }
}