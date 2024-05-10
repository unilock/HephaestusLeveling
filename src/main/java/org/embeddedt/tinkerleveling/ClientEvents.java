package org.embeddedt.tinkerleveling;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.awt.*;
import java.util.List;

public class ClientEvents implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> onTooltipEvent(stack, lines));
    }

    static void onTooltipEvent(ItemStack stack, List<Component> tooltips) {
        if(!stack.is(TinkerTags.Items.MODIFIABLE))
            return;
        ToolStack tool = ToolStack.copyFrom(stack);
        if(tool.getModifierLevel(TinkerLeveling.LEVELING_MODIFIER.getId()) > 0) {
            ModDataNBT levelData = tool.getPersistentData();
            int xp = levelData.getInt(ModToolLeveling.XP_KEY);
            int level = levelData.getInt(ModToolLeveling.LEVEL_KEY);
            tooltips.add(1, Component.translatable("tooltip.tinkerleveling.xp").append(": ").append(Component.literal(String.format("%d / %d", xp, ModToolLeveling.getXpForLevelup(level, stack.getItem())))));
            tooltips.add(1, getLevelTooltip(level));
        }
    }

    private static Component getLevelTooltip(int level) {
        return Component.translatable("tooltip.tinkerleveling.level").append(": ").append(getLevelString(level));
    }

    public static Component getLevelString(int level) {
        return Component.literal(getRawLevelString(level)).withStyle(style -> style.withColor(getLevelColor(level)));
    }

    private static String getRawLevelString(int level) {
        if(level <= 0) {
            return "";
        }

        // try a basic translated string
        if(TranslationHelper.canTranslate("tooltip.tinkerleveling.level." + level)) {
            return Component.translatable("tooltip.tinkerleveling.level." + level).getString();
        }

        // ok. try to find a modulo
        int i = 1;
        while(TranslationHelper.canTranslate("tooltip.tinkerleveling.level." + i)) {
            i++;
        }

        // get the modulo'd string
        String str = Component.translatable("tooltip.level." + (level % i)).getString();
        // and add +s!
        for(int j = level / i; j > 0; j--) {
            str += '+';
        }

        return str;
    }

    private static int getLevelColor(int level) {
        float hue = (0.277777f * level);
        hue = hue - (int) hue;
        return Color.HSBtoRGB(hue, 0.75f, 0.8f);
    }
}
