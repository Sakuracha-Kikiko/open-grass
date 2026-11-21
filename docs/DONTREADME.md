# 移植说明（LambdaBetterGrass 1.5.2 / Quilt 1.20.1 → 1.21.1 NeoForge）

本仓库是 [LambdaBetterGrass](https://github.com/LambdAurora/LambdaBetterGrass) 1.5.2（MIT）在
Minecraft 1.21.1 / NeoForge（21.1.x）上的移植。上游按 Quilt + Fabric Rendering API（FRAPI）编写，
这里全部改用 NeoForge 原生 API 重写，**不使用 mixin，也不使用 access widener**。

## 架构映射

| 上游（Quilt 1.20.1） | 本移植（NeoForge 1.21.1） |
| --- | --- |
| `ModelLoadingPlugin` + `ModelModifier.WRAP_PHASE` | `ModelEvent.ModifyBakingResult` + `BlockModelShaper.stateToModelLocation` 逐 state 替换 |
| FRAPI `emitBlockQuads` / `pushTransform` | `BakedModelWrapper` + 5 参 `getQuads(state, side, rand, data, renderType)`，顶点数组直接改写 |
| FRAPI `quad.colorIndex()` / `nominalFace()` | `BakedQuad#getTintIndex()` / `getDirection()` |
| FRAPI `spriteBake(sprite, BAKE_LOCK_UV)` | `LBGQuadUtil#withSprite`：把顶点 UV（int 偏移 4、5）重映射到 `sprite.getU(u / 16f)` |
| FRAPI `materialFinder().ambientOcclusion(FALSE)` | `new BakedQuad(vertices, tint, dir, sprite, shade, false)` |
| `world.getBlockState(pos)` 由 FRAPI 直接传入 | `getModelData(level, pos, state, data)` 写入 `LBGData.BLOCK_CONTEXT`，渲染期取出 |
| `BlockRenderLayerMap.put` 全局改层 | 包装模型的 `getRenderTypes(...)` 返回 `ChunkRenderTypeSet` |
| `InMemoryResourcePack` + 强制 resource reload | 自定义 `SpriteSource`（`open_grass:generated`）+ 自带 `assets/minecraft/atlases/blocks.json` |
| `ModelVariantMap.fromJson`（内联 block_state） | `BlockModelDefinition.fromStream(Context#setDefinition(...))` + `IModelBakerExtension#bakeUncached` |
| `BlockBehaviour#getModelOffset` / `isSideSolidFullSquare` | `getOffset` / `isFaceSturdy(..., SupportType.FULL)` |
| `Identifier`、`Registries.BLOCK` | `ResourceLocation`、`BuiltInRegistries.BLOCK` |
| SpruceUI 设置界面 + ModMenu + `VideoOptionsScreenMixin` | 原生 `Screen` + `IConfigScreenFactory` + `ScreenEvent.Init.Post` 追加按钮 |
| `@EventBusSubscriber(bus = MOD)`（已废弃） | `modEventBus.addListener(...)`（`OpenGrass` 构造器，客户端分支用 `FMLEnvironment.dist` 守卫） |

## 目录结构

```
com.sakuracha.opengrass
├── OpenGrass / Config / LBGMode          入口、配置（ModConfigSpec）、渲染模式枚举
└── client
    ├── OpenGrassClient                    客户端初始化（配置界面扩展点）
    ├── LBGData                            资源加载结果缓存（states / layer types / 生成贴图）
    ├── event/LBGClientEvents              模型烘焙替换、SpriteSource 类型注册
    ├── resource/LBGSpriteSource           把运行时生成的贴图注册进方块图集
    ├── metadata/                          states、data、layer type、layer 的解析与烘焙
    ├── model/                             BakedModel 包装、烘焙器、顶点工具
    ├── gui/SettingsScreen                 设置界面
    └── util/                              贴图合成（mask）、邻块判定
```

## 与上游的行为差异

- **不再有“迁移到 Quilt”的拦截**：上游的 `MigrationNotice`、`MainMixin`（取消 `Main.main`）整体移除。
- **雪变体**：上游用两个 metadata 相互引用实现，这里直接在烘焙结果里取同一方块 `snowy=true` 的模型。
- **`getModelData` 上的额外修复**：上游 FRAPI 直接把 `world`/`pos` 传给 `emitBlockQuads`；
  NeoForge 只能经 `ModelData` 传递，因此 `LBGBakedModel` 与 `LBGLayerBakedModel` 都会写入
  `LBGData.BLOCK_CONTEXT`（栅栏、苔藓地毯这类 `layer` 状态的外层包装正是后者）。
- **`layer_types/ash.json`** 指向 `cinderscapes:ash`，未安装时自动跳过（日志里只加载 3 个 layer type）。
- **1.21.1 改名**：`states/grass.json`、`data/grass.json` 已按 `short_grass` 处理；
  上游 `resourcepacks/default` 里的 `materialmaps` 是 Canvas 渲染器的材质映射（不是 OptiFine），已移除；
  该内置包的内容已并入模组 assets（见下面的「关于「资源包」」）。
- **`cave_vines_head`、`skeleton_head`、`weeping_wines`、`wither_skeleton_head`** 并不存在对应方块，加载时跳过
  （272 = 276 − 4，与上游 1.20.1 上的行为一致）。

## 关于「资源包」

上游把「资源包」用在了三个不同的地方，很容易混淆；拿 1.5.2 的 release jar 逐层拆开看是这样：

1. **模组 jar 本身就是资源包（常开、不可关）**
   NeoForge 把所有模组 jar 的根目录合成一个 `mod_resources` 包（`ResourcePackLoader` 里 `PackSelectionConfig(true, …, TOP)`，
   `withChildren(hiddenPacks)`）；每个 jar 默认是它的隐藏子包，只有 `neoforge.mods.toml` 里 `showAsResourcePack=true` 才会单独列进界面。
   所以像 `assets/minecraft/bettergrass/states/*.json` 这种直接改原版命名空间的资源，不需要任何 `pack.mcmeta`，也不需要玩家启用。

2. **可选内置包 `resourcepacks/default` 与 `resourcepacks/32x`**
   这才是通常说的「资源包」：放在 jar 里，由加载器注册后出现在资源包界面，带图标与描述。
   上游在 `LambdaBetterGrass.java` 里注册：`ResourceLoader.registerBuiltinResourcePack(id("default"), mod, DEFAULT_ENABLED)`
   与 `(id("32x"), mod, NORMAL)` —— 前者默认启用、可关闭，后者默认关闭。
   内容（release jar 实测）：`default` 共 176 个文件、约 300 KB，包含 12 种栅栏的积雪/苔藓层
   （内联 `block_state` multipart、81 个模型 + 5 个模板、69 张 fence 贴图、`mycelium` 覆盖数据、3 个 Canvas materialmap）；
   `32x` 只有 `pack.mcmeta` 和 512×512 的 `pack.png`，没有实际内容。

3. **运行时生成的内存包**
   `LBGResourcePack extends InMemoryResourcePack`：把 mask 合成出来的 `connect`/`blend_up`/`blend_up_m`/`arch` 贴图
   写成 `textures/block/bettergrass/*.png`，插入资源栈最顶层并强制重载一次。它不在资源包界面里出现。

本移植的处理：

- `default` 的内容**并入模组自身的 assets**（`assets/minecraft/bettergrass/data/*_fence.json`、`assets/open_grass/models|textures/block/...`），
  行为等同上游的「默认启用」，但玩家无法关闭（也就不用 `pack.mcmeta`，格式问题随之消失）。
- `32x` 未复刻：上游本来就是空壳，没有内容可丢。
- `materialmaps` 删除：属于 Canvas 渲染器，NeoForge 没有对应物。
- 第三层换成 `LBGSpriteSource`（`open_grass:generated`）+ 自带 `assets/minecraft/atlases/blocks.json`，
  不再需要「插入包 + 强制 reload」。
- 如果以后想让某个包在界面里可选：`AddPackFindersEvent#addPackFinders(new ResourceLocation("open_grass:resourcepacks/default"),
  PackType.CLIENT_RESOURCES, Component.literal("Open Grass - Default extension"), PackSource.BUILT_IN, false, Pack.Position.TOP)`。
  注意 NeoForge 的资源包只有「强制启用」与「默认关闭」两态（该方法的 javadoc 明确写了 resourcepack 默认是关的），
  没有上游 `DEFAULT_ENABLED` 那种「默认开、可关」；1.21.1 的资源包格式是 34（数据包 48）。

## 验证方式

```bash
export JAVA_HOME=/path/to/jdk21
./gradlew build          # 产出 build/libs/open_grass-<version>.jar
./gradlew runClient      # 开发客户端
```

- 日志应有 `Loaded 272 better grass states and 3 layer types.`，且没有 `Failed to retrieve texture` 警告。
- `docs/screenshots/better_grass_fancy.png` 与 `vanilla_off.png` 是同一场景（草方块台阶）在
  `FANCY` 与 `OFF` 下的对比：前者台阶侧面被草贴图完全覆盖，后者是原版泥土。
- `docs/screenshots/better_snow_fence.png` 是雪地上的橡木栅栏，可见栅栏顶部与横杆上的积雪层
  （来自并入模组 assets 的内联 `block_state` multipart 模型）。

## 尚未完成 / 已知限制

- 没有做单元测试；渲染验证靠上面的截图与日志。
- 上游 `resourcepacks/32x` 只有 `pack.mcmeta` + `pack.png`（512×512 图标）三个条目，是空壳占位包，本移植未复刻，因此资源包界面里看不到它。
- 不支持 Canvas 渲染器的 `materialmaps`；上游那三份（`bamboo`、`bamboo_sapling`、`grass`）已删除，NeoForge 上没有对应机制。
- 上游主 jar 还内置了 26 个 `assets/cinderscapes/bettergrass/states` + 26 个 `data` 兼容数据，本移植未搬运（只保留了 `layer_types/ash.json`）；未安装 Cinderscapes 时无影响。
- `layer_types/ash.json` 依赖 `cinderscapes`，未安装时该 layer 不生效。
- 设置界面只暴露 `mode` 与 `better_snow`（与上游一致），没有复用上游 SpruceUI 的样式。
