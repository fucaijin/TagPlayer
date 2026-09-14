package javax.swing.tree;

/**
 * 极简替代实现（仅 Android 使用），见 {@link TreeNode}。
 */
public class DefaultTreeModel {

    protected TreeNode root;

    public DefaultTreeModel(TreeNode root) {
        if (root == null) {
            throw new IllegalArgumentException("root cannot be null");
        }
        this.root = root;
    }

    public TreeNode getRoot() {
        return root;
    }

    public void setRoot(TreeNode root) {
        this.root = root;
    }
}
