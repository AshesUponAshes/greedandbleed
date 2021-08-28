package com.github.teamfusion.greedandbleed.common.entity;

import net.minecraft.item.ItemStack;

public interface IHasMountArmor {

    ItemStack getArmor();

    boolean canWearArmor();

    boolean isArmor(ItemStack stack);
}
