package javax.swing.tree;

/**
 * 极简替代实现（仅 Android 使用）。
 *
 * jaudiotagger 2.0.1 的 MP4 读写实现（Mp4InfoReader/Mp4AtomTree/Mp4TagWriter）内部依赖
 * {@code javax.swing.tree.*} 来构建 atom 树，而 Android 平台没有 Swing，
 * 导致读取/写入 MP4(m4a/aac) 标签时抛 NoClassDefFoundError。
 * 这里提供行为等价的最小实现（只覆盖 jaudiotagger 实际用到的 API）。
 */
public interface TreeNode {

    TreeNode getParent();
}
