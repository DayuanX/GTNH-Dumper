# GTNH-Dumper
 Dump data for GTNH wiki

 Special thanks : https://github.com/GTNewHorizons/ExampleMod1.7.10

## 按 Mod 导出

在游戏内打开 **NEI 选项 → 工具 → 数据导出 → GTNH Dumper**，在 **Mod 筛选 / Mod filter**
输入已加载 Mod 的 ID（如 `gregtech`）或完整显示名称，然后点击对应导出按钮。
不区分大小写，忽略首尾空格；清空输入即可恢复全量导出。设置使用 NEI 的全局/世界配置并自动保存。
未知或重名的 Mod 会提示错误；重名时请使用 ID。

筛选结果保存到 `dumps/filtered/<modid>/`，全量导出仍使用 `dumps/`。各个 Mod 的结果互不混合。

- 物品、图标和矿辞成员：普通物品按注册所属 Mod 筛选，GT 机器按 metadata 对应的
  MetaTileEntity 实现类所属 Mod 筛选，保留所有 metadata/NBT 变体。
- 流体：按 Forge 记录的流体注册 Mod 筛选，包括没有对应方块的流体。
- GT 材料：按导出数据中的材料来源字段筛选；GT 结构沿用控制器物品的上述归属规则。
  因此附属 Mod 共用 `gregtech:gt.blockmachines` 时，仍可归属到提供具体 MetaTileEntity
  实现类的 Mod；无法可靠解析实现类归属时回退到物品注册 Mod。
- 配方：按 **原始配方来源** 筛选，目前使用 `GTRecipe.owners` 的第一条记录。
  来源表示最初注册该配方的 Mod。
  匹配后保留完整配方，包括其他 Mod 的原料和产物，并导出 `sourceMod` 便于核对。
- 神秘时代研究：在研究成功注册时记录 FML 生命周期事件的 Mod ID，导出为 `sourceMod`。
  同一分类可以包含多个 Mod 的研究；修改已注册研究或重复注册保留首次来源。
  生命周期上下文缺失时，全量导出保留该研究并省略 `sourceMod`，Mod 筛选跳过该项。

GT 配方需要在加载配方前开启 `config/GregTech/Client.cfg` 的 `NEIRecipeOwner=true`，修改后重启游戏。
运行时打开该选项无法补回此前丢失的来源记录。缺少来源的 GT 配方、普通 NEI 配方以及 EOH/EEC
等缺少来源记录的自定义配方，筛选时跳过并在 `recipes_filter_report.json` 中列出原因，
游戏内也会显示提示。清空筛选仍可全量导出这些配方。

任务、任务线、TiC 材料、矿脉及维度等没有可靠逐项 Mod 归属的数据，在开启筛选时会提示并停止该项导出。

研究来源捕获使用 UniMixins 的延迟加载入口，仅在安装 Thaumcraft 时启用；运行 Dumper 需安装 UniMixins。

## 配方专用物品变体

从 `GTRecipe` 转换的配方会导出布尔字段 `nbtSensitive`，原样保留 `isNBTSensitive` 的 `true` 或 `false`。
EOH 等自定义配方及其他无法确定该标志的配方省略此字段，不默认填 `false`。
该字段表示 GT 的配方级 NBT 匹配标志；`false` 不排除特殊物品自身的 NBT 检查，输入和产物的 NBT 仍保留。

客户端首次导出物品信息或图标时，会遍历已支持的配方处理器，收集输入、产物、矿辞压缩前的备选物品、催化物及其他展示物品，补充创造物品栏中的记录。每个物品保留 NBT，数量归一为 1，沿用现有 JSON key、short key 和图片命名规则。

完成的扫描结果供本次客户端会话后续导出复用。扫描失败会记入日志，并在下次物品或图标导出时重试；成功扫描后修改配方，需要重启客户端以更新结果。

扫描会遍历全部配方处理器，再按物品所属 Mod 筛选结果，其中 GT 机器采用前述实现类归属规则。配方导出本身按原始配方来源筛选。

日志包含扫描汇总和失败项，便于检查当前整合包中各处理器提供的物品记录。

