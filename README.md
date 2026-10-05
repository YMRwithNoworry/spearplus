# Spear Plus（长矛 Plus）

为 **Minecraft 26.3 / NeoForge 26.3.0.51-beta** 提供的一个小型模组：在**完全不改动原版长矛既有行为**的前提下，
给原版长矛增加“**Shift + 右键**蓄力后投掷”的第二种攻击方式。

## 功能

| 行为 | 说明 |
|---|---|
| 触发方式 | **Shift + 右键**开始蓄力；单独按右键仍然是原版举矛（原版蓄力突刺），两者互不干扰 |
| 蓄力投掷 | 蓄力**满 20 tick（1 秒，与原版弓一致）** 后松开 Shift 或右键，长矛作为投掷物飞出，并从背包中移除该长矛 |
| 蓄力未满 | 提前松手不投掷：长矛留在主手，不消耗、不产生冷却、不生成任何实体 |
| 伤害 | 命中伤害 = 该长矛 ItemStack 的**攻击力属性值 × 1.5**，在命中瞬间从投掷物携带的物品读取（附魔提供的攻击力加成一并计入） |
| 穿透 | 投掷物穿过飞行路径上命中的所有生物，每个生物各结算一次伤害且只结算一次，不被首个命中目标阻挡 |
| 落地 | 命中方块后**不再消失**：长矛模型固定在最终落点，按落地姿态斜插在地面/墙面上，并保留在场景中 |
| 回收 | **走近落地后的长矛即可拾取**（与掉落物一致的拾取范围）；飞行途中不可拾取，背包满时留在原地不消失 |
| 近战 | 长矛原有的近战伤害、突刺/击退/下马行为与附魔效果全部保持原样 |

## 输入与蓄力的处理方式

这是本模组唯一有技巧的部分，记录在此以免后续改动踩坑。

**为什么不能复用原版的使用状态**：原版长矛的“举矛”是由 `Item.use` → `player.startUsingItem` 驱动的。
需求要求 Shift + 右键时**不出现**举矛动作，因此这个输入必须在 `Item.use` 之前被取消。
而一旦取消，原版的使用状态（`LivingEntity#isUsingItem` / `getTicksUsingItem`）就永远不会被置位，
也就无法用来判断“玩家松手了没有”或“蓄力了多久”。**取消原版使用 与 依赖原版使用状态计时，二者不可兼得。**

**因此蓄力有自己的一套生命周期**：

1. 客户端 `SpearThrowClient` 直接读原始按键（`options.keyUse` + 潜行状态 + 主手是否为长矛），
   在“按住/松开”的**边沿**各发一次 `spearplus:spear_charge` 载荷（`SpearChargePayload`）。
2. 客户端同时取消 `RightClickItem`，服务端 `SpearThrowHandler.onRightClickItem` 也取消一次，
   于是这条输入不会触发原版 `Item.use`，举矛动作彻底不出现，也不会与蓄力互相打断。
3. 服务端收到“开始”后记录起始 tick；收到“结束”时用 tick 差算蓄力时长，达到 20 tick 才投掷。
4. `startCharge` **是幂等的**：按键保持期间客户端会重复发送“开始”边沿，
   若每次都重置起始 tick，蓄力将永远攒不满（这是修复过程中实际踩到的坑）。
5. 安全网：`PlayerTickEvent.Post` 每 tick 检查，一旦玩家不再满足“潜行 + 手持长矛 + 存活”，
   立即丢弃待发的蓄力，不会留下卡住的蓄力状态；`PlayerLoggedOutEvent` 清理离线玩家。

## 实现要点

- **不替换任何原版内容**：没有新增物品、附魔、配置文件、命令或 GUI；没有修改任何原版物品/实体 ID。
  长矛本身的 `Item` 类、数据组件（`kinetic_weapon`、`piercing_weapon`、`attack_range` …）都不动。
- **识别方式**：使用原版物品标签 `minecraft:spears`，因此木/石/铜/铁/金/钻石/下界合金长矛以及
  其它模组按标签加入的长矛都能投掷。
- **服务端权威**：投掷物实体的生成、直线飞行、碰撞检测与伤害结算**全部在服务端执行**；
  客户端只负责输入边沿上报与渲染。专用服务器与单人世界行为一致。
