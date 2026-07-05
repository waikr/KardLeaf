/**
 * 编辑器设置扩展 - 动态配置管理
 * 
 * **功能：**
 * 通过 CodeMirror 的 Compartment 机制实现运行时动态更新编辑器设置。
 * 支持修改可编辑性、行号、换行、只读模式、拼写检查、Tab 大小和主题等。
 * 
 * **核心技术：**
 * - Compartment：CodeMirror 的动态配置容器，允许运行时重新配置
 * - StateField：持久化存储当前设置状态
 * - StateEffect：触发设置更新的单次效果
 */
import {
  Compartment,
  EditorState,
  StateEffect,
  StateField,
  type Extension,
} from '@codemirror/state';
import { EditorView, lineNumbers } from '@codemirror/view';
import type { EditorSettings, EditorSettingsUpdate } from '../types';
import { DEFAULT_SETTINGS } from '../types';
import { createEditorTheme } from '../theme/createTheme';

/** 编辑器设置的 Compartment 集合 */
export interface EditorSettingsCompartments {
  /** 可编辑性配置 */
  editable: Compartment;
  /** 行号显示配置 */
  lineNumbers: Compartment;
  /** 自动换行配置 */
  lineWrapping: Compartment;
  /** 只读模式配置 */
  readOnly: Compartment;
  /** 拼写检查配置 */
  spellcheck: Compartment;
  /** Tab 大小配置 */
  tabSize: Compartment;
  /** 主题配置 */
  theme: Compartment;
}

/** 编辑器设置扩展运行时对象 */
export interface EditorSettingsExtensionRuntime {
  /** Compartment 集合 */
  compartments: EditorSettingsCompartments;
  /** 扩展数组 */
  extension: Extension;
  /** 设置状态字段 */
  field: StateField<EditorSettings>;
}

/**
 * 合并设置 - 深度合并策略
 * 
 * **合并规则：**
 * 1. 顶层属性：update 覆盖 current
 * 2. features 对象：深度合并
 * 3. theme 对象：深度合并
 * 4. theme.colors：更深层次的合并
 * 
 * @param current - 当前设置
 * @param update - 要应用的更新
 * @returns 合并后的新设置
 */
function mergeSettings(
  current: EditorSettings,
  update: EditorSettingsUpdate,
): EditorSettings {
  return {
    ...current,
    ...update,
    // 深度合并 features
    features: {
      ...current.features,
      ...update.features,
    },
    // 深度合并 theme
    theme: {
      ...current.theme,
      ...update.theme,
      // 更深层次合并 colors
      colors: {
        ...current.theme.colors,
        ...update.theme?.colors,
      },
    },
  };
}

/**
 * 根据设置创建扩展数组
 * 
 * @param settings - 编辑器设置
 * @returns 包含所有配置扩展的对象
 */
function createSettingsExtensions(settings: EditorSettings) {
  return {
    // 可编辑性：editable 为 true 且 readonly 为 false 时可编辑
    editable: EditorView.editable.of(settings.editable && !settings.readonly),
    // 行号显示
    lineNumbers: settings.showLineNumbers ? lineNumbers() : [],
    // 自动换行
    lineWrapping: settings.lineWrapping ? EditorView.lineWrapping : [],
    // 只读模式
    readOnly: EditorState.readOnly.of(settings.readonly),
    // 拼写检查
    spellcheck: EditorView.contentAttributes.of({
      spellcheck: settings.spellcheck ? 'true' : 'false',
    }),
    // Tab 大小
    tabSize: EditorState.tabSize.of(settings.tabSize),
    // 主题
    theme: createEditorTheme(settings.theme),
  };
}

/**
 * 创建 Compartment 集合
 * 
 * @returns 包含 7 个 Compartment 的对象
 */
function createCompartments(): EditorSettingsCompartments {
  return {
    editable: new Compartment(),
    lineNumbers: new Compartment(),
    lineWrapping: new Compartment(),
    readOnly: new Compartment(),
    spellcheck: new Compartment(),
    tabSize: new Compartment(),
    theme: new Compartment(),
  };
}

/**
 * 创建设置重新配置的 Effects 数组
 * 
 * **工作原理：**
 * 对每个 Compartment 调用 reconfigure()，传入新的扩展配置。
 * CodeMirror 会在下一个事务中应用这些重新配置。
 * 
 * @param compartments - Compartment 集合
 * @param settings - 新的编辑器设置
 * @returns Effects 数组
 */