## 一次性自动进档导出

首次启动会在游戏目录生成 `config/gtnhdumper-autorun.json`，默认 `enabled: false`。
需要导出时将 `enabled` 改为 `true`，然后正常启动客户端。也可以提前创建该文件：

```json
{
  "enabled": true,
  "outputMode": "latest",
  "noImages": true,
  "modFilter": "",
  "keepWorld": false,
  "exitGame": true
}
```

上例适合反复更新配方数据。仅写 `{"enabled": true}` 时使用时间戳目录，同样默认跳过图标导出。
自动流程由模组在客户端内执行。
启动时先将 `enabled` 改为 `false`，防止崩溃后重复运行；下次运行需手动重新开启。
到主菜单后自动创建本次专用的 `Dumper-<runId>` 创造模式超平坦存档，等待 NEI 与神秘研究同步，
逐项导出到所选输出目录。`request.json` 留存本次参数，`status.json` 记录进度、耗时、筛选范围和跳过的任务。
每次仍生成独立的运行 ID 和存档名，固定输出目录只复用导出文件。

### 输出目录与图片

| 参数 | 默认值 | 行为 |
| --- | --- | --- |
| `outputMode` | `"timestamp"` | 每次写入 `dumper-runs/<runId>/dumps/`；改为 `"latest"` 后重复写入 `dumper-runs/latest/dumps/` |
| `noImages` | `true` | 跳过物品图标任务；改为 `false` 时导出图标及动画 |
| `modFilter` | `""` | 全量导出；填入已加载 Mod 的 ID 或完整名称后按前述来源规则筛选 |

`latest` 在世界和 NEI 准备完成后更新本次范围的数据。旧配方、结构目录会先清除，
因此附属更新时删除的配方也会从结果中移除。全量与各 Mod 的结果分别保存；
例如 `modFilter: "gregtech"` 写入 `dumper-runs/latest/filtered/gregtech/dumps/`，
对应的请求与状态也保存在这个 Mod 的目录中。
时间戳模式的筛选数据写入 `dumper-runs/<runId>/dumps/filtered/<modid>/`，请求与状态保存在 `<runId>/` 下。

`noImages: true` 保留该目录中的 `icons/`、`itemicon.json` 与图片重定向文件。
首次只导出数据时得到配方、物品等 JSON；图片及动画映射可以沿用已有图标导出，
需要补充新物品或更新纹理时设置 `noImages: false`。
图标导出仍使用最大 PNG 压缩。`imagesOnly` 和 `layoutsOnly` 明确请求捕获素材，优先于 `noImages`。

### 只更新一个附属

```json
{
  "enabled": true,
  "outputMode": "latest",
  "modFilter": "TwistSpaceTechnology",
  "noImages": true,
  "exitGame": true
}
```

筛选复用手动导出的归属规则，保留命中配方中的全部跨 Mod 原料与产物。
物品与流体 JSON 只包含该 Mod 的条目，处理附属数据时与基础包的物品库组合使用。
任务和矿脉等缺少逐项归属的数据会列入 `status.json` 的 `skipped`，其余任务继续完成。
配方来源缺失的分类另见 `recipes_filter_report.json`；GT 配方的来源采集需要提前开启 `NEIRecipeOwner`。
`modFilter` 只控制本次自动会话，手动 NEI 筛选设置保持原值。

`layoutsOnly` 的界面范围与配方注册来源不同，`smoke` 使用的 TiC 材料也缺少逐项归属；
运行这两种模式时使用空 `modFilter`。配置组合错误会在创建存档、清理旧数据前报告。

### 存档与结束流程

导出后等待集成服务器保存、停止并刷新存档缓存。成功时按 `keepWorld` 保留或删除本次创建的存档；
导出或停止阶段报错时保留存档。`exitGame: true` 随后退出客户端，`false` 返回主菜单。
结束时恢复并保存运行前的难度和失焦暂停设置，准备或导出失败时也执行恢复。
清理失败会报告 `FAILED`，保留剩余内容。`EXPORTED` 表示导出任务及存档退出流程完成；随后可用数据处理工具检查物品关联和图片完整性。

