package org.embeddedt.tinkerleveling;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import org.embeddedt.tinkerleveling.capability.CapabilityDamageXp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.common.SoundUtils;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.TinkerHooks;
import slimeknights.tconstruct.library.modifiers.hook.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ElytraFlightModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.PlantHarvestModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ProjectileLaunchModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ShearsModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.build.ModifierRemovalHook;
import slimeknights.tconstruct.library.modifiers.hook.build.RawDataModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.build.VolatileDataModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.DamageTakenModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.mining.BlockBreakModifierHook;
import slimeknights.tconstruct.library.modifiers.util.ModifierHookMap;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.context.ToolHarvestContext;
import slimeknights.tconstruct.library.tools.context.ToolRebuildContext;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.item.ModifiableLauncherItem;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.NamespacedNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.utils.RestrictedCompoundTag;
import slimeknights.tconstruct.tools.TinkerModifiers;

import java.util.UUID;
import java.util.WeakHashMap;

public class ModToolLeveling extends Modifier implements BlockBreakModifierHook, DamageTakenModifierHook, ElytraFlightModifierHook, ShearsModifierHook, MeleeHitModifierHook, PlantHarvestModifierHook, ProjectileHitModifierHook, ProjectileLaunchModifierHook, ModifierRemovalHook, RawDataModifierHook, VolatileDataModifierHook {

    public static final ResourceLocation XP_KEY = new ResourceLocation(TinkerLeveling.MODID, "xp");
    public static final ResourceLocation BONUS_MODIFIERS_KEY = new ResourceLocation(TinkerLeveling.MODID, "bonus_modifiers");
    public static final ResourceLocation LEVEL_KEY = new ResourceLocation(TinkerLeveling.MODID, "level");
    public static final ResourceLocation UUID_KEY = new ResourceLocation(TinkerLeveling.MODID, "uuid");

    private static final WeakHashMap<Projectile, Pair<ItemStack, Integer>> LAUNCH_INFO_MAP = new WeakHashMap<>();

    @Override
    public boolean shouldDisplay(boolean advanced) {
        return false;
    }

    public ModToolLeveling() {
        super();
    }

    @Override
    protected void registerHooks(ModifierHookMap.Builder hookBuilder) {
        hookBuilder.addHook(this, TinkerHooks.BLOCK_BREAK, TinkerHooks.DAMAGE_TAKEN, TinkerHooks.ELYTRA_FLIGHT, TinkerHooks.SHEAR_ENTITY, TinkerHooks.MELEE_HIT, TinkerHooks.PLANT_HARVEST, TinkerHooks.PROJECTILE_HIT, TinkerHooks.PROJECTILE_LAUNCH, TinkerHooks.REMOVE, TinkerHooks.RAW_DATA, TinkerHooks.VOLATILE_DATA);
    }

    @Override
    public void addVolatileData(@NotNull ToolRebuildContext context, @NotNull ModifierEntry modifier, @NotNull ModDataNBT volatileData) {
        IModDataView persistentData = context.getPersistentData();
        int numExtraModifiers = persistentData.getInt(BONUS_MODIFIERS_KEY);
        int numAbilitySlots = (numExtraModifiers / 2);
        volatileData.addSlots(SlotType.ABILITY, numAbilitySlots);
        volatileData.addSlots(SlotType.UPGRADE, numExtraModifiers - numAbilitySlots);
    }

    @Override
    public Component onRemoved(@NotNull IToolStackView tool, @NotNull Modifier modifier) {
        tool.getPersistentData().remove(XP_KEY);
        tool.getPersistentData().remove(BONUS_MODIFIERS_KEY);
        tool.getPersistentData().remove(LEVEL_KEY);
        tool.getPersistentData().remove(UUID_KEY);
        return null;
    }

    @Override
    public void addRawData(@NotNull IToolStackView tool, @NotNull ModifierEntry modifier, @NotNull RestrictedCompoundTag tag) {
        if(!tool.getPersistentData().contains(UUID_KEY, Tag.TAG_INT_ARRAY)) {
            tool.getPersistentData().put(UUID_KEY, NbtUtils.createUUID(UUID.randomUUID()));
        }
        if(tool.getPersistentData().getInt(LEVEL_KEY) <= 0) {
            tool.getPersistentData().putInt(LEVEL_KEY, 1);
        }
    }

