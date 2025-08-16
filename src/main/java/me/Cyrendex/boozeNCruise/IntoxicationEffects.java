package me.Cyrendex.boozeNCruise;

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
}
