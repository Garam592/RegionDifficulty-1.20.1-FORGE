package com.garam.regiondifficulty.attribute;

import com.garam.regiondifficulty.Config;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Applies region-difficulty-scaled {@link AttributeModifier}s to living entities at spawn.
 *
 * <p>Each attribute type uses a fixed UUID so re-application on the same entity replaces
 * the previous modifier rather than stacking. The operation is {@code MULTIPLY_BASE}:</p>
 * <pre>finalValue = baseValue * (1 + (multiplier - 1) * intensity)</pre>
 */
public final class AttributeApplier {

    private AttributeApplier() {} // utility class

    // Fixed UUIDs — one per attribute, shared across all entities
    private static final UUID UUID_HEALTH        = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000001");
    private static final UUID UUID_ATTACK_DAMAGE = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000002");
    private static final UUID UUID_MOVEMENT_SPEED = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000003");
    private static final UUID UUID_ARMOR         = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000004");
    private static final UUID UUID_ARMOR_TOUGHNESS = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000005");
    private static final UUID UUID_FOLLOW_RANGE  = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000006");
    private static final UUID UUID_KNOCKBACK_RES = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000007");
    private static final UUID UUID_REINFORCEMENTS = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000008");

    /**
     * Descriptor for one attribute target: which attribute, which UUID, whether enabled,
     * and what intensity to apply.
     */
    private record AttrTarget(Attribute attribute, UUID uuid,
                               boolean enabled, double intensity) {}

    /** Lazily-built set of excluded mob type registry names (lowercased). */
    private static volatile Set<String> excludedMobIds = null;

    // ========== Public API ==========

    /**
     * Apply all enabled region-difficulty attribute modifiers to the given entity.
     *
     * @param entity     the spawned mob (non-player living entity)
     * @param multiplier the regional difficulty multiplier (1.0 = neutral)
     */
    public static void apply(LivingEntity entity, float multiplier) {
        if (!Config.spawnAttributesEnabled) return;
        if (Math.abs(multiplier - 1.0F) < 0.0001F) return;

        // Check exclusion list
        if (isExcluded(entity)) return;

        // Effective multiplier clamped to safe range
        float effectiveMult = Math.max(0.1F, Math.min(10.0F, multiplier));

        // Build target list from current config (fast, config fields are primitives)
        AttrTarget[] targets = buildTargets();
        boolean healthModified = false;

        for (AttrTarget target : targets) {
            if (!target.enabled || target.intensity <= 0.0) continue;

            AttributeInstance instance = entity.getAttribute(target.attribute);
            if (instance == null) continue;

            double amount = (effectiveMult - 1.0) * target.intensity;
            // Clamp to prevent attributes going negative
            if (amount <= -1.0) amount = -0.99;

            // Remove any existing modifier with the same UUID (idempotent)
            instance.removeModifier(target.uuid);

            AttributeModifier modifier = new AttributeModifier(
                    target.uuid,
                    "region_difficulty",
                    amount,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );
            instance.addPermanentModifier(modifier);

            if (target.attribute == Attributes.MAX_HEALTH) {
                healthModified = true;
            }
        }

        // Ensure spawned mob has full health after max health scaling
        if (healthModified) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    /**
     * Remove all region-difficulty modifiers from an entity.
     * Useful for mob types that should not be affected.
     */
    public static void removeAll(LivingEntity entity) {
        Attribute[] attrs = {
                Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED,
                Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.FOLLOW_RANGE,
                Attributes.KNOCKBACK_RESISTANCE, Attributes.SPAWN_REINFORCEMENTS_CHANCE
        };
        UUID[] uuids = {
                UUID_HEALTH, UUID_ATTACK_DAMAGE, UUID_MOVEMENT_SPEED,
                UUID_ARMOR, UUID_ARMOR_TOUGHNESS, UUID_FOLLOW_RANGE,
                UUID_KNOCKBACK_RES, UUID_REINFORCEMENTS
        };
        for (int i = 0; i < attrs.length; i++) {
            AttributeInstance instance = entity.getAttribute(attrs[i]);
            if (instance != null) {
                instance.removeModifier(uuids[i]);
            }
        }
    }

    // ========== Internal ==========

    /**
     * Build the list of attribute targets from current Config values.
     */
    private static AttrTarget[] buildTargets() {
        return new AttrTarget[] {
                new AttrTarget(Attributes.MAX_HEALTH, UUID_HEALTH,
                        Config.attrHealthEnabled, Config.attrHealthIntensity),
                new AttrTarget(Attributes.ATTACK_DAMAGE, UUID_ATTACK_DAMAGE,
                        Config.attrAttackEnabled, Config.attrAttackIntensity),
                new AttrTarget(Attributes.MOVEMENT_SPEED, UUID_MOVEMENT_SPEED,
                        Config.attrSpeedEnabled, Config.attrSpeedIntensity),
                new AttrTarget(Attributes.ARMOR, UUID_ARMOR,
                        Config.attrArmorEnabled, Config.attrArmorIntensity),
                new AttrTarget(Attributes.ARMOR_TOUGHNESS, UUID_ARMOR_TOUGHNESS,
                        Config.attrArmorToughEnabled, Config.attrArmorToughIntensity),
                new AttrTarget(Attributes.FOLLOW_RANGE, UUID_FOLLOW_RANGE,
                        Config.attrFollowEnabled, Config.attrFollowIntensity),
                new AttrTarget(Attributes.KNOCKBACK_RESISTANCE, UUID_KNOCKBACK_RES,
                        Config.attrKnockbackEnabled, Config.attrKnockbackIntensity),
                new AttrTarget(Attributes.SPAWN_REINFORCEMENTS_CHANCE, UUID_REINFORCEMENTS,
                        Config.attrReinforceEnabled, Config.attrReinforceIntensity),
        };
    }

    /**
     * Check whether an entity type is in the exclusion list.
     */
    private static boolean isExcluded(LivingEntity entity) {
        Set<String> excluded = excludedMobIds;
        if (excluded == null) {
            excluded = new HashSet<>();
            for (String entry : Config.spawnAttrExcludedMobs) {
                excluded.add(entry.toLowerCase());
            }
            excludedMobIds = excluded;
        }
        String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).toString();
        return excluded.contains(typeId);
    }

    /**
     * Clear the excluded mobs cache so it's rebuilt from config on next use.
     */
    public static void refreshExcludedMobs() {
        excludedMobIds = null;
    }
}