    @Override
    public void removeRawData(@NotNull IToolStackView tool, @NotNull Modifier modifier, @NotNull RestrictedCompoundTag tag) {
        tool.getPersistentData().remove(UUID_KEY);
        tool.getPersistentData().remove(LEVEL_KEY);
    }

    public static int getXpForLevelup(int level, Item item) {
        if(level <= 1) {
            return TinkerConfig.getBaseXpForTool(item);
        }
        return (int) ((double) getXpForLevelup(level - 1, item) * TinkerConfig.levelMultiplier.get());
    }

    public void addXp(IToolStackView tool, int amount, Player player) {
        ModDataNBT levelData = tool.getPersistentData();

        // is max level?
        if(!TinkerConfig.canLevelUp(levelData.getInt(LEVEL_KEY))) {
            return;
        }

        levelData.putInt(XP_KEY, levelData.getInt(XP_KEY) + amount);

        int xpForLevelup = getXpForLevelup(levelData.getInt(LEVEL_KEY), tool.getItem());

        boolean leveledUp = false;
        // check for levelup
        if(levelData.getInt(XP_KEY) >= xpForLevelup) {
            levelData.putInt(XP_KEY, levelData.getInt(XP_KEY) - xpForLevelup);
            levelData.putInt(LEVEL_KEY, levelData.getInt(LEVEL_KEY) + 1);
            levelData.putInt(BONUS_MODIFIERS_KEY, levelData.getInt(BONUS_MODIFIERS_KEY) + 1);
            leveledUp = true;
        }

        if(leveledUp) {
            if(!player.level().isClientSide) {
                // TODO maybe only play for that player again
                SoundUtils.playSoundForAll(player, TinkerLeveling.SOUND_LEVELUP, 1f, 1f);
                ClientHelper.sendLevelUpMessage(levelData.getInt(LEVEL_KEY), (ServerPlayer) player);
            }
            /* FIXME: no other way of doing this that I see */
            if(tool instanceof ToolStack) {
                ((ToolStack)tool).rebuildStats();
            } else {
                throw new IllegalStateException("Unable to figure out how to rebuild this tool!");
            }
        }
    }

    /* Handlers */

    @Override
    public void afterBlockBreak(@NotNull IToolStackView tool, @NotNull ModifierEntry modifier, @NotNull ToolHarvestContext context) {
        if(context.isEffective() && context.getPlayer() != null) {
            addXp(tool, 1, context.getPlayer());
        }
    }

    @Override
    public void onDamageTaken(@NotNull IToolStackView tool, @NotNull ModifierEntry modifier, @NotNull EquipmentContext context, @NotNull EquipmentSlot slotType, @NotNull DamageSource source, float amount, boolean isDirectDamage) {
        if(!(context.getEntity() instanceof Player player))
            return;
        boolean wasMobDamage = source.getEntity() != player && source.getEntity() instanceof LivingEntity;
        ModifierEntry blockingModifier = tool.getModifiers().getEntry(TinkerModifiers.blocking.getId());
        boolean isLevelableItem;
        if(slotType.getType() == EquipmentSlot.Type.ARMOR && (wasMobDamage || TinkerConfig.allowArmorExploits.get()))
            isLevelableItem = true;
        else if(player.isBlocking() && blockingModifier != null && ModifierUtil.getActiveModifier(tool) == blockingModifier)
            isLevelableItem = true;
        else
            isLevelableItem = false;
        if(isDirectDamage
                && isLevelableItem
                && !player.level().isClientSide) {
            addXp(tool, 1, player);
        }
    }

    @Override
    public boolean elytraFlightTick(@NotNull IToolStackView tool, @NotNull ModifierEntry modifier, @NotNull LivingEntity entity, int flightTicks) {
        // Grant 1 XP every 5 seconds
        if(flightTicks > 0 && (flightTicks % 100) == 0 && entity instanceof Player player) {
            addXp(tool, 1, player);
        }
        return false;
    }

