package javax.swing.tree;

/**
 * 极简替代实现（仅 Android 使用），见 {@link TreeNode}。
 */
public interface MutableTreeNode extends TreeNode {

    void insert(MutableTreeNode child, int index);

    void remove(int index);

    void remove(MutableTreeNode node);

    void setUserObject(Object object);

    void removeFromParent();

    void setParent(MutableTreeNode newParent);
}
