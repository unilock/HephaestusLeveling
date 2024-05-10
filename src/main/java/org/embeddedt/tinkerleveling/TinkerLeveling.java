package org.embeddedt.tinkerleveling;

import io.github.fabricators_of_create.porting_lib.config.ConfigRegistry;
import io.github.fabricators_of_create.porting_lib.config.ConfigType;
import io.github.fabricators_of_create.porting_lib.entity.events.PlayerTickEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.tinkerleveling.capability.CapabilityDamageXp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.util.ModifierDeferredRegister;
import slimeknights.tconstruct.library.modifiers.util.StaticModifier;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

public class TinkerLeveling implements ModInitializer {
    public static final String MODID = "tinkerleveling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    protected static final ModifierDeferredRegister MODIFIERS = ModifierDeferredRegister.create(TinkerLeveling.MODID);
    public static StaticModifier<ModToolLeveling> LEVELING_MODIFIER = MODIFIERS.register("leveling", ModToolLeveling::new);

    public static TinkerLeveling instance;

    @Override
    public void onInitialize() {
        this.registerModifiers();
        ConfigRegistry.registerConfig(MODID, ConfigType.SERVER, TinkerConfig.SERVER_CONFIG);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> onDeath(entity));
        PlayerTickEvents.END.register((player) -> onPlayerTick(player));

        this.registerSoundEvent();

        instance = this;
    }

    public void registerModifiers() {
        MODIFIERS.register();
    }

    public void onDeath(LivingEntity entity) {
        if(!entity.level().isClientSide) {
            CapabilityDamageXp.CAPABILITY.maybeGet(entity).ifPresent(cap -> {
                cap.distributeXpToTools(entity);
            });
        }
    }

    private void processInvList(NonNullList<ItemStack> items) {
        for(ItemStack itemStack : items) {
            if(itemStack.is(TinkerTags.Items.MODIFIABLE)) {
                if(ModifierUtil.getModifierLevel(itemStack, LEVELING_MODIFIER.getId()) == 0) {
                    ToolStack tool = ToolStack.from(itemStack);
                    tool.addModifier(LEVELING_MODIFIER.getId(), 1);
                }
            }
        }
    }

    public void onPlayerTick(Player player) {
        /* TODO: replace with tool building event if/when Tinkers adds one */
        if(!player.level().isClientSide) {
            Inventory inventory = player.getInventory();
            processInvList(inventory.items);
            processInvList(inventory.armor);
            processInvList(inventory.offhand);
        }
    }

    public static SoundEvent SOUND_LEVELUP = sound("levelup");

    private static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(TinkerLeveling.MODID, name));
    }

    public void registerSoundEvent() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, SOUND_LEVELUP.getLocation(), SOUND_LEVELUP);
    }
}
