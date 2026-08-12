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
 * 在生物生成时，将区域难度缩放的{@link AttributeModifier}应用到生物实体上。
 *
 * <p>每种属性类型使用固定的UUID，因此对同一实体的重复应用会替换先前的修改器而不会叠加。
 * 运算方式为{@code MULTIPLY_BASE}：</p>
 * <pre>最终值 = 基础值 * (1 + (乘数 - 1) * 强度)</pre>
 */
public final class AttributeApplier {

    private AttributeApplier() {} // 工具类

    // 固定UUID —— 每种属性一个，所有实体共享
    private static final UUID UUID_HEALTH        = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000001");
    private static final UUID UUID_MOVEMENT_SPEED = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000003");
    private static final UUID UUID_ARMOR         = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000004");
    private static final UUID UUID_ARMOR_TOUGHNESS = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000005");
    private static final UUID UUID_FOLLOW_RANGE  = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000006");
    private static final UUID UUID_KNOCKBACK_RES = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000007");
    private static final UUID UUID_REINFORCEMENTS = UUID.fromString("a1b2c3d4-1001-4000-8000-000000000008");

    /**
     * 属性目标描述符：包含目标属性、对应UUID、是否启用以及应用的强度。
     */
    private record AttrTarget(Attribute attribute, UUID uuid,
                               boolean enabled, double intensity) {}

    /** 延迟构建的排除生物类型注册名集合（已转为小写）。 */
    private static volatile Set<String> excludedMobIds = null;

    // ========== 公共API ==========

    /**
     * 将所有已启用的区域难度属性修改器应用到给定的实体上。
     *
     * @param entity     生成的生物（非玩家的生物实体）
     * @param multiplier 区域难度乘数（1.0 = 无变化）
     */
    public static void apply(LivingEntity entity, float multiplier) {
        if (!Config.spawnAttributesEnabled) return;
        if (Math.abs(multiplier - 1.0F) < 0.0001F) return;

        // 检查排除列表
        if (isExcluded(entity)) return;

        // 将有效乘数限制在安全范围内
        float effectiveMult = Math.max(0.1F, Math.min(10.0F, multiplier));

        // 从当前配置构建目标列表（快速，配置字段为基本类型）
        AttrTarget[] targets = buildTargets();
        boolean healthModified = false;

        for (AttrTarget target : targets) {
            if (!target.enabled || target.intensity <= 0.0) continue;

            AttributeInstance instance = entity.getAttribute(target.attribute);
            if (instance == null) continue;

            double amount = (effectiveMult - 1.0) * target.intensity;
            // 限制以防止属性变为负数
            if (amount <= -1.0) amount = -0.99;

            // 移除任何具有相同UUID的现有修改器（幂等操作）
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

        // 确保生成的生物在最大生命值缩放后拥有满血状态
        if (healthModified) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    /**
     * 从实体上移除所有区域难度修改器。
     * 对于不应受影响的生物类型很有用。
     */
    public static void removeAll(LivingEntity entity) {
        Attribute[] attrs = {
                Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED,
                Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.FOLLOW_RANGE,
                Attributes.KNOCKBACK_RESISTANCE, Attributes.SPAWN_REINFORCEMENTS_CHANCE
        };
        UUID[] uuids = {
                UUID_HEALTH, UUID_MOVEMENT_SPEED,
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

    // ========== 内部方法 ==========

    /**
     * 从当前Config值构建属性目标列表。
     */
    private static AttrTarget[] buildTargets() {
        return new AttrTarget[] {
                new AttrTarget(Attributes.MAX_HEALTH, UUID_HEALTH,
                        Config.attrHealthEnabled, Config.attrHealthIntensity),
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
     * 检查实体类型是否在排除列表中。
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
     * 清除排除生物缓存，使其在下次使用时从配置重新构建。
     */
    public static void refreshExcludedMobs() {
        excludedMobIds = null;
    }
}
