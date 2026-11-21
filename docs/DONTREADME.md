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
  `resourcepacks/default` 里的 OptiFine `materialmaps` 已移除；内置资源包 `pack_format` 9 → 34。
- **`cave_vines_head`、`skeleton_head`、`weeping_wines`、`wither_skeleton_head`** 并不存在对应方块，加载时跳过
  （272 = 276 − 4，与上游 1.20.1 上的行为一致）。

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
  （来自内置资源包的内联 `block_state` multipart 模型）。

## 尚未完成 / 已知限制

- 没有做单元测试；渲染验证靠上面的截图与日志。
- 上游 `resourcepacks/32x` 只有 `pack.mcmeta` + `pack.png` 两个文件（占位用的空包），没有随移植保留。
- 不支持 OptiFine 的 `materialmaps`（CTM 兼容）；上游那份 `resourcepacks/default/.../materialmaps/block/grass.json` 已删除。
- `layer_types/ash.json` 依赖 `cinderscapes`，未安装时该 layer 不生效。
- 设置界面只暴露 `mode` 与 `better_snow`（与上游一致），没有复用上游 SpruceUI 的样式。
