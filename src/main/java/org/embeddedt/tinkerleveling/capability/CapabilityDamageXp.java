package org.embeddedt.tinkerleveling.capability;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.embeddedt.tinkerleveling.TinkerLeveling;
import org.jetbrains.annotations.NotNull;

public final class CapabilityDamageXp implements EntityComponentInitializer {

  public static final ComponentKey<IDamageXp> CAPABILITY = ComponentRegistry.getOrCreate(new ResourceLocation(TinkerLeveling.MODID, "entityxp"), IDamageXp.class);

  @Override
  public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
    registry.registerFor(LivingEntity.class, CAPABILITY, entity -> new DamageXp());
  }
}
