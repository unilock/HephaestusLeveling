package org.embeddedt.tinkerleveling.capability;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public interface IDamageXp extends AutoSyncedComponent {
    void addDamageFromTool(float damage, UUID tool, Player player);

    float getDamageDealtByTool(UUID tool, Player player);

    void distributeXpToTools(LivingEntity deadEntity);
}
