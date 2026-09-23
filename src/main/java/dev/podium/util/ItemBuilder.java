package dev.podium.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.stream.Collectors;

/** A small chainable ItemStack builder for menu items. */
public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (name != null) meta.displayName(TextUtil.item(name));
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (lore != null) meta.lore(lore.stream().map(TextUtil::item).collect(Collectors.toList()));
        return this;
    }

    /** Stashes a raw string on the item so a click handler doesn't have to parse display text. */
    public ItemBuilder tag(NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }
}
