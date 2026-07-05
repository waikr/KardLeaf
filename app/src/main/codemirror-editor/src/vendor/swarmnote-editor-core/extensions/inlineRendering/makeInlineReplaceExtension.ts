import { RangeSetBuilder, type Extension } from '@codemirror/state';
import { ensureSyntaxTree } from '@codemirror/language';
import {
  Decoration,
  type DecorationSet,
  EditorView,
  ViewPlugin,
  type ViewUpdate,
  WidgetType,
} from '@codemirror/view';
import type { InlineRenderingSpec, ReplacementExtension } from './types';
import { shouldReveal } from './revealStrategy';
import { checkUpdateAction } from '../../core';

/**
 * 装饰条目接口
 * 
 * 存储待应用的装饰信息，用于后续排序和构建 RangeSet。
 */
interface DecorationEntry {
  /** 起始位置 */
  from: number;
  /** 结束位置 */
  to: number;
  /** 装饰对象 */
  decoration: Decoration;
}

/**
 * 创建内联替换扩展
 * 
 * **核心功能：**
 * 这是一个工厂函数，根据提供的规格列表创建一个 CodeMirror ViewPlugin，
 * 该插件会遍历语法树并将匹配的节点替换为装饰或 widget。
 * 
 * **工作原理：**
 * 
 * 1. **注册阶段**：将 specs 中的 nodeNames 映射到对应的 extension
 * 2. **构建阶段**：遍历可见区域的语法树，为每个匹配节点创建装饰
 * 3. **更新阶段**：监听视图更新，根据需要重建装饰
 * 
 * **关键特性：**
 * - 支持嵌套检测（通过 parentTags 追踪父节点深度）
 * - 智能显示策略（根据选区状态决定显示源码还是 widget）
 * - 性能优化（使用 checkUpdateAction 避免不必要的重建）
 * 
 * @param specs - 内联渲染规格列表
 * @returns CodeMirror 扩展
 */
export function makeInlineReplaceExtension(
  specs: InlineRenderingSpec[],
): Extension {
  // 构建节点名称到扩展的映射表
  const specMap = new Map<string, ReplacementExtension>();
  for (const spec of specs) {
    for (const name of spec.nodeNames) {
      specMap.set(name, spec.extension);
    }
  }

  // 创建 ViewPlugin
  return ViewPlugin.fromClass(
    class {
      /** 当前装饰集合 */
      decorations: DecorationSet;

      /**
       * 构造函数：初始化时构建装饰
       * 
       * @param view - 编辑器视图
       */
      constructor(view: EditorView) {
        this.decorations = this.buildDecorations(view);
      }

      /**
       * 更新方法：响应视图变化
       * 
       * 使用 checkUpdateAction 智能决策：
       * - 'rebuild' → 重建装饰
       * - 'skip' → 跳过（拖拽中）
       * - 'none' → 无操作
       * 
       * @param update - 视图更新对象
       */
      update(update: ViewUpdate) {
        if (checkUpdateAction(update) === 'rebuild') {
          this.decorations = this.buildDecorations(update.view);
        }
      }

      /**
       * 构建装饰集合
       * 
       * **核心算法：**
       * 1. 遍历所有可见区域（visibleRanges）
       * 2. 对每个区域解析语法树
       * 3. 深度优先遍历语法节点
       * 4. 匹配节点名称，创建装饰
       * 5. 应用显示策略（判断是否隐藏）
       * 6. 排序并构建 RangeSet
       * 
       * @param view - 编辑器视图
       * @returns 装饰集合
       */
      buildDecorations(view: EditorView): DecorationSet {
        const entries: DecorationEntry[] = [];
        // 父节点标签计数器，用于检测嵌套层级
        const parentTags = new Map<string, number>();

        // 遍历所有可见区域（支持大文档的分段渲染）
        for (const { from, to } of view.visibleRanges) {
          // 确保语法树已解析到指定位置
          ensureSyntaxTree(view.state, to)?.iterate({
            from,
            to,
            /**
             * 进入节点时的回调
             * 
             * @param node - 当前语法节点
             */
            enter(node) {
              // 更新父节点计数（用于嵌套检测）
              const currentDepth = parentTags.get(node.name) ?? 0;
              parentTags.set(node.name, currentDepth + 1);

              // 查找是否有匹配的替换扩展
              const spec = specMap.get(node.name);
              if (!spec) return;  // 未匹配，跳过

              // 获取显示策略和隐藏配置
              const strategy = spec.getRevealStrategy?.(node, view.state) ?? 'line';
              const hideOnSelection = spec.hideWhenContainsSelection ?? true;

              // 确定装饰范围（可能被扩展自定义）
              const rangeOverride = spec.getDecorationRange?.(node, view.state);
              let decoFrom: number;
              let decoTo: number;

              if (rangeOverride) {
                // 使用自定义范围
                decoFrom = rangeOverride[0];
                decoTo = rangeOverride.length === 2 ? rangeOverride[1] : rangeOverride[0];
              } else {
                // 使用节点默认范围
                decoFrom = node.from;
                decoTo = node.to;
              }

              // 确定显示策略使用的范围（可能与装饰范围不同）
              const revealRangeOverride = spec.getRevealRange?.(node, view.state);
              const revealFrom = revealRangeOverride ? revealRangeOverride[0] : decoFrom;
              const revealTo = revealRangeOverride ? revealRangeOverride[1] : decoTo;

              // 检查是否应该隐藏 widget 并显示源码
              // shouldReveal 会根据选区和策略判断
              if (hideOnSelection && shouldReveal(view.state, revealFrom, revealTo, strategy)) {
                return;  // 需要显示源码，跳过创建装饰
              }

              // 调用扩展创建装饰或 widget
              const result = spec.createDecoration(node, view.state, parentTags);
              if (!result) return;  // 返回 null，不渲染

              // 将结果转换为 Decoration 对象
              let decoration: Decoration;
              if (result instanceof WidgetType) {
                // Widget 类型：根据范围决定是替换还是插入
                if (decoFrom === decoTo) {
                  // 零宽度：插入 widget（如列表标记）
                  decoration = Decoration.widget({ widget: result, side: 1 });
                } else {
                  // 有宽度：替换内容（如加粗标记）
                  decoration = Decoration.replace({ widget: result });
                }
              } else {
                // 已经是 Decoration 对象，直接使用
                decoration = result;
              }

              // 保存装饰条目
              entries.push({ from: decoFrom, to: decoTo, decoration });
            },
            /**
             * 离开节点时的回调
             * 
             * 减少父节点计数，维护正确的嵌套层级。
             * 
             * @param node - 离开的语法节点
             */
            leave(node) {
              const depth = parentTags.get(node.name);
              if (depth !== undefined) {
                if (depth <= 1) {
                  // 最后一层，删除计数
                  parentTags.delete(node.name);
                } else {
                  // 还有嵌套，减少计数
                  parentTags.set(node.name, depth - 1);
                }
              }
            },
          });
        }

        // 按位置排序装饰条目（确保正确应用）
        entries.sort((a, b) => a.from - b.from || a.to - b.to);

        // 构建 RangeSet
        const builder = new RangeSetBuilder<Decoration>();
        for (const entry of entries) {
          builder.add(entry.from, entry.to, entry.decoration);
        }
        return builder.finish();
      }
    },
    {
      // 暴露 decorations 给 CodeMirror
      decorations: (value) => value.decorations,
    },
  );
}
