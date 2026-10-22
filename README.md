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
  后续修改者、配方产物、机器和 NEI 分类的 Mod 都不作为原始来源。
  匹配后保留完整配方，包括其他 Mod 的原料和产物，并导出 `sourceMod` 便于核对。

GT 配方需要在加载配方前开启 `config/GregTech/Client.cfg` 的 `NEIRecipeOwner=true`，修改后重启游戏。
运行时打开该选项无法补回此前丢失的来源记录。缺少来源的 GT 配方、普通 NEI 配方以及 EOH/EEC
等未保存来源的自定义配方不会被猜测归属；筛选时跳过并在 `recipes_filter_report.json` 中列出原因，
游戏内也会显示提示。清空筛选仍可全量导出这些配方。

研究、任务、任务线、TiC 材料、矿脉及维度等没有可靠逐项 Mod 归属的数据，在开启筛选时会提示并停止该项导出。

### Recipe-only item variants

The first item-information or item-icon export in a client session also visits the installed recipe dump adapters. Concrete inputs, outputs, alternatives (before ore-dictionary compression), catalysts, marked items and other exposed stacks supplement the creative-tab enumeration. Stacks are copied with their NBT and normalized to count 1; existing JSON keys, short keys and image filenames are unchanged. This does not deduplicate image pixels or invent dynamic NBT variants.

Successful snapshots are shared by subsequent item/icon exports until the client restarts. Incomplete scans are reported and retried on the next item/icon export; they are never retained as complete snapshots. Restart after changing recipes following a successful scan. All recipe handlers are visited because an item's owning mod can differ from a recipe's registering mod; the resulting items still use the existing item ownership filter, including GT machine ownership. A single-mod export therefore does not include every foreign dependency or require any wiki data. Recipe JSON source filtering is unchanged.

The log reports scan failures and a summary. A failing handler can leave that handler's coverage partial; cached NEI recipe slots and ordinary GT recipes are isolated so other readable entries can still be collected. Coverage is limited to the current adapters and their exposed stacks, not every dynamically generated NEI variant. Validate exports inside the intended GTNH client before relying on completeness.

## 一次性自动进档导出

在游戏目录创建 `config/gtnhdumper-autorun.json`，然后正常启动客户端：

```json
{
  "enabled": true,
  "keepWorld": false,
  "exitGame": true
}
```

仅写 `{"enabled": true}` 也可使用上述默认值。无需外部脚本、Python 或特定启动器。
启动时先将 `enabled` 改为 `false`，防止崩溃后重复运行；下次运行需手动重新开启。
到主菜单后自动创建本次独占的 `Dumper-<runId>` 创造模式超平坦存档，等待 NEI 与神秘研究同步，
逐项导出到 `dumper-runs/<runId>/dumps/`。`request.json` 留存本次参数，`status.json` 记录进度、耗时和错误。
已有同名存档或已使用的运行 ID 会被拒绝，不会进入或覆盖旧存档。

导出后等待集成服务器保存、停止并刷新存档缓存。成功时按 `keepWorld` 保留或删除本次创建的存档；
导出或停止阶段报错时保留存档。`exitGame: true` 随后退出客户端，`false` 返回主菜单。
清理失败会报告 `FAILED`，保留尚未删除的内容。`EXPORTED` 表示任务与生命周期完成，不代表外部数据完整性验证通过。

可选参数：`runId`（1–101 位字母、数字、下划线或连字符，首字符必须是字母或数字，默认自动生成）、
`seed`（默认 `290002`）、`smoke: true`（仅导出 TiC 材料以检查生命周期）、
`layoutsOnly: true`（仅导出 NEI 布局原始资料）。两种模式同时开启时优先使用 `layoutsOnly`。

`imageMetadata` 接受先前图标 JSON 映射文件路径，只导入名称、帧数及尺寸等映射并跳过图标渲染，
**不会拷贝 PNG**。使用者须自行配齐并核对图片。
`imageResume` 接受包含 `directory` 和 `images` 的 JSON 清单：`images` 将 PNG 文件名映射到 `[宽, 高]`，
`directory` 指向图片目录。导出器按清单决定可复用项，将图片硬链接或拷贝到本次输出，缺失清单项继续渲染。
这不是 PNG 内容验证接口；清单应由可信的外部验证流程生成。两者均需与当前整合包及导出设置匹配。
状态中的 `imagesReused` 表示设置了上述任一复用参数，不表示实际复用数量或图片完整性。

`managedByRunner: true` 是可选外部调度接口：模组仍负责创建世界、导出和安全退出存档，
将世界保留给外部调度器验证、清理。外部调度器可通过该模式接入；
启动器调用、PNG 验证及其他导出产物的交叉核验由外部工具负责，不属于模组运行依赖。
外部调度器等待进程退出时须保持 `exitGame: true`。旧 `finalizer` 参数不再执行外部命令。

配方物品的附加提示使用 `tooltip` 字段，通过 `RecipeItem.withTooltip(...)` 追加。
普通配方物品序列化为以 `<br>` 连接的字符串；专用记录和流体槽也可保留提示列表。
它与物品数据库的 `tooltips` 字段分开。神秘时代配方的 `research` 保持列表形式。