    @Override
    public void afterMeleeHit(@NotNull IToolStackView tool, @NotNull ModifierEntry modifier, @NotNull ToolAttackContext context, float damageDealt) {
        LivingEntity target = context.getLivingTarget();
        if(target == null) {
            return;
        }
        if(!context.getTarget().level().isClientSide && context.getPlayerAttacker() != null) {
            // if we killed it the event for distributing xp was already fired and we just do it manually here
            if(!context.getTarget().isAlive()) {
                addXp(tool, Math.round(damageDealt), context.getPlayerAttacker());
            }
            else {
                CapabilityDamageXp.CAPABILITY.maybeGet(target).ifPresent(cap -> {
                    cap.addDamageFromTool(damageDealt, NbtUtils.loadUUID(tool.getPersistentData().get(UUID_KEY)), context.getPlayerAttacker());
                });
            }
        }
    }

    @Override
    public void afterShearEntity(@NotNull IToolStackView tool, @NotNull ModifierEntry level, @NotNull Player player, @NotNull Entity entity, boolean isTarget) {
        addXp(tool, 1, player);
    }


    @Override
    public void afterHarvest(@NotNull IToolStackView tool, @NotNull ModifierEntry modifier, @NotNull UseOnContext context, @NotNull ServerLevel world, @NotNull BlockState state, @NotNull BlockPos pos) {
        if(context.getPlayer() != null) {
            addXp(tool, 1, context.getPlayer());
        }
    }

    @Override
    public void onProjectileLaunch(@NotNull IToolStackView iToolStackView, @NotNull ModifierEntry modifierEntry, @NotNull LivingEntity livingEntity, @NotNull Projectile projectile, @Nullable AbstractArrow abstractArrow, @NotNull NamespacedNBT namespacedNBT, boolean b) {
        if(livingEntity instanceof Player player) {
            ItemStack stack = player.getUseItem();
            if(stack.isEmpty()) {
                ItemStack mainHandStack = player.getItemInHand(InteractionHand.MAIN_HAND);
                ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);
                stack = mainHandStack.getItem() == iToolStackView.getItem() ? mainHandStack : offHandStack;
            }
            if(stack.getItem() == iToolStackView.getItem() && stack.getItem() instanceof ModifiableLauncherItem) {
                float drawspeed = ConditionalStatModifierHook.getModifiedStat(iToolStackView, player, ToolStats.DRAW_SPEED) / 20.0f;
                int totalDrawTime = player.getTicksUsingItem();
                int fullDrawTime = (int)Math.ceil(1.0f / drawspeed);
                if((abstractArrow != null && abstractArrow.shotFromCrossbow()) || totalDrawTime >= fullDrawTime) {
                    synchronized (LAUNCH_INFO_MAP) {
                        LAUNCH_INFO_MAP.put(projectile, Pair.of(stack, fullDrawTime));
                    }
                }
            }
        }
    }

    @Override
    public boolean onProjectileHitEntity(@NotNull ModifierNBT modifiers, @NotNull NamespacedNBT persistentData, @NotNull ModifierEntry modifier, @NotNull Projectile projectile, @NotNull EntityHitResult hit, @Nullable LivingEntity attacker, @Nullable LivingEntity target) {
        if(projectile.getDeltaMovement().length() > 0.4f && attacker instanceof Player player) {
            Pair<ItemStack, Integer> launchInfo;
            synchronized (LAUNCH_INFO_MAP) {
                launchInfo = LAUNCH_INFO_MAP.remove(projectile);
            }
            if(launchInfo != null) {
                int drawTime = launchInfo.getSecond();
                if(drawTime > 0) {
                    ItemStack stack = launchInfo.getFirst();
                    double drawTimeInSeconds = drawTime / 20f;
                    // we award 5 xp per 1s draw time
                    int xp = Mth.ceil((5d * drawTimeInSeconds));
                    this.addXp(ToolStack.from(stack), xp, player);
                }
            }
        }
        return false;
    }
}
