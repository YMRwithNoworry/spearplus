# Changelog

## 1.0.7

- 蓄力动画改为**三叉戟式的“向后收”姿态**，不再是原版长矛的举矛前推。
  客户端用 `IClientItemExtensions` 实现（`RegisterClientExtensionsEvent` 注册到原版 7 种长矛）：
  - 第三人称：`getArmPose` 在蓄力期间返回 `ArmPose.THROW_TRIDENT`，即原版三叉戟蓄力的持握姿态；
  - 第一人称：`applyForgeHandTransform` 套用原版 `FirstPersonHandsAndItemsRenderer` 里 `case TRIDENT`
    的那组变换（手臂后收 -55°/35.3°/-9.785°、蓄满后的轻微抖动与前后位移），
    并把 pull-back 进度对齐 `FULL_CHARGE_TICKS`。
  姿态只在**投掷蓄力期间**生效，且只作用于发起蓄力的本地玩家；不蓄力时仍是原版举矛，其它玩家不受影响。
- 投掷不再要求蓄满：**任何时长松手都会投出长矛**，力度按蓄力时长线性缩放，
  速度从 `MIN_LAUNCH_SPEED = 0.75` 到 `MAX_LAUNCH_SPEED = 2.5`（格/tick，满蓄力等于原版三叉戟投掷速度）。
  投掷音效音调也随力度略微变化。松开 Shift（只要手里还拿着长矛）同样会把矛投出去，不再出现“有时投有时不投”的竞态。
- 投掷物**受重力约束**：每 tick 增加 `-0.05` 格/tick² 的下坠（与原版箭/三叉戟一致），
  并且每 tick 让模型朝向当前速度方向，因此长矛会沿着抛物线飞行、下落时矛头自然朝下。
  直投不再能一直飞到天边：满蓄力平抛约 19 格落地，抬头抛才能抛得更远。
- 落地姿态与穿透、伤害规则不变。

## 1.0.6

- 修复蓄力投掷时手部动画仍然怪异的问题（手臂在两种姿态之间每 tick 抖动、举矛动作永远起不来）。
  根因（读源码确认，1.0.5 只诊断到了一半）：服务端取消 `LivingEntityUseItemEvent.Tick` 时，
  NeoForge 的 `EventHooks#onItemUseTick` 会返回 **-1**，于是
  `LivingEntity#updateUsingItem` 里 `--useItemRemaining <= 0` 当场成立并**立刻调用 `completeUsingItem()`**，
  服务端每 tick 都把使用状态清掉一次；客户端收到同步的标志位后跟着 `stopUsingItem()`，
  下一 tick 又因为右键仍按住而重新开始使用。于是 `getTicksUsingItem()` 永远停在 1 附近，
  原版 `SpearAnimations` 的举矛/摆动进度每次都被重置，手臂就在“举矛姿态”和“空手姿态”之间反复横跳。
- 改为**服务端从一开始就不进入原版使用状态**：在 `PlayerInteractEvent.RightClickItem` 里取消蓄力玩家的这次右键。
  该事件位于 `Item#use` 之前，取消后既不会留下使用状态，也不会有使用音效和背包重同步；
  原版突刺（`KineticWeapon#damageEntities`）在蓄力期间因此根本没有机会触发，比“先跑起来再取消”更干净。
- 客户端的原版使用动作**照常执行**：`MultiPlayerGameMode#useItem` 会预测性地调用 `ItemStack#use`，
  客户端本地照样 `startUsingItem`，`ticksUsingItem` 从 0 平滑涨到 `delayTicks`（铁矛 12 tick）后进入摆动，
  举矛动画与第一/第三人称手持动画全部由原版驱动，无需自绘。
- 客户端上报按键边沿的时机由 `ClientTickEvent.Post` 提前到 `Pre`：
  同一 tick 内蓄力包先于原版右键包发出，服务端才能可靠地拒绝这次使用
  （并保留“潜行 + 手持长矛”作为兜底判定）。

## 1.0.5

- 修复 Shift + 右键蓄力动画闪烁、且永远射不出去的问题。
  根因：上一版把蓄力计时挂在原版使用状态上，但实测该状态不可用——
  客户端 `LocalPlayer#isUsingItem` 在按住期间**每 tick 在 true/false 之间翻转**
  （`LocalPlayer` 用 `startedUsingItem` 本地标志，并会被服务端同步包反复纠正），
  于是 `getTicksUsingItem()` 永远停在 1，蓄力攒不满，动画也每 tick 重置一次（表现为闪烁）。
  另外服务端读到的 `isShiftKeyDown()` 在蓄力期间恒为 false，进一步让投掷判定失效。
