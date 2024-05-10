package org.embeddedt.tinkerleveling;

import net.minecraft.network.chat.Component;

public class TranslationHelper {
    public static boolean canTranslate(String key) {
        return !key.equals(Component.translatable(key).getString());
    }
}
