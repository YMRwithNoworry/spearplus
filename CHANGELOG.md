# Changelog

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
