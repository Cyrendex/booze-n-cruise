package me.Cyrendex.boozeNCruise.listeners;

import me.Cyrendex.boozeNCruise.AlcoholManager;
import org.bukkit.Material;
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

        ItemStack hand = e.getItem();
        String drinkId = mgr.matchDrink(hand);
        if (drinkId == null) return;

        // Cancel vanilla use (bottle drink behavior)
        e.setCancelled(true);

        // consume the used item (respect whichever hand)
        AlcoholManager.consumeOne(e.getPlayer(), e.getHand(), hand);

        // give an empty container only if this drink requests it (I think this doesn't work rn lol)
        AlcoholManager.DrinkDef def = mgr.getDrink(drinkId);
        if (def != null && def.giveEmptyBottle) {
            e.getPlayer().getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
        }

// apply intake
        mgr.ingest(e.getPlayer(), drinkId);
    }
}