可选参数：`runId`（1–101 位字母、数字、下划线或连字符，首字符必须是字母或数字，默认自动生成）、
`seed`（默认 `290002`）、`smoke: true`（仅导出 TiC 材料以检查生命周期）、
`layoutsOnly: true`（仅导出 NEI 布局原始资料）。两种模式同时开启时优先使用 `layoutsOnly`。
时间戳模式的 `runId` 必须唯一，`latest` 为固定输出目录的保留名称。

设置 `noImages: false` 后可以使用以下图标复用参数。
`imageMetadata` 接受先前图标 JSON 映射文件路径，导入名称、帧数及尺寸等映射并跳过图标渲染。
使用此参数时，请将对应 PNG 配齐到输出目录并核对图片。
`imageResume` 接受包含 `directory` 和 `images` 的 JSON 清单：`images` 将 PNG 文件名映射到 `[宽, 高]`，
`directory` 指向图片目录。导出器按清单决定可复用项，将图片硬链接或拷贝到本次输出，缺失清单项继续渲染。
复用前应检查 PNG 内容，并确认映射或清单与当前整合包及导出设置匹配。
状态中的 `imagesReused` 记录是否启用了上述任一复用参数。

`managedByRunner: true` 是可选外部调度接口：模组仍负责创建世界、导出和安全退出存档，
将世界保留给外部调度器验证、清理。调度器负责启动客户端、验证 PNG 和核对导出数据。
外部调度器等待进程退出时须保持 `exitGame: true`。

配方物品的附加提示使用 `tooltip` 字段，通过 `RecipeItem.withTooltip(...)` 追加。
普通配方物品序列化为以 `<br>` 连接的字符串；专用记录和流体槽也可保留提示列表。
它与物品数据库的 `tooltips` 字段分开。神秘时代配方的 `research` 保持列表形式。

### 只更新研究数据

设置 `researchOnly: true` 只运行神秘时代研究导出，支持同时指定 `modFilter`。
它使用已注册的研究表，跳过物品扫描、图片导出与玩家研究解锁；仍沿用自动建档、退出和存档清理流程。
此模式与 `imagesOnly`、`layoutsOnly`、`smoke` 互斥，要求安装 Thaumcraft。
`outputMode: "latest"` 只清理并重新写入 `tc4research.json`，保留其他导出数据。

```json
{"enabled":true,"researchOnly":true,"noImages":true,"exitGame":true}
```

### 定向图片与时钟动画

`imagesOnly: true` 仅运行配方物品扫描和图标导出，不导出其他数据库。
可配合 `imageKeys` 指定完整 `shortKey` 数组；未找到的 key 会报告失败。
例如 `{"enabled":true,"imagesOnly":true,"imageKeys":["minecraft:diamond"]}`。
`imageDiagnostics: true` 在 `icon-capture-report.json` 记录渲染器、非透明像素数、NBT 和 FBO 前后的 GL 状态，供定向图片诊断使用。

GT 两种超立方体由客户端 RenderTick 同时按 50 ms 目标间隔采样 120 帧，动态帧为 64×64，静态图另以 256×256 渲染。
实际采样时间记录在诊断文件中；低帧率会影响间隔。动画仍使用现有 `frameCount`、`size` 和 `_dynamic.png` 横向帧条格式，PNG 使用最大压缩。
手动 NEI 导出及自动会话均等待采样和 PNG 写入完成后才发布映射或完成提示。
这两项时钟动画重新采样，不从 `imageResume` 复用旧帧条；`imageMetadata` 仍只导入已有映射并跳过采样。

特殊物品的选择规则和参数集中在 `ItemIconRenderProfile`，按注册名与 damage 选择，同一物品的 NBT 变体使用相同配置。
定时采样与关闭面剔除分别配置；超立方体在绘制期间关闭剔除，以保留 Angelica 将宽线展开成的三角形，随后恢复原状态。
普通物品保留现有静态图和纹理动画导出流程。当前所有定时采样物品共用一组采样参数。
