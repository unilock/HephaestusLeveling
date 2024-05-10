package org.embeddedt.tinkerleveling;

import io.github.fabricators_of_create.porting_lib.config.ModConfigSpec;
import net.minecraft.world.item.Item;

public class TinkerConfig {
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();
    public static ModConfigSpec SERVER_CONFIG;

    public static final ModConfigSpec.IntValue maximumLevels;
    public static final ModConfigSpec.IntValue defaultBaseXP;
    public static final ModConfigSpec.DoubleValue levelMultiplier;
    public static final ModConfigSpec.BooleanValue allowArmorExploits;

    static {
        maximumLevels = SERVER_BUILDER.comment("Maximum achievable levels. If set to 0 or lower there is no upper limit").defineInRange("maximumLevels", 0, 0, Integer.MAX_VALUE);
        levelMultiplier = SERVER_BUILDER.comment("How much the XP cost will multiply per level (minimum 2).").defineInRange("levelMultiplier", 2, 2, Double.MAX_VALUE);
        defaultBaseXP = SERVER_BUILDER.comment("Base XP used when no more specific entry is present for the tool").defineInRange("defaultBaseXP", 500, 1, Integer.MAX_VALUE);
        allowArmorExploits = SERVER_BUILDER.comment("Allow any form of damage to the player to count towards armor XP, not just mobs").define("allowArmorExploits", false);

        SERVER_CONFIG = SERVER_BUILDER.build();
    }

    public static int getBaseXpForTool(Item item) {
        return defaultBaseXP.get();
    }

    public static boolean canLevelUp(int currentLevel) {
        return maximumLevels.get() <= 0 || maximumLevels.get() >= currentLevel;
    }
}
