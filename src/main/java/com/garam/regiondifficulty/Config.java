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
 * Mod configuration including regional difficulty multipliers and effect layers.
 * Config values are parsed from string lists into typed fields on load/reload.
 */
@Mod.EventBusSubscriber(modid = RegionDifficulty.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ========== Legacy config entries (kept for compatibility) ==========
    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // ========== Regional Difficulty Configuration (Layer A) ==========

    private static final ForgeConfigSpec.BooleanValue ENABLE_REGIONAL_DIFFICULTY = BUILDER
            .comment("Master switch to enable/disable the regional difficulty overhaul")
            .define("regionalDifficulty.enabled", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> DIMENSION_MULTIPLIERS = BUILDER
            .comment("Per-dimension difficulty multipliers.",
                    "Format: \"namespace:path=multiplier\"",
                    "Example: \"minecraft:the_nether=2.0\"")
            .defineListAllowEmpty("regionalDifficulty.dimensionMultipliers",
                    defaultDimensionMultipliers(),
                    Config::validateMultiplierEntry);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BIOME_MULTIPLIERS = BUILDER
            .comment("Per-biome difficulty multipliers.",
                    "Format: \"namespace:path=multiplier\"",
                    "Example: \"minecraft:desert=1.4\"")
            .defineListAllowEmpty("regionalDifficulty.biomeMultipliers",
                    defaultBiomeMultipliers(),
                    Config::validateMultiplierEntry);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_MULTIPLIERS = BUILDER
            .comment("Per-structure difficulty multipliers.",
                    "Format: \"namespace:path=multiplier\"",
                    "Example: \"minecraft:bastion_remnant=1.8\"",
                    "When a position is inside multiple structures, the HIGHEST multiplier is used.")
            .defineListAllowEmpty("regionalDifficulty.structureMultipliers",
                    defaultStructureMultipliers(),
                    Config::validateMultiplierEntry);

    private static final ForgeConfigSpec.DoubleValue DEPTH_BASE_Y = BUILDER
            .comment("Y-level considered 'surface'. At or above this level, the depth multiplier equals minMultiplier.")
            .defineInRange("regionalDifficulty.depth.baseY", 64.0, -64.0, 320.0);

    private static final ForgeConfigSpec.DoubleValue DEPTH_MAX_Y = BUILDER
            .comment("Y-level at which the depth multiplier reaches its maximum value (deepest point).")
            .defineInRange("regionalDifficulty.depth.maxY", -64.0, -64.0, 320.0);

    private static final ForgeConfigSpec.DoubleValue DEPTH_MAX_MULTIPLIER = BUILDER
            .comment("Maximum depth multiplier applied at or below maxY.")
            .defineInRange("regionalDifficulty.depth.maxMultiplier", 2.0, 0.5, 10.0);

    private static final ForgeConfigSpec.DoubleValue DEPTH_MIN_MULTIPLIER = BUILDER
            .comment("Depth multiplier applied at or above baseY (surface level).")
            .defineInRange("regionalDifficulty.depth.minMultiplier", 1.0, 0.1, 5.0);

    private static final ForgeConfigSpec.DoubleValue DEFAULT_MULTIPLIER = BUILDER
            .comment("Default multiplier for biomes/structures/dimensions not explicitly configured.")
            .defineInRange("regionalDifficulty.defaultMultiplier", 1.0, 0.1, 10.0);

    // ========== Spawn Attribute Modifier Configuration (Layer B) ==========

    private static final ForgeConfigSpec.BooleanValue SPAWN_ATTR_ENABLED = BUILDER
            .comment("Master switch for spawn-time attribute scaling.",
                    "When enabled, mob attributes (health, damage, speed, etc.) are scaled",
                    "by the regional difficulty multiplier at their spawn position.")
            .define("regionalDifficulty.spawnAttributes.enabled", true);

    // --- Per-attribute toggles and intensities ---
    private static final ForgeConfigSpec.BooleanValue ATTR_HEALTH_ENABLED = BUILDER
            .define("regionalDifficulty.spawnAttributes.maxHealth.enabled", true);
    private static final ForgeConfigSpec.DoubleValue ATTR_HEALTH_INTENSITY = BUILDER
            .comment("Intensity for max health scaling. 0.0 = disabled, 1.0 = full scaling.")
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
            .comment("Mob types (registry names) excluded from attribute scaling.",
                    "Format: \"namespace:path\"",
                    "Example: \"minecraft:ender_dragon\"")
            .defineListAllowEmpty("regionalDifficulty.spawnAttributes.excludedMobs",
                    List.of("minecraft:ender_dragon", "minecraft:wither"),
                    Config::validateIdEntry);

    // ========== Combat Scaling Configuration (Layer C) ==========

    private static final ForgeConfigSpec.BooleanValue COMBAT_ENABLED = BUILDER
            .comment("Master switch for runtime combat damage scaling.",
                    "Scales damage dealt/received based on regional difficulty at the attacker/target position.")
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

    // ========== Spawn Control Configuration (Layer D) ==========

    private static final ForgeConfigSpec.BooleanValue SPAWNCTL_ENABLED = BUILDER
            .comment("Master switch for spawn type gating by regional difficulty.",
                    "When enabled, mob types can be restricted to specific difficulty ranges.",
                    "This is an experimental feature — disabled by default.")
            .define("regionalDifficulty.spawnControl.enabled", false);

    private static final ForgeConfigSpec.BooleanValue SPAWNCTL_DEFAULT_ALLOW = BUILDER
            .comment("Whether mob types without an explicit rule are allowed to spawn.",
                    "true = unlisted mobs always spawn (safe default).",
                    "false = unlisted mobs require a matching rule (restrictive).")
            .define("regionalDifficulty.spawnControl.defaultAllow", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPAWNCTL_RULES = BUILDER
            .comment("Per-mob-type spawn rules based on regional difficulty multiplier.",
                    "Format: \"namespace:path=minMultiplier,maxMultiplier\"",
                    "Example: \"minecraft:wither_skeleton=2.0,\"  (requires multiplier >= 2.0)",
                    "Example: \"minecraft:creeper=1.5,3.0\"       (requires 1.5 <= multiplier <= 3.0)",
                    "Empty min = 0.0, empty max = unbounded.")
            .defineListAllowEmpty("regionalDifficulty.spawnControl.rules",
                    List.of(),
                    Config::validateSpawnRuleEntry);

    // ========== Cache Configuration ==========

    private static final ForgeConfigSpec.BooleanValue CACHE_ENABLED = BUILDER
            .comment("Whether to cache regional difficulty calculations per chunk.",
                    "Recommended to keep enabled for performance.")
            .define("regionalDifficulty.cache.enabled", true);

    private static final ForgeConfigSpec.IntValue CACHE_TTL_TICKS = BUILDER
            .comment("How long (in ticks) a cached difficulty value remains valid.",
                    "Default 6000 ticks = 5 minutes. Lower = more responsive to config changes.",
                    "Higher = better performance for structure-heavy configs.")
            .defineInRange("regionalDifficulty.cache.ttlTicks", 6000, 100, 72000);

    // ========== Build the spec ==========
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ========== Parsed public static fields ==========

    // Legacy
    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;

    // Layer A: Regional Difficulty multipliers
    public static boolean enableRegionalDifficulty;
    public static List<? extends String> dimensionMultipliers;
    public static List<? extends String> biomeMultipliers;
    public static List<? extends String> structureMultipliers;
    public static float depthBaseY;
    public static float depthMaxY;
    public static float depthMaxMultiplier;
    public static float depthMinMultiplier;
    public static float defaultMultiplier;

    // Layer B: Spawn attribute scaling
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

    // Layer C: Combat scaling
    public static boolean combatScalingEnabled;
    public static boolean combatToPlayerEnabled;
    public static double combatToPlayerIntensity;
    public static double combatToPlayerClampMin;
    public static double combatToPlayerClampMax;
    public static boolean combatByPlayerEnabled;
    public static double combatByPlayerIntensity;
    public static double combatByPlayerClampMin;
    public static double combatByPlayerClampMax;

    // Layer D: Spawn control
    public static boolean spawnControlEnabled;
    public static boolean spawnControlDefaultAllow;
    public static List<? extends String> spawnControlRules;

    // Cache
    public static boolean cacheEnabled;
    public static int cacheTtlTicks;

    // ========== Validation ==========

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

    /** Validates a simple "namespace:path" entry (no value part). */
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

    /** Validates a spawn rule entry: "namespace:path=min,max" or "namespace:path=min," or "namespace:path=,max". */
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
            // Validate the range part: "min,max" or "min," or ",max" or empty
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

    // ========== Default value providers ==========

    private static List<String> defaultDimensionMultipliers() {
        return List.of(
                "minecraft:overworld=1.0",
                "minecraft:the_nether=2.0",
                "minecraft:the_end=3.0"
        );
    }

    private static List<String> defaultBiomeMultipliers() {
        return Arrays.asList(
                // Overworld — harsh environments
                "minecraft:desert=1.4",
                "minecraft:badlands=1.3",
                "minecraft:eroded_badlands=1.5",
                "minecraft:ice_spikes=1.6",
                "minecraft:frozen_peaks=1.4",
                "minecraft:stony_peaks=1.3",
                "minecraft:jagged_peaks=1.3",
                "minecraft:snowy_slopes=1.2",
                "minecraft:snowy_plains=1.2",
                // Swamps
                "minecraft:swamp=1.2",
                "minecraft:mangrove_swamp=1.3",
                // Forests
                "minecraft:dark_forest=1.5",
                "minecraft:old_growth_birch_forest=1.1",
                "minecraft:old_growth_pine_taiga=1.1",
                "minecraft:old_growth_spruce_taiga=1.1",
                // Underground
                "minecraft:deep_dark=2.5",
                "minecraft:dripstone_caves=1.4",
                "minecraft:lush_caves=0.8",
                "minecraft:deep_ocean=1.2",
                "minecraft:deep_cold_ocean=1.3",
                "minecraft:deep_frozen_ocean=1.5",
                "minecraft:deep_lukewarm_ocean=1.1",
                // Mushroom — safe haven
                "minecraft:mushroom_fields=0.6",
                // Nether biomes
                "minecraft:nether_wastes=2.0",
                "minecraft:soul_sand_valley=2.3",
                "minecraft:crimson_forest=2.0",
                "minecraft:warped_forest=1.8",
                "minecraft:basalt_deltas=2.5",
                // End biomes
                "minecraft:the_end=3.0",
                "minecraft:end_highlands=3.0",
                "minecraft:end_midlands=2.5",
                "minecraft:small_end_islands=2.0",
                "minecraft:end_barrens=2.5"
        );
    }

    private static List<String> defaultStructureMultipliers() {
        return Arrays.asList(
                // Overworld structures
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
                // Villages are relatively safe
                "minecraft:village_plains=0.8",
                "minecraft:village_desert=0.9",
                "minecraft:village_savanna=0.9",
                "minecraft:village_snowy=0.9",
                "minecraft:village_taiga=0.9",
                // Nether structures
                "minecraft:fortress=2.0",
                "minecraft:bastion_remnant=2.2",
                "minecraft:nether_fossil=1.5",
                // End structures
                "minecraft:end_city=3.0"
        );
    }

    // ========== Config load/reload handler ==========

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Legacy
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // Layer A: Regional difficulty multipliers
        enableRegionalDifficulty = ENABLE_REGIONAL_DIFFICULTY.get();
        dimensionMultipliers = DIMENSION_MULTIPLIERS.get();
        biomeMultipliers = BIOME_MULTIPLIERS.get();
        structureMultipliers = STRUCTURE_MULTIPLIERS.get();
        depthBaseY = DEPTH_BASE_Y.get().floatValue();
        depthMaxY = DEPTH_MAX_Y.get().floatValue();
        depthMaxMultiplier = DEPTH_MAX_MULTIPLIER.get().floatValue();
        depthMinMultiplier = DEPTH_MIN_MULTIPLIER.get().floatValue();
        defaultMultiplier = DEFAULT_MULTIPLIER.get().floatValue();

        // Layer B: Spawn attribute scaling
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

        // Layer C: Combat scaling
        combatScalingEnabled = COMBAT_ENABLED.get();
        combatToPlayerEnabled = COMBAT_TOPLAYER_ENABLED.get();
        combatToPlayerIntensity = COMBAT_TOPLAYER_INTENSITY.get();
        combatToPlayerClampMin = COMBAT_TOPLAYER_CLAMPMIN.get();
        combatToPlayerClampMax = COMBAT_TOPLAYER_CLAMPMAX.get();
        combatByPlayerEnabled = COMBAT_BYPLAYER_ENABLED.get();
        combatByPlayerIntensity = COMBAT_BYPLAYER_INTENSITY.get();
        combatByPlayerClampMin = COMBAT_BYPLAYER_CLAMPMIN.get();
        combatByPlayerClampMax = COMBAT_BYPLAYER_CLAMPMAX.get();

        // Layer D: Spawn control
        spawnControlEnabled = SPAWNCTL_ENABLED.get();
        spawnControlDefaultAllow = SPAWNCTL_DEFAULT_ALLOW.get();
        spawnControlRules = SPAWNCTL_RULES.get();

        // Cache
        cacheEnabled = CACHE_ENABLED.get();
        cacheTtlTicks = CACHE_TTL_TICKS.get();

        // Refresh cached data
        DifficultyEventHandler.refreshMultipliers();
    }
}
