package ua.roma.roflrpg.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class ItemUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ItemUtil() {}

    public static ItemStack icon(Material mat, String nameMm, List<String> loreMm) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(MM.deserialize(nameMm));
        if (loreMm != null && !loreMm.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String s : loreMm) lore.add(MM.deserialize(s));
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack withStringTag(ItemStack it, NamespacedKey key, String value) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        it.setItemMeta(meta);
        return it;
    }

    public static String getStringTag(ItemStack it, NamespacedKey key) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