function createSettingsReconfigureEffects(
  compartments: EditorSettingsCompartments,
  settings: EditorSettings,
) {
  const extensions = createSettingsExtensions(settings);

  return [
    compartments.editable.reconfigure(extensions.editable),
    compartments.lineNumbers.reconfigure(extensions.lineNumbers),
    compartments.lineWrapping.reconfigure(extensions.lineWrapping),
    compartments.readOnly.reconfigure(extensions.readOnly),
    compartments.spellcheck.reconfigure(extensions.spellcheck),
    compartments.tabSize.reconfigure(extensions.tabSize),
    compartments.theme.reconfigure(extensions.theme),
  ];
}

/** 设置编辑器设置的 Effect - 用于触发状态更新 */
export const setEditorSettingsEffect = StateEffect.define<EditorSettingsUpdate>();

/**
 * 创建编辑器设置扩展
 * 
 * **工作流程：**
 * 1. 创建 7 个 Compartment（每个设置项一个）
 * 2. 合并默认设置和初始设置
 * 3. 创建 StateField 持久化存储设置状态
 * 4. 根据初始设置创建扩展并挂载到 Compartment
 * 5. 返回运行时对象（包含 compartments、extension、field）
 * 
 * @param initialSettings - 初始设置
 * @returns 运行时对象
 */
export function createEditorSettingsExtension(
  initialSettings: EditorSettings,
): EditorSettingsExtensionRuntime {
  // 创建 Compartment 集合
  const compartments = createCompartments();
  // 合并默认设置和初始设置
  const mergedInitialSettings = mergeSettings(DEFAULT_SETTINGS, initialSettings);

  // 创建设置状态字段
  const field = StateField.define<EditorSettings>({
    create() {
      // 初始化时返回合并后的设置
      return mergedInitialSettings;
    },
    update(value, transaction) {
      // 遍历事务中的所有 effects
      for (const effect of transaction.effects) {
        if (effect.is(setEditorSettingsEffect)) {
          // 找到设置更新 effect，合并后返回新设置
          return mergeSettings(value, effect.value);
        }
      }

      return value;  // 无变化时返回原值
    },
  });

  // 根据初始设置创建扩展
  const initialExtensions = createSettingsExtensions(mergedInitialSettings);

  return {
    compartments,
    // 扩展数组：StateField + 7 个 Compartment 的初始配置
    extension: [
      field,
      compartments.editable.of(initialExtensions.editable),
      compartments.lineNumbers.of(initialExtensions.lineNumbers),
      compartments.lineWrapping.of(initialExtensions.lineWrapping),
      compartments.readOnly.of(initialExtensions.readOnly),
      compartments.spellcheck.of(initialExtensions.spellcheck),
      compartments.tabSize.of(initialExtensions.tabSize),
      compartments.theme.of(initialExtensions.theme),
    ],
    field,
  };
}

/**
 * 获取当前编辑器设置
 * 
 * @param state - 编辑器状态
 * @param runtime - 运行时对象
 * @returns 当前设置
 */
export function getEditorSettings(
  state: EditorState,
  runtime: EditorSettingsExtensionRuntime,
): EditorSettings {
  return state.field(runtime.field);
}

/**
 * 获取设置更新的 Effects 数组
 * 
 * **工作流程：**
 * 1. 获取当前设置
 * 2. 合并更新得到新设置
 * 3. 创建两个 effect：
 *    - setEditorSettingsEffect：更新 StateField 中的设置
 *    - reconfigure effects：重新配置所有 Compartment
 * 
 * @param state - 编辑器状态
 * @param runtime - 运行时对象
 * @param update - 要应用的更新
 * @returns Effects 数组
 */
export function getEditorSettingsEffects(
  state: EditorState,
  runtime: EditorSettingsExtensionRuntime,
  update: EditorSettingsUpdate,
) {
  // 计算新设置
  const nextSettings = mergeSettings(getEditorSettings(state, runtime), update);

  return [
    // Effect 1：更新 StateField 中的设置
    setEditorSettingsEffect.of(update),
    // Effect 2-8：重新配置所有 Compartment
    ...createSettingsReconfigureEffects(runtime.compartments, nextSettings),
  ];
}
