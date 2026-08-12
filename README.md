# Region Difficulty

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.0-orange)](https://files.minecraftforge.net/)

根据玩家所在位置（维度、生物群系、深度、结构）动态调整区域难度，影响生物属性和战斗伤害。

## 功能

- **区域难度系统**：每个位置的难度由 维度 × 生物群系 × 结构 × 深度 四个因子乘积决定
- **生成属性缩放（B 层）**：高难度区域的生物拥有更高的生命值、移动速度、护甲等
- **战斗伤害缩放（C 层）**：高难度区域的生物造成更高伤害，也更耐打
- **区域难度指示器**：手持指示器物品即可在 HUD 上查看当前位置的完整难度分解
- **命令支持**：`/regiondifficulty check` 查看详细调试信息，`/regiondifficulty reload` 热重载配置
- **维度专属深度曲线**：每个维度可独立配置深度-难度映射

## 依赖

| Mod                                                                                     | 说明                         |
|-----------------------------------------------------------------------------------------|----------------------------|
| [More Attributes](https://www.curseforge.com/minecraft/mc-mods/more-attributes-1618942) | 提供扩展属性（暴击、闪避、生命窃取等），用于未来扩展 |

## 快速开始

1. 安装 Forge 47.4.0+ 和上述依赖
2. 将 mod jar 放入 `mods` 文件夹
3. 启动游戏，配置文件生成在 `config/region_difficulty-common.toml`
4. 从创造模式 "区域难度" 标签页取出 **区域难度指示器**，手持查看难度

## 难度计算

```
综合倍率 = 维度倍率 × 生物群系倍率 × 结构倍率 × 深度倍率
```

| 因子   | 说明          | 示例                          |
|------|-------------|-----------------------------|
| 维度   | 按维度固定倍率     | 主世界 1.0, 下界 2.0, 末地 3.0     |
| 生物群系 | 按群系固定倍率     | 沙漠 1.4, 黑暗森林 1.5, 深暗之域 2.5  |
| 结构   | 位于结构内时取最高倍率 | 堡垒遗迹 2.2, 远古城市 2.5, 末地城 3.0 |
| 深度   | Y 坐标线性插值    | 地表 ×1.0 → 最深 ×2.0（按维度独立配置）  |

## 命令

```
/regiondifficulty check    — 显示当前位置的难度倍率分解
/regiondifficulty reload   — 热重载配置文件
```

需要操作员权限（等级 2）。

## 配置

配置文件：`config/region_difficulty-common.toml`

### 区域难度倍率

```toml
[regionalDifficulty]
enabled = true
defaultMultiplier = 1.0

# 维度倍率：格式 "modid:dimension=倍率"
dimensionMultipliers = ["minecraft:overworld=1.0", "minecraft:the_nether=2.0", "minecraft:the_end=3.0"]

# 生物群系倍率
biomeMultipliers = ["minecraft:desert=1.4", "minecraft:deep_dark=2.5", ...]

# 结构倍率
structureMultipliers = ["minecraft:bastion_remnant=2.2", "minecraft:ancient_city=2.5", ...]
```

### 深度曲线（全局默认 + 按维度覆盖）

```toml
# 全局默认：Y=64 → ×1.0，Y=-64 → ×2.0
depth.baseY = 64.0
depth.maxY = -64.0
depth.maxMultiplier = 2.0
depth.minMultiplier = 1.0

# 按维度覆盖：格式 "dim=baseY,maxY,maxMultiplier,minMultiplier"
depthOverrides = [
    "minecraft:the_nether=100.0,0.0,2.5,1.0",
    "minecraft:the_end=60.0,-64.0,3.0,1.0"
]
```

### 生成属性缩放（B 层）

```toml
[regionalDifficulty.spawnAttributes]
enabled = true

# 每项属性可独立开关和调整强度（0.0=禁用，1.0=满缩放）
maxHealth.enabled = true
maxHealth.intensity = 1.0

movementSpeed.enabled = true
movementSpeed.intensity = 1.0

# 注意：ATTACK_DAMAGE 已废弃，伤害缩放由 combatScaling 层处理

# 排除列表：这些生物不受属性缩放影响
excludedMobs = ["minecraft:ender_dragon", "minecraft:wither"]
```

### 战斗伤害缩放（C 层）

```toml
[regionalDifficulty.combatScaling]
enabled = true

# 高难度区域的怪物对玩家造成更高伤害
damageToPlayer.enabled = true
damageToPlayer.intensity = 1.0      # 缩放强度
damageToPlayer.clampMin = 0.2       # 最小倍率
damageToPlayer.clampMax = 5.0       # 最大倍率

# 高难度区域的怪物更耐打（降低玩家伤害）
damageByPlayer.enabled = false      # 默认关闭
damageByPlayer.intensity = 0.5
damageByPlayer.clampMin = 0.2
damageByPlayer.clampMax = 2.0
```

## 难度指示器 HUD

手持**区域难度指示器**（主手或副手均可），HUD 右上角显示：

```
区域难度: 4.50        ← 颜色编码：绿(<1.0) 黄(1.0~2.0) 红(>2.0)
  维度(末地): x3.00
  生物群系(end_highlands): x3.00
  深度(Y=30): x1.50
  综合倍率: 4.50
```

- 每 1 秒自动刷新
- 跨维度传送立即刷新
- 切走物品自动隐藏

## 构建

```bash
./gradlew build
```

构建输出：`build/libs/region_difficulty-0.0.1-forge-1.20.1-alpha.jar`

## 许可

MIT License
