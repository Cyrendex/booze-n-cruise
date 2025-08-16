package me.Cyrendex.boozeNCruise;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        // TODO: Do some math to incorporate tolerance into this calculation and return stage
    }

    private void applyStage(Player p, Stage stage) {
        clearManaged(p); // Wipe current effects

        switch (stage) {
            case SOBER -> { /* Nothing here */ }
            case S1 -> {    // Slight positives
                // TODO: Add speed 1 and a bit of nausea
            }
            case S2 -> { // Mediocre benefits
                // TODO: Add strength and hunger
            }
            case S3 -> { // Uh oh stage
                // TODO: Slightly fucked up, looking at the bathroom mirror
            }
            case S4 -> { // Remove all positive benefits
                // TODO: Mouth salivating, deep breaths (if you haven't noticed this is all flavor text lol)
            }
            case S5 -> { // Heavy side-effects
                // TODO: Cripple the user, passed out on the bathroom floor
            }
        }
    }

    private void clearManaged(Player p) {
        // TODO: remove effects intoxication handles, we don't wanna clear all effects on a player.
    }
}
