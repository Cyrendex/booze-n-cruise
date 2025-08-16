package me.Cyrendex.boozeNCruise.listeners;

import me.Cyrendex.boozeNCruise.AlcoholManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class DrinkListener implements Listener {

    private final AlcoholManager mgr;

    public DrinkListener(AlcoholManager mgr) {
        this.mgr = mgr;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack stack = e.getItem();
        if (stack == null) return;

        // Identify drink from CustomModelData string tags
        AlcoholManager.DrinkDef def = AlcoholManager.lookupDrink(stack);
        if (def == null) return;

        e.setCancelled(true); // prevent vanilla use

        Player p = e.getPlayer();

        // Register intake (adds to absorb, lifetimeDrinks, tolerance)
        mgr.addDrink(p.getUniqueId(), def.volumeMl, def.abv);

        // Consume one item from the hand that triggered the event
        AlcoholManager.consumeOne(p, e.getHand(), stack);

        // Give back an empty bottle for potion-based drinks
        if (stack.getType() == Material.POTION) {
            p.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
        }

        // Slurp
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
    }
}
