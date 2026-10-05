# Spear Plus（长矛 Plus）

为 **Minecraft 26.3 / NeoForge 26.3.0.51-beta** 提供的一个小型模组：在**完全不改动原版长矛既有行为**的前提下，
给原版长矛增加“长按右键蓄力后投掷”的第二种攻击方式。

## 功能

| 行为 | 说明 |
|---|---|
| 蓄力投掷 | 手持长矛按住使用键（右键）蓄力，**满 20 tick（1 秒，与原版弓一致）** 后松手，长矛作为投掷物飞出，并从背包中移除该长矛 |
| 蓄力未满 | 提前松手不投掷：长矛留在主手，不消耗、不产生冷却、不生成任何实体 |
| 伤害 | 命中伤害 = 该长矛 ItemStack 的**攻击力属性值 × 1.5**，在命中瞬间从投掷物携带的物品读取（附魔提供的攻击力加成一并计入） |
| 穿透 | 投掷物穿过飞行路径上命中的所有生物，每个生物各结算一次伤害且只结算一次，不被首个命中目标阻挡 |
| 回收 | 命中方块后投掷物消失，并在落点掉落一柄可被玩家拾取的长矛（与原版三叉戟一致） |
| 近战 | 长矛原有的近战伤害、突刺/击退/下马行为与附魔效果全部保持原样 |

## 实现要点

- **不替换任何原版内容**：没有新增物品、附魔、配置文件、命令或 GUI；没有修改任何原版物品/实体 ID。
  投掷能力通过 NeoForge 的 `LivingEntityUseItemEvent.Stop` 事件叠加在现有长矛上，
  长矛本身的 `Item` 类、数据组件（`kinetic_weapon`、`piercing_weapon`、`attack_range` …）都不动。
- **识别方式**：使用原版物品标签 `minecraft:spears`，因此木/石/铜/铁/金/钻石/下界合金长矛以及
  其它模组按标签加入的长矛都能投掷。
- **服务端权威**：投掷物实体的生成、直线飞行、碰撞检测与伤害结算**全部在服务端执行**；
  客户端只保留原版的使用动画与输入处理，并额外注册一个渲染器。专用服务器与单人世界行为一致。
- **投掷物**：自定义实体 `spearplus:thrown_spear`，继承 `Projectile`。
  - 不受重力影响、速度不衰减（每 tick 直接沿抛出向量平移）。
  - 每 tick 用 `Level.clip` 求飞行线段上的方块命中点，再用射线-AABB 求交收集该线段内**所有**生物，
    按距离排序后逐个结算伤害；第一个方块命中点之后的目标不会被命中。
  - 命中方块后丢弃实体并 `spawnAtLocation` 掉出一柄长矛。
- **渲染**：复用原版长矛的物品模型与贴图（`ItemModelResolver` + `ItemDisplayContext.GROUND`），
  按速度方向调整朝向（与三叉戟一致）。**没有新增任何纹理、模型、粒子或音效文件。**
- **模组 id / 名称**：全流程统一使用 `spearplus` / `Spear Plus`，贯穿 `neoforge.mods.toml`、
  Java 包名（`com.spearplus`）与注册表命名空间。唯一注册项是投掷物实体类型，ID 稳定，
  已有存档可直接加载。

## 构建

需要 JDK 25。

```bash
# Windows
set JAVA_HOME=D:\MC\jdk\jdk-25.0.2
gradlew.bat build

# Linux / macOS
JAVA_HOME=/path/to/jdk-25 ./gradlew build
```

产物：`build/libs/spearplus-1.0.0.jar`

调试运行：

```bash
gradlew.bat runClient     # 单人客户端
gradlew.bat runServer     # 专用服务端
```

## 手工验收步骤

1. **单人世界**：`/give @s minecraft:iron_spear`，按住右键满 1 秒后松手 —— 长矛飞出并击中目标，
   伤害为铁长矛攻击力 × 1.5（铁长矛 `attack_damage` 为 2.0 + 材料加成，可在 F3+H 高级提示中核对）。
2. **穿透**：`/summon minecraft:pig` 三次排成一线，从侧面投掷 —— 三只全部被穿透并各受一次伤害。
3. **蓄力未满**：按住右键约 0.5 秒松手 —— 长矛仍在主手，世界中没有投掷物。
4. **回收**：向方块投掷 —— 落点出现一柄可拾取的长矛。
5. **专用服务器**：两名玩家，A 投掷时 B 能看到飞行中的长矛，伤害在服务端正确结算。
6. **旧存档**：装模组前创建的存档直接加载，无注册表或数据错误。

## 目录结构

```
src/main/java/com/spearplus/
  SpearPlus.java               主模组类（模组 id 常量、注册入口）
  ModEntities.java             投掷物实体类型注册
  SpearThrowHandler.java       蓄力判定与投掷（服务端）
  entity/ThrownSpear.java      投掷物实体（飞行、穿透、伤害、回收）
  client/SpearPlusClient.java  客户端渲染器注册
  client/ThrownSpearRenderer.java
  client/ThrownSpearRenderState.java
src/main/resources/META-INF/neoforge.mods.toml
```

## 许可证

MIT
