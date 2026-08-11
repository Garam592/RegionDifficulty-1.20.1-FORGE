package com.garam.regiondifficulty;

import com.garam.regiondifficulty.event.DifficultyEventHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Arrays;
import java.util.List;

/**
 * 模组配置，包含区域难度倍率和效果层。
 * 配置值在加载/重载时从字符串列表解析为类型化字段。
 */
@Mod.EventBusSubscriber(modid = RegionDifficulty.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ========== 旧版配置项（保留用于兼容） ==========
    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("是否在通用设置时记录泥土方块日志")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("一个魔法数字")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("你希望魔法数字的介绍信息是什么")
            .define("magicNumberIntroduction", "The magic number is... ");

    // ========== 区域难度配置（A层） ==========

    private static final ForgeConfigSpec.BooleanValue ENABLE_REGIONAL_DIFFICULTY = BUILDER
            .comment("启用/禁用区域难度系统的总开关")
            .define("regionalDifficulty.enabled", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSION_MULTIPLIERS = BUILDER
            .comment("按维度的难度倍率。",
                    "格式：\"命名空间:路径=倍率\"",
                    "示例：\"minecraft:the_nether=2.0\"")
            .defineListAllowEmpty("regionalDifficulty.dimensionMultipliers",
                    defaultDimensionMultipliers(),
                    Config::validateMultiplierEntry);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_MULTIPLIERS = BUILDER
            .comment("按生物群系的难度倍率。",
                    "格式：\"命名空间:路径=倍率\"",
                    "示例：\"minecraft:desert=1.4\"")
            .defineListAllowEmpty("regionalDifficulty.biomeMultipliers",
                    defaultBiomeMultipliers(),
                    Config::validateMultiplierEntry);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_MULTIPLIERS = BUILDER
            .comment("按结构的难度倍率。",
                    "格式：\"命名空间:路径=倍率\"",
                    "示例：\"minecraft:bastion_remnant=1.8\"",
                    "当一个位置处于多个结构内时，使用最大的倍率值。")
            .defineListAllowEmpty("regionalDifficulty.structureMultipliers",
                    defaultStructureMultipliers(),
                    Config::validateMultiplierEntry);

    private static final ForgeConfigSpec.DoubleValue DEPTH_BASE_Y = BUILDER
            .comment("被视为\"地表\"的Y坐标。在此高度及以上，深度倍率等于 minMultiplier。")
            .defineInRange("regionalDifficulty.depth.baseY", 64.0, -64.0, 320.0);

    private static final ForgeConfigSpec.DoubleValue DEPTH_MAX_Y = BUILDER
            .comment("深度倍率达到最大值（最深点）的Y坐标。")
            .defineInRange("regionalDifficulty.depth.maxY", -64.0, -64.0, 320.0);

    private static final ForgeConfigSpec.DoubleValue DEPTH_MAX_MULTIPLIER = BUILDER
            .comment("在 maxY 及以下应用的最大深度倍率。")
            .defineInRange("regionalDifficulty.depth.maxMultiplier", 2.0, 0.5, 10.0);

    private static final ForgeConfigSpec.DoubleValue DEPTH_MIN_MULTIPLIER = BUILDER
            .comment("在 baseY 及以上（地表层级）应用的深度倍率。")
            .defineInRange("regionalDifficulty.depth.minMultiplier", 1.0, 0.1, 5.0);

    private static final ForgeConfigSpec.DoubleValue DEFAULT_MULTIPLIER = BUILDER
            .comment("未明确配置的生物群系/结构/维度的默认倍率。")
            .defineInRange("regionalDifficulty.defaultMultiplier", 1.0, 0.1, 10.0);

    // ========== 生成属性修改器配置（B层） ==========

    private static final ForgeConfigSpec.BooleanValue SPAWN_ATTR_ENABLED = BUILDER
            .comment("生成时属性缩放的总开关。",
                    "启用后，生物的属性（生命值、攻击力、速度等）将根据",
                    "其生成位置对应的区域难度倍率进行缩放。")
            .define("regionalDifficulty.spawnAttributes.enabled", true);

    // --- 各属性的开关和强度 ---
    private static final ForgeConfigSpec.BooleanValue ATTR_HEALTH_ENABLED = BUILDER
            .define("regionalDifficulty.spawnAttributes.maxHealth.enabled", true);
    private static final ForgeConfigSpec.DoubleValue ATTR_HEALTH_INTENSITY = BUILDER
            .comment("最大生命值缩放的强度。0.0 = 禁用，1.0 = 完整缩放。")
            .defineInRange("regionalDifficulty.spawnAttributes.maxHealth.intensity", 1.0, 0.0, 5.0);

    private static final ForgeConfigSpec.BooleanValue ATTR_ATTACK_ENABLED = BUILDER
            .define("regionalDifficulty.spawnAttributes.attackDamage.enabled", true);
    private static final ForgeConfigSpec.DoubleValue ATTR_ATTACK_INTENSITY = BUILDER
            .defineInRange("regionalDifficulty.spawnAttributes.attackDamage.intensity", 1.0, 0.0, 5.0);

    private static final ForgeConfigSpec.BooleanValue ATTR_SPEED_ENABLED = BUILDER
            .define("regionalDifficulty.spawnAttributes.movementSpeed.enabled", true);
    private static final ForgeConfigSpec.DoubleValue ATTR_SPEED_INTENSITY = BUILDER
            .defineInRange("regionalDifficulty.spawnAttributes.movementSpeed.intensity", 1.0, 0.0, 5.0);

    private static final ForgeConfigSpec.BooleanValue ATTR_ARMOR_ENABLED = BUILDER
            .define("regionalDifficulty.spawnAttributes.armor.enabled", false);
    private static final ForgeConfigSpec.DoubleValue ATTR_ARMOR_INTENSITY = BUILDER
            .defineInRange("regionalDifficulty.spawnAttributes.armor.intensity", 1.0, 0.0, 5.0);

    private static final ForgeConfigSpec.BooleanValue ATTR_ARMOR_TOUGH_ENABLED = BUILDER
            .define("regionalDifficulty.spawnAttributes.armorToughness.enabled", false);
    private static final ForgeConfigSpec.DoubleValue ATTR_ARMOR_TOUGH_INTENSITY = BUILDER
            .defineInRange("regionalDifficulty.spawnAttributes.armorToughness.intensity", 1.0, 0.0, 5.0);

    private static final ForgeConfigSpec.BooleanValue ATTR_FOLLOW_ENABLED = BUILDER
            .define("regionalDifficulty.spawnAttributes.followRange.enabled", true);
    private static final ForgeConfigSpec.DoubleValue ATTR_FOLLOW_INTENSITY = BUILDER
            .defineInRange("regionalDifficulty.spawnAttributes.followRange.intensity", 0.6, 0.0, 5.0);

    private static final ForgeConfigSpec.BooleanValue ATTR_KNOCKBACK_ENABLED = BUILDER
            .define("regionalDifficulty.spawnAttributes.knockbackResistance.enabled", true);
    private static final ForgeConfigSpec.DoubleValue ATTR_KNOCKBACK_INTENSITY = BUILDER
            .defineInRange("regionalDifficulty.spawnAttributes.knockbackResistance.intensity", 1.0, 0.0, 5.0);

    private static final ForgeConfigSpec.BooleanValue ATTR_REINFORCE_ENABLED = BUILDER
            .define("regionalDifficulty.spawnAttributes.spawnReinforcements.enabled", false);
    private static final ForgeConfigSpec.DoubleValue ATTR_REINFORCE_INTENSITY = BUILDER
            .defineInRange("regionalDifficulty.spawnAttributes.spawnReinforcements.intensity", 1.0, 0.0, 5.0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPAWN_ATTR_EXCLUDED = BUILDER
            .comment("排除在属性缩放之外的生物类型（注册名称）。",
                    "格式：\"命名空间:路径\"",
                    "示例：\"minecraft:ender_dragon\"")
            .defineListAllowEmpty("regionalDifficulty.spawnAttributes.excludedMobs",
                    List.of("minecraft:ender_dragon", "minecraft:wither"),
                    Config::validateIdEntry);

    // ========== 战斗缩放配置（C层） ==========

    private static final ForgeConfigSpec.BooleanValue COMBAT_ENABLED = BUILDER
            .comment("运行时战斗伤害缩放的总开关。",
                    "根据攻击者/目标所在位置的区域难度来缩放造成/受到的伤害。")
            .define("regionalDifficulty.combatScaling.enabled", true);

    private static final ForgeConfigSpec.BooleanValue COMBAT_TOPLAYER_ENABLED = BUILDER
            .define("regionalDifficulty.combatScaling.damageToPlayer.enabled", true);
    private static final ForgeConfigSpec.DoubleValue COMBAT_TOPLAYER_INTENSITY = BUILDER
            .defineInRange("regionalDifficulty.combatScaling.damageToPlayer.intensity", 1.0, 0.0, 5.0);
    private static final ForgeConfigSpec.DoubleValue COMBAT_TOPLAYER_CLAMPMIN = BUILDER
            .defineInRange("regionalDifficulty.combatScaling.damageToPlayer.clampMin", 0.2, 0.0, 10.0);
    private static final ForgeConfigSpec.DoubleValue COMBAT_TOPLAYER_CLAMPMAX = BUILDER
            .defineInRange("regionalDifficulty.combatScaling.damageToPlayer.clampMax", 5.0, 0.0, 10.0);

    private static final ForgeConfigSpec.BooleanValue COMBAT_BYPLAYER_ENABLED = BUILDER
            .define("regionalDifficulty.combatScaling.damageByPlayer.enabled", false);
    private static final ForgeConfigSpec.DoubleValue COMBAT_BYPLAYER_INTENSITY = BUILDER
            .defineInRange("regionalDifficulty.combatScaling.damageByPlayer.intensity", 0.5, 0.0, 5.0);
    private static final ForgeConfigSpec.DoubleValue COMBAT_BYPLAYER_CLAMPMIN = BUILDER
            .defineInRange("regionalDifficulty.combatScaling.damageByPlayer.clampMin", 0.2, 0.0, 10.0);
    private static final ForgeConfigSpec.DoubleValue COMBAT_BYPLAYER_CLAMPMAX = BUILDER
            .defineInRange("regionalDifficulty.combatScaling.damageByPlayer.clampMax", 2.0, 0.0, 10.0);

    // ========== 生成控制配置（D层） ==========

    private static final ForgeConfigSpec.BooleanValue SPAWNCTL_ENABLED = BUILDER
            .comment("按区域难度控制生物生成类型的总开关。",
                    "启用后，可将生物类型限制在特定的难度范围内。",
                    "这是一个实验性功能——默认禁用。")
            .define("regionalDifficulty.spawnControl.enabled", false);

    private static final ForgeConfigSpec.BooleanValue SPAWNCTL_DEFAULT_ALLOW = BUILDER
            .comment("是否允许没有明确规则的生物类型生成。",
                    "true = 未列出生物始终可以生成（安全的默认值）。",
                    "false = 未列出生物需要匹配的规则才能生成（限制性模式）。")
            .define("regionalDifficulty.spawnControl.defaultAllow", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPAWNCTL_RULES = BUILDER
            .comment("基于区域难度倍率的各生物类型生成规则。",
                    "格式：\"命名空间:路径=最小倍率,最大倍率\"",
                    "示例：\"minecraft:wither_skeleton=2.0,\"  （需要倍率 >= 2.0）",
                    "示例：\"minecraft:creeper=1.5,3.0\"       （需要 1.5 <= 倍率 <= 3.0）",
                    "最小倍率留空 = 0.0，最大倍率留空 = 无上限。")
            .defineListAllowEmpty("regionalDifficulty.spawnControl.rules",
                    List.of(),
                    Config::validateSpawnRuleEntry);

    // ========== 缓存配置 ==========

    private static final ForgeConfigSpec.BooleanValue CACHE_ENABLED = BUILDER
            .comment("是否按区块缓存区域难度计算结果。",
                    "建议保持启用以提高性能。")
            .define("regionalDifficulty.cache.enabled", true);

    private static final ForgeConfigSpec.IntValue CACHE_TTL_TICKS = BUILDER
            .comment("缓存难度值保持有效的时长（以游戏刻为单位）。",
                    "默认 6000 刻 = 5 分钟。值越小对配置变更响应越快。",
                    "值越大对含大量结构的配置性能越好。")
            .defineInRange("regionalDifficulty.cache.ttlTicks", 6000, 100, 72000);

    // ========== 构建配置规范 ==========
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ========== 解析后的公共静态字段 ==========

    // 旧版
    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;

    // A层：区域难度倍率
    public static boolean enableRegionalDifficulty;
    public static List<? extends String> dimensionMultipliers;
    public static List<? extends String> biomeMultipliers;
    public static List<? extends String> structureMultipliers;
    public static float depthBaseY;
    public static float depthMaxY;
    public static float depthMaxMultiplier;
    public static float depthMinMultiplier;
    public static float defaultMultiplier;

    // B层：生成属性缩放
    public static boolean spawnAttributesEnabled;
    public static boolean attrHealthEnabled;
    public static double attrHealthIntensity;
    public static boolean attrAttackEnabled;
    public static double attrAttackIntensity;
    public static boolean attrSpeedEnabled;
    public static double attrSpeedIntensity;
    public static boolean attrArmorEnabled;
    public static double attrArmorIntensity;
    public static boolean attrArmorToughEnabled;
    public static double attrArmorToughIntensity;
    public static boolean attrFollowEnabled;
    public static double attrFollowIntensity;
    public static boolean attrKnockbackEnabled;
    public static double attrKnockbackIntensity;
    public static boolean attrReinforceEnabled;
    public static double attrReinforceIntensity;
    public static List<? extends String> spawnAttrExcludedMobs;

    // C层：战斗缩放
    public static boolean combatScalingEnabled;
    public static boolean combatToPlayerEnabled;
    public static double combatToPlayerIntensity;
    public static double combatToPlayerClampMin;
    public static double combatToPlayerClampMax;
    public static boolean combatByPlayerEnabled;
    public static double combatByPlayerIntensity;
    public static double combatByPlayerClampMin;
    public static double combatByPlayerClampMax;

    // D层：生成控制
    public static boolean spawnControlEnabled;
    public static boolean spawnControlDefaultAllow;
    public static List<? extends String> spawnControlRules;

    // 缓存
    public static boolean cacheEnabled;
    public static int cacheTtlTicks;

    // ========== 验证 ==========

    @SuppressWarnings("deprecation")
    private static boolean validateMultiplierEntry(final Object obj) {
        if (!(obj instanceof final String entry)) return false;
        String[] parts = entry.split("=", 2);
        if (parts.length != 2) return false;
        try {
            String[] idParts = parts[0].split(":", 2);
            if (idParts.length != 2) return false;
            ResourceLocation resource = new ResourceLocation(idParts[0], idParts[1]);
            Float.parseFloat(parts[1]);
            return !resource.getNamespace().isEmpty() && !resource.getPath().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 验证简单的"命名空间:路径"条目（无值部分）。 */
    @SuppressWarnings("deprecation")
    private static boolean validateIdEntry(final Object obj) {
        if (!(obj instanceof final String entry)) return false;
        try {
            String[] idParts = entry.split(":", 2);
            if (idParts.length != 2) return false;
            ResourceLocation resource = new ResourceLocation(idParts[0], idParts[1]);
            return !resource.getNamespace().isEmpty() && !resource.getPath().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 验证生成规则条目："namespace:path=min,max" 或 "namespace:path=min," 或 "namespace:path=,max"。 */
    @SuppressWarnings("deprecation")
    private static boolean validateSpawnRuleEntry(final Object obj) {
        if (!(obj instanceof final String entry)) return false;
        String[] parts = entry.split("=", 2);
        if (parts.length != 2) return false;
        try {
            String[] idParts = parts[0].split(":", 2);
            if (idParts.length != 2) return false;
            ResourceLocation resource = new ResourceLocation(idParts[0], idParts[1]);
            if (resource.getNamespace().isEmpty() || resource.getPath().isEmpty()) return false;
            // 验证范围部分："min,max" 或 "min," 或 ",max" 或为空
            String range = parts[1].trim();
            if (range.isEmpty()) return false;
            String[] rangeParts = range.split(",", 2);
            if (rangeParts.length == 0) return false;
            if (rangeParts.length >= 1 && !rangeParts[0].isEmpty()) {
                Float.parseFloat(rangeParts[0]);
            }
            if (rangeParts.length == 2 && !rangeParts[1].isEmpty()) {
                Float.parseFloat(rangeParts[1]);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 默认值提供器 ==========

    private static List<String> defaultDimensionMultipliers() {
        return List.of(
                "minecraft:overworld=1.0",
                "minecraft:the_nether=2.0",
                "minecraft:the_end=3.0"
        );
    }

    private static List<String> defaultBiomeMultipliers() {
        return Arrays.asList(
                // 主世界——恶劣环境
                "minecraft:desert=1.4",
                "minecraft:badlands=1.3",
                "minecraft:eroded_badlands=1.5",
                "minecraft:ice_spikes=1.6",
                "minecraft:frozen_peaks=1.4",
                "minecraft:stony_peaks=1.3",
                "minecraft:jagged_peaks=1.3",
                "minecraft:snowy_slopes=1.2",
                "minecraft:snowy_plains=1.2",
                // 沼泽
                "minecraft:swamp=1.2",
                "minecraft:mangrove_swamp=1.3",
                // 森林
                "minecraft:dark_forest=1.5",
                "minecraft:old_growth_birch_forest=1.1",
                "minecraft:old_growth_pine_taiga=1.1",
                "minecraft:old_growth_spruce_taiga=1.1",
                // 地下
                "minecraft:deep_dark=2.5",
                "minecraft:dripstone_caves=1.4",
                "minecraft:lush_caves=0.8",
                "minecraft:deep_ocean=1.2",
                "minecraft:deep_cold_ocean=1.3",
                "minecraft:deep_frozen_ocean=1.5",
                "minecraft:deep_lukewarm_ocean=1.1",
                // 蘑菇岛——安全港湾
                "minecraft:mushroom_fields=0.6",
                // 下界生物群系
                "minecraft:nether_wastes=2.0",
                "minecraft:soul_sand_valley=2.3",
                "minecraft:crimson_forest=2.0",
                "minecraft:warped_forest=1.8",
                "minecraft:basalt_deltas=2.5",
                // 末地生物群系
                "minecraft:the_end=3.0",
                "minecraft:end_highlands=3.0",
                "minecraft:end_midlands=2.5",
                "minecraft:small_end_islands=2.0",
                "minecraft:end_barrens=2.5"
        );
    }

    private static List<String> defaultStructureMultipliers() {
        return Arrays.asList(
                // 主世界结构
                "minecraft:monument=1.5",
                "minecraft:pillager_outpost=1.4",
                "minecraft:swamp_hut=1.3",
                "minecraft:mansion=1.8",
                "minecraft:ancient_city=2.5",
                "minecraft:stronghold=1.6",
                "minecraft:jungle_pyramid=1.3",
                "minecraft:desert_pyramid=1.3",
                "minecraft:buried_treasure=1.0",
                "minecraft:shipwreck=1.1",
                "minecraft:ruined_portal=1.2",
                "minecraft:trail_ruins=1.1",
                // 村庄相对安全
                "minecraft:village_plains=0.8",
                "minecraft:village_desert=0.9",
                "minecraft:village_savanna=0.9",
                "minecraft:village_snowy=0.9",
                "minecraft:village_taiga=0.9",
                // 下界结构
                "minecraft:fortress=2.0",
                "minecraft:bastion_remnant=2.2",
                "minecraft:nether_fossil=1.5",
                // 末地结构
                "minecraft:end_city=3.0"
        );
    }

    // ========== 配置加载/重载处理器 ==========

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // 旧版
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // A层：区域难度倍率
        enableRegionalDifficulty = ENABLE_REGIONAL_DIFFICULTY.get();
        dimensionMultipliers = DIMENSION_MULTIPLIERS.get();
        biomeMultipliers = BIOME_MULTIPLIERS.get();
        structureMultipliers = STRUCTURE_MULTIPLIERS.get();
        depthBaseY = DEPTH_BASE_Y.get().floatValue();
        depthMaxY = DEPTH_MAX_Y.get().floatValue();
        depthMaxMultiplier = DEPTH_MAX_MULTIPLIER.get().floatValue();
        depthMinMultiplier = DEPTH_MIN_MULTIPLIER.get().floatValue();
        defaultMultiplier = DEFAULT_MULTIPLIER.get().floatValue();

        // B层：生成属性缩放
        spawnAttributesEnabled = SPAWN_ATTR_ENABLED.get();
        attrHealthEnabled = ATTR_HEALTH_ENABLED.get();
        attrHealthIntensity = ATTR_HEALTH_INTENSITY.get();
        attrAttackEnabled = ATTR_ATTACK_ENABLED.get();
        attrAttackIntensity = ATTR_ATTACK_INTENSITY.get();
        attrSpeedEnabled = ATTR_SPEED_ENABLED.get();
        attrSpeedIntensity = ATTR_SPEED_INTENSITY.get();
        attrArmorEnabled = ATTR_ARMOR_ENABLED.get();
        attrArmorIntensity = ATTR_ARMOR_INTENSITY.get();
        attrArmorToughEnabled = ATTR_ARMOR_TOUGH_ENABLED.get();
        attrArmorToughIntensity = ATTR_ARMOR_TOUGH_INTENSITY.get();
        attrFollowEnabled = ATTR_FOLLOW_ENABLED.get();
        attrFollowIntensity = ATTR_FOLLOW_INTENSITY.get();
        attrKnockbackEnabled = ATTR_KNOCKBACK_ENABLED.get();
        attrKnockbackIntensity = ATTR_KNOCKBACK_INTENSITY.get();
        attrReinforceEnabled = ATTR_REINFORCE_ENABLED.get();
        attrReinforceIntensity = ATTR_REINFORCE_INTENSITY.get();
        spawnAttrExcludedMobs = SPAWN_ATTR_EXCLUDED.get();

        // C层：战斗缩放
        combatScalingEnabled = COMBAT_ENABLED.get();
        combatToPlayerEnabled = COMBAT_TOPLAYER_ENABLED.get();
        combatToPlayerIntensity = COMBAT_TOPLAYER_INTENSITY.get();
        combatToPlayerClampMin = COMBAT_TOPLAYER_CLAMPMIN.get();
        combatToPlayerClampMax = COMBAT_TOPLAYER_CLAMPMAX.get();
        combatByPlayerEnabled = COMBAT_BYPLAYER_ENABLED.get();
        combatByPlayerIntensity = COMBAT_BYPLAYER_INTENSITY.get();
        combatByPlayerClampMin = COMBAT_BYPLAYER_CLAMPMIN.get();
        combatByPlayerClampMax = COMBAT_BYPLAYER_CLAMPMAX.get();

        // D层：生成控制
        spawnControlEnabled = SPAWNCTL_ENABLED.get();
        spawnControlDefaultAllow = SPAWNCTL_DEFAULT_ALLOW.get();
        spawnControlRules = SPAWNCTL_RULES.get();

        // 缓存
        cacheEnabled = CACHE_ENABLED.get();
        cacheTtlTicks = CACHE_TTL_TICKS.get();

        // 刷新缓存数据
        DifficultyEventHandler.refreshMultipliers();
    }
}
