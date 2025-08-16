package me.Cyrendex.boozeNCruise;

import me.Cyrendex.boozeNCruise.listeners.BrewListener;
import me.Cyrendex.boozeNCruise.listeners.DrinkListener;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class BoozeNCruise extends JavaPlugin {

    private AlcoholManager alcohols;
    private IntoxicationEffects effects;
    private int taskId = -1;

    @Override public void onEnable() {
        this.alcohols = new AlcoholManager();
        this.effects = new IntoxicationEffects(alcohols);

        // Register listeners (no commands for now)
        getServer().getPluginManager().registerEvents(new DrinkListener(alcohols), this);
        getServer().getPluginManager().registerEvents(new BrewListener(), this);

        // 1s ticker: metabolism + HUD
        this.taskId = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                alcohols.tickOneSecond(p.getUniqueId());
                String hud = alcohols.buildHud(p.getUniqueId());
                if (hud != null) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(hud)); // This is apparently deprecated, but it works so it will be left for the time being!

                effects.tick(p);
            }
        }, 20L, 20L).getTaskId();
    }

    @Override public void onDisable() {
        if (taskId != -1) {
            getServer().getScheduler().cancelTask(taskId);
        }

        effects.clearAll(getServer().getOnlinePlayers());
    }
}