- 改为由客户端上报**原始按键状态**（`spearplus:spear_charge` 载荷，仅在边沿发送），
  服务端用 tick 差自行计时，不再依赖原版使用状态。
- 原版使用动作仍然放行（它提供原版手臂姿态与手持蓄力动画）；
  蓄力期间在服务端取消 `LivingEntityUseItemEvent.Tick` 以抑制原版突刺伤害。

## 1.0.4

- 修复插在建筑上的长矛会跟随玩家视角转动的问题。
  根因：`ThrownSpearRenderer.submit` 里额外乘了一次 `camera.orientation`，
  而渲染管线在调用 `submit` 之前**已经**应用过相机视图旋转，等于把视图旋转抵消掉，
  实体就变成了永远正对相机的 billboard。现在姿态只在世界空间构建。
- 修复蓄力时手部动画怪异的问题。
  根因：先前为了“不出现举矛动作”取消了 `Item.use`，导致 `isUsingItem` 永不置位，
  而原版 `SpearAnimations` 正是靠 `HumanoidRenderState#ticksUsingItem` / `isUsingItem` 驱动，
  于是手臂落回默认姿态。
  现改为**让原版使用动作正常跑起来**：动画、蓄力进度、释放包全部复用原版；
  服务端只在蓄力期间取消 `LivingEntityUseItemEvent.Tick`，以抑制原版突刺伤害
  （该事件位于 `ItemStack#onUseTick` 之前，取消它会跳过 `KineticWeapon#damageEntities`）。
- 顺带删除不再需要的自定义网络载荷 `spearplus:spear_charge` 与客户端按键轮询，
  模组回归到"零网络包、零自定义输入"。

## 1.0.3

- 修复渲染器从未注册导致渲染帧崩溃的问题。
  根因：NeoForge 26.3 的 `@EventBusSubscriber` 已没有 `bus` 属性，只会把监听器注入 **game bus**，
  而 `EntityRenderersEvent.RegisterRenderers` 是 mod bus 事件，注册静默失效，
  `thrown_spear` 没有渲染器 → `EntityRenderDispatcher` 解引用 null 崩溃。
  现改为在 `@Mod` 构造函数里用 `modEventBus.addListener` 显式注册，并按 `FMLEnvironment.getDist()` 判定客户端。
  另外给 `ThrownSpear#shouldRender` 加了兜底，渲染器缺失时跳过渲染而不是崩游戏。
- 修复投出的长矛外观抖动：原先用 `ItemDisplayContext.GROUND` 解析模型，
  而原版长矛物品模型对 `gui/ground/fixed/on_shelf` 选用的是平面背包贴图，
  在三维空间里旋转就像一块翻来翻去的卡片。改用 `ItemDisplayContext.NONE` 解析到
  3D 的 `*_spear_in_hand` 模型，并套用与 `ThrownTridentRenderer` 完全相同的姿态公式。
- 新增回收：落地后的长矛可以走近拾取（`playerTouch`），飞行中不可拾取，背包满时不消失。

## 1.0.2

- 修复潜行 + 右键无法进入蓄力的问题。
  根因：蓄力原本依赖原版 `LivingEntity#isUsingItem`，但该输入必须先取消原版 `Item.use` 才能不出现举矛动作，
  于是原版使用状态永远不会置位，蓄力分支在第一个 tick 就被判定为“已松开”而清空。
  现在改由客户端上报按键边沿（`spearplus:spear_charge` 载荷）+ 服务端自行计时。
- 修复 `startCharge` 非幂等导致蓄力每 tick 重置、永远攒不满的问题。
- 移除了一处会在每 tick 清空蓄力表的服务器清理逻辑。

## 1.0.1

- 投掷触发方式由右键改为 **Shift + 右键**；单独按右键恢复为原版举矛行为，两者不再冲突。
- 长矛落地后不再消失，也不再掉落可拾取物品：改为在最终落点按落地姿态斜插固定，并永久保留在场景中。

## 1.0.0

- 初始版本，目标 Minecraft 26.3 / NeoForge 26.3.0.51-beta。
- 原版长矛新增蓄力投掷：满 20 tick 松手投出，伤害为攻击力属性值 × 1.5。
- 投掷物直线飞行、无重力、无速度衰减，穿透路径上所有生物且每个只结算一次。
- 原版长矛的近战行为与附魔效果保持不变；无新增物品、纹理或配置文件。
