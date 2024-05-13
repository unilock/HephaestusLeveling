package org.embeddedt.tinkerleveling.capability;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.embeddedt.tinkerleveling.ModToolLeveling;
import org.embeddedt.tinkerleveling.TinkerLeveling;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DamageXp implements IDamageXp {
    private static final String TAG_PLAYER_UUID = "player_uuid";
    private static final String TAG_DAMAGE_LIST = "damage_data";
    private static final String TAG_ITEM = "item";
    private static final String TAG_DAMAGE = "damage";

    private Map<UUID, Map<UUID, Float>> playerToDamageMap = new HashMap<>();

    @Override
    public void addDamageFromTool(float damage, UUID tool, Player player) {
        Map<UUID, Float> damageMap = playerToDamageMap.getOrDefault(player.getUUID(), new HashMap<>());

        damage += getDamageDealtByTool(tool, player);

        damageMap.put(tool, damage);
        playerToDamageMap.put(player.getUUID(), damageMap);
    }

    @Override
    public float getDamageDealtByTool(UUID tool, Player player) {
        Map<UUID, Float> damageMap = playerToDamageMap.getOrDefault(player.getUUID(), new HashMap<>());

        return damageMap.entrySet().stream()
                .filter(itemStackFloatEntry -> tool.equals(itemStackFloatEntry.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(0f);
    }

    @Override
    public void distributeXpToTools(LivingEntity deadEntity) {
        playerToDamageMap.forEach((uuid, itemStackFloatMap) -> distributeXpForPlayer(deadEntity.level(), uuid, itemStackFloatMap));
    }

    private void distributeXpForPlayer(Level world, UUID playerUuid, Map<UUID, Float> damageMap) {
        Optional.ofNullable(world.getPlayerByUUID(playerUuid))
                .ifPresent(
                        player -> damageMap.forEach(
                                (itemStack, damage) -> distributeXpToPlayerForTool(player, itemStack, damage)
                        )
                );
    }

    private void distributeXpToPlayerForTool(Player player, UUID toolUUID, float damage) {
        if(toolUUID != null) {
            List<SingleSlotStorage<ItemVariant>> slots = PlayerInventoryStorage.of(player).getSlots();
            // check for identity. should work in most cases because the entity was killed without loading/unloading
            for(int i = 0; i < slots.size(); i++) {
                if(slots.get(i).getResource().getItem() instanceof IModifiable) {
                    ToolStack tool = ToolStack.from(slots.get(i).getResource().toStack((int) slots.get(i).getAmount()));
                    if(tool.getPersistentData().contains(ModToolLeveling.UUID_KEY, Tag.TAG_INT_ARRAY)) {
                        if(NbtUtils.loadUUID(tool.getPersistentData().get(ModToolLeveling.UUID_KEY)).equals(toolUUID)) {
                            TinkerLeveling.LEVELING_MODIFIER.get().addXp(tool, Math.round(damage), player);
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        ListTag playerList = new ListTag();

        playerToDamageMap.forEach((uuid, itemStackFloatMap) -> playerList.add(convertPlayerDataToTag(uuid, itemStackFloatMap)));

        tag.put("playerList", playerList);
    }

    private CompoundTag convertPlayerDataToTag(UUID uuid, Map<UUID, Float> itemStackFloatMap) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_PLAYER_UUID, uuid);

        ListTag damageTag = new ListTag();

        itemStackFloatMap.forEach((itemStack, damage) -> damageTag.add(convertItemDamageDataToTag(itemStack, damage)));

        tag.put(TAG_DAMAGE_LIST, damageTag);
        return tag;
    }

    private CompoundTag convertItemDamageDataToTag(UUID stack, Float damage) {
        CompoundTag tag = new CompoundTag();

        tag.put(TAG_ITEM, NbtUtils.createUUID(stack));
        tag.putFloat(TAG_DAMAGE, damage);

        return tag;
    }


    @Override
    public void readFromNbt(CompoundTag nbt) {
        ListTag playerList = nbt.getList("playerList", Tag.TAG_LIST);

        playerToDamageMap = new HashMap<>();
        for(int i = 0; i < playerList.size(); i++) {
            CompoundTag tag = playerList.getCompound(i);

            UUID playerUuid = tag.getUUID(TAG_PLAYER_UUID);
            ListTag data = tag.getList(TAG_DAMAGE_LIST, 10);

            Map<UUID, Float> damageMap = new HashMap<>();

            for(int j = 0; j < data.size(); j++) {
                deserializeTagToMapEntry(damageMap, data.getCompound(j));
            }

            playerToDamageMap.put(playerUuid, damageMap);
        }
    }

    private void deserializeTagToMapEntry(Map<UUID, Float> damageMap, CompoundTag tag) {
        UUID stack = NbtUtils.loadUUID(tag.get(TAG_ITEM));
        damageMap.put(stack, tag.getFloat(TAG_DAMAGE));
    }
}
