package me.Cyrendex.boozeNCruise;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BoozeNCruise extends JavaPlugin {

    // Constants
    private static final double BAC_SCALE_PER_GRAM = 0.0025;
    private static final double ABSORB_RATE_BASE_PER_SEC  = 0.001;
    private static final double ABSORB_RATE_SCALE_PER_SEC = 0.04;
    private static final double ELIM_RATE_PER_HOUR = 0.06;

    // Beer
    private static final double BEER_ABV = 5.0; // Percent based
    private static final int BEER_VOL = 350; // ml

    private final Map<UUID, Profile> profiles = new HashMap<>();
    private int secCounter = 0;

    private static class Profile {
        final UUID id;
        double bac; // Blood Alcohol Content
        double absorb; // Alcohol drunk but not metabolized
        Profile(UUID id) {
            this.id = id;
        }
    }
    @Override
    public void onEnable() {
        getLogger().info("Boozing and cruising!");
        // TODO: Implement alcohol ticker here

    }

    @Override
    public void onDisable() {
        profiles.clear();
    }

    private void startTicker() {
        // TODO: Alcohol absorption logic goes here!
    }
}
