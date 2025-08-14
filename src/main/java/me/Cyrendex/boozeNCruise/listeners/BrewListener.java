package me.Cyrendex.boozeNCruise.listeners;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, robust brewing listener .
 * Accepts wheat in the brewing stand but turns it into a netherwart behind the scenes.
 * Resourcepack needed for the illusion lol
 */
public final class BrewListener implements Listener {

    // Minimal handlers to accept WHEAT into slot 3 (ingredient)
    @EventHandler(ignoreCancelled = true)
    public void onBrewerClick(InventoryClickEvent e) {
        InventoryView view = e.getView();
        // if (view == null) return; GETVIEW IS TAGGED NOT NULL BUT I'M SKEPTIC I'LL DELETE THESE LATER
        Inventory top = view.getTopInventory();
        if (top == null || top.getType() != InventoryType.BREWING) return; // THIS IS ALSO TAGGED NOT NULL BUT I'LL ALSO DELETE IT LATER

        BrewerInventory brewer = (BrewerInventory) top;

        // Case A: clicking directly on the ingredient slot
        if (e.getClickedInventory() == top && e.getSlot() == 3) {
            handleIngredientPlacement(e, brewer);
            return;
        }

        // Case B: shift-clicking wheat from the player inventory into the brewer
        if (e.getClickedInventory() == e.getWhoClicked().getInventory()
                && e.isShiftClick()
                && isWheat(e.getCurrentItem())) {

            ItemStack ingredient = brewer.getIngredient();
            if (ingredient == null || ingredient.getType() == Material.AIR || isDisguisedWheat(ingredient)) {
                e.setCancelled(true); // we'll place one disguised wheat ourselves

                // Place one disguised wheat if slot empty
                if (ingredient == null || ingredient.getType() == Material.AIR) {
                    brewer.setIngredient(makeDisguisedWheat(1));
                    // remove one from the shift-clicked stack
                    ItemStack from = e.getCurrentItem();
                    if (from != null) {
                        int amount = from.getAmount();
                        if (amount <= 1) {
                            e.getWhoClicked().getInventory().setItem(e.getSlot(), null);
                        } else {
                            from.setAmount(amount - 1);
                        }
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrewerDrag(InventoryDragEvent e) {
        InventoryView view = e.getView();
        //if (view == null) return; // THESE ARE ALL NOT NULL LMFAO
        Inventory top = view.getTopInventory();
        if (top == null || top.getType() != InventoryType.BREWING) return; // THESE COMMENTS ARE FOR ME NOT YOU

        // If the drag includes the ingredient slot (index 3) and the dragged item is wheat,
        // cancel and place one disguised wheat.
        if (!e.getRawSlots().contains(3)) return;
        if (!isWheat(e.getOldCursor())) return;

        e.setCancelled(true);
        BrewerInventory brewer = (BrewerInventory) top;

        ItemStack ingredient = brewer.getIngredient();
        if (ingredient == null || ingredient.getType() == Material.AIR) {
            brewer.setIngredient(makeDisguisedWheat(1));
            // reduce one from the cursor
            ItemStack old = e.getOldCursor();
            if (old != null) { // THANKS SPIGOT FOR NOT NULL TAGS AND FUCK YOU JAVA FOR NULL POINTERS
                int left = old.getAmount() - 1;
                if (left <= 0) {
                    e.getWhoClicked().setItemOnCursor(null);
                } else {
                    old.setAmount(left);
                    e.getWhoClicked().setItemOnCursor(old);
                }
            }
        }
    }

    private void handleIngredientPlacement(InventoryClickEvent e, BrewerInventory brewer) {
        InventoryAction action = e.getAction();

        switch (action) {
            case PLACE_ALL:
            case PLACE_ONE:
            case PLACE_SOME:
            case SWAP_WITH_CURSOR: {
                ItemStack cursor = e.getCursor();
                if (isWheat(cursor)) {
                    e.setCancelled(true);
                    brewer.setIngredient(makeDisguisedWheat(1));

                    // Decrease the cursor by 1 (left/right both place one for the ingredient slot)
                    decrementCursor(e, 1);
                }
                break;
            }
            default:
                // ignore other actions
                break;
        }
    }

    private void decrementCursor(InventoryClickEvent e, int amount) {
        ItemStack cursor = e.getCursor();
        if (cursor == null) return;
        int left = cursor.getAmount() - amount;
        if (left <= 0) {
            e.getWhoClicked().setItemOnCursor(null);
        } else {
            cursor.setAmount(left);
            e.getWhoClicked().setItemOnCursor(cursor);
        }
    }

    private boolean isWheat(ItemStack it) {
        return it != null && it.getType() == Material.WHEAT && it.getAmount() > 0;
    }

    private boolean isDisguisedWheat(ItemStack it) {
        if (it == null || it.getType() != Material.NETHER_WART) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        if (cmd == null) return false;
        List<String> tags = cmd.getStrings();
        return tags != null && tags.contains("wheat");
    }

    private ItemStack makeDisguisedWheat(int amount) {
        ItemStack it = new ItemStack(Material.NETHER_WART, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();

        meta.setDisplayName("§eWheat");

        // Build CMD with "wheat"
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        List<String> tags = new ArrayList<>();
        tags.add("wheat");
        cmd.setStrings(tags);
        meta.setCustomModelDataComponent(cmd);

        it.setItemMeta(meta);
        return it;
    }

    // Brewing completion logic

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        BrewerInventory inv = e.getContents();

        // Only act when the ingredient is our disguised wheat (so we don't hijack real nether wart recipes)
        ItemStack ingredient = inv.getIngredient();
        if (!isDisguisedWheat(ingredient)) return;

        // Replace each water bottle result with a Beer item and clear empty slots.
        List<ItemStack> results = e.getResults(); // mutable btw, it's making my IDE lose it
        for (int slot = 0; slot < 3; slot++) {
            ItemStack in = inv.getItem(slot);
            if (isWaterPotion(in)) {
                results.set(slot, makeBeerPotion());
            } else {
                results.set(slot, new ItemStack(Material.AIR));
            }
        }
    }

    private boolean isWaterPotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof PotionMeta pm)) return false;
        PotionType base = pm.getBasePotionType();
        return base == PotionType.WATER;
    }

    /** Build a POTION item named “Beer”, base WATER, and CMD (custom model data -component-) strings containing "beer". */
    private ItemStack makeBeerPotion() {
        ItemStack it = new ItemStack(Material.POTION);
        ItemMeta meta = it.getItemMeta();

        if (meta instanceof PotionMeta pm) {
            pm.setBasePotionType(PotionType.WATER);
        }

        meta.setDisplayName("§eBeer");

        // String-based CMD (because my resourcepack does it like that)
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();

        List<String> existing = cmd.getStrings();
        List<String> tags = new ArrayList<>(existing);
        if (!tags.contains("beer")) tags.add("beer");

        cmd.setStrings(tags);
        meta.setCustomModelDataComponent(cmd);

        it.setItemMeta(meta);
        return it;
    }
}