- **投掷物**：自定义实体 `spearplus:thrown_spear`，继承 `Projectile`。
  - 不受重力影响、速度不衰减（每 tick 直接沿抛出向量平移）。
  - 每 tick 用 `Level.clip` 求飞行线段上的方块命中点，再用射线-AABB 求交收集该线段内**所有**生物，
    按距离排序后逐个结算伤害；第一个方块命中点之后的目标不会被命中。
  - 命中方块（或飞行超时）后调用 `plant()`：清零速度、把位置钉在落点、按落地方向翻转朝向并写入实体旋转，
    实体本身保留在世界上（不 `discard`、不掉落物品、不参与拾取）。
  - 落地姿态：由落地瞬间的速度向量求出偏航/俯仰，再取反并限制在 ±89°，
    使矛尖朝下扎入地面、矛杆斜向露出，与三叉戟插地的观感一致。
- **渲染**：复用原版长矛的模型与贴图，**没有新增任何纹理、模型、粒子或音效文件**。
  - 显示上下文必须用 `ItemDisplayContext.NONE`。原版长矛物品模型对 `gui/ground/fixed/on_shelf`
    选用的是**平面背包贴图**，在三维空间里旋转会像一块翻来翻去的卡片（就是“抖动”的来源）；
    `NONE` 才会落到 3D 的 `*_spear_in_hand` 模型。
  - 姿态公式与 `ThrownTridentRenderer` 完全一致（相机朝向 → Y 轴 `yRot-90` → Z 轴 `xRot+90`），
    因为长矛的持握模型与三叉戟模型是同一套坐标约定。
  - 渲染器在 `@Mod` 构造函数里用 `modEventBus.addListener` **显式**注册到 mod bus。
    NeoForge 26.3 的 `@EventBusSubscriber` 已没有 `bus` 属性、只会注入 game bus，
    用它注册 `RegisterRenderers` 会静默失效并导致渲染帧 NPE 崩溃。
- **模组 id / 名称**：全流程统一使用 `spearplus` / `Spear Plus`，贯穿 `neoforge.mods.toml`、
  Java 包名（`com.spearplus`）与注册表命名空间。注册项为投掷物实体类型与一个自定义网络载荷，ID 稳定，
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

产物：`build/libs/spearplus-1.0.3.jar`

调试运行：

```bash
gradlew.bat runClient     # 单人客户端
gradlew.bat runServer     # 专用服务端
```

## 手工验收步骤

1. **潜行蓄力**：`/give @s minecraft:iron_spear`，按住 Shift 潜行后按住右键 —— 应进入蓄力，
   **不出现**原版举矛动作；满 1 秒松开后长矛飞出。
2. **站立右键**：不按 Shift 单独按住右键 —— 与装模组前完全一致（原版举矛/蓄力突刺），不生成投掷物。
3. **蓄力未满**：Shift + 右键约 0.5 秒松手 —— 长矛仍在主手，世界中没有投掷物。
4. **中途取消**：蓄力过程中松开 Shift（或切换物品/打开界面）—— 不投掷，也不残留卡住的蓄力状态。
5. **伤害与穿透**：`/summon minecraft:pig` 三次排成一线，从侧面投掷 —— 三只全部被穿透并各受一次
   攻击力 × 1.5 的伤害。
6. **落地保留**：向方块投掷 —— 长矛斜插在落点且一直留在那里；穿透生物后继续飞行的，
   仍以最终撞到的方块位置为准。
7. **回收**：走近落地后的长矛 —— 应自动拾取回到背包（有拾取音效与拾取动画）；
   背包满时留在原地不消失。
8. **飞行观感**：投掷过程中长矛应是立体的 3D 矛身、朝向与速度方向一致，
   不应出现"扁平卡片翻来翻去"的抖动。
9. **其他右键交互**：空手、其他物品、其他武器、右键箱子/工作台等行为不受影响。
10. **专用服务器**：两名玩家，A 投掷时 B 能看到飞行中与落地后的长矛，伤害在服务端正确结算。
11. **旧存档**：装模组前创建的存档直接加载，无注册表或数据错误。

## 目录结构

```
src/main/java/com/spearplus/
  SpearPlus.java                  主模组类（模组 id 常量、注册入口、载荷注册）
  ModEntities.java                投掷物实体类型注册
  SpearThrowHandler.java          服务端：Shift+右键取消举矛、蓄力计时、投掷
  network/SpearChargePayload.java 客户端→服务端的蓄力开始/结束边沿
  entity/ThrownSpear.java         投掷物实体（飞行、穿透、伤害、落地固定）
  client/SpearThrowClient.java    客户端：原始按键检测、边沿上报、取消举矛
  client/ThrownSpearRenderer.java 投掷物渲染（复用原版长矛模型贴图）
  client/ThrownSpearRenderState.java
src/main/resources/META-INF/neoforge.mods.toml
```

## 许可证

MIT
