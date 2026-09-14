package javax.swing.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 极简替代实现（仅 Android 使用），见 {@link TreeNode}。
 *
 * 方法签名需与 Swing 保持一致（jaudiotagger 的字节码按 Swing 的签名调用），
 * 例如 getParent() 返回 TreeNode、getPreviousSibling() 返回 DefaultMutableTreeNode。
 */
public class DefaultMutableTreeNode implements MutableTreeNode {

    protected Object userObject;
    protected MutableTreeNode parent;
    protected List<MutableTreeNode> children;
    protected boolean allowsChildren;

    public DefaultMutableTreeNode() {
        this(null, true);
    }

    public DefaultMutableTreeNode(Object userObject) {
        this(userObject, true);
    }

    public DefaultMutableTreeNode(Object userObject, boolean allowsChildren) {
        this.userObject = userObject;
        this.allowsChildren = allowsChildren;
    }

    @Override
    public void insert(MutableTreeNode child, int index) {
        if (!allowsChildren) {
            throw new IllegalStateException("node does not allow children");
        }
        if (child == null) {
            throw new IllegalArgumentException("new child is null");
        }
        child.removeFromParent();
        child.setParent(this);
        childList().add(index, child);
    }

    @Override
    public void remove(int index) {
        MutableTreeNode child = childList().remove(index);
        child.setParent(null);
    }

    @Override
    public void remove(MutableTreeNode node) {
        if (node == null) {
            return;
        }
        int index = getIndex(node);
        if (index >= 0) {
            remove(index);
        }
    }

    @Override
    public void setUserObject(Object object) {
        this.userObject = object;
    }

    @Override
    public void removeFromParent() {
        MutableTreeNode p = parent;
        if (p instanceof DefaultMutableTreeNode) {
            ((DefaultMutableTreeNode) p).remove(this);
        } else {
            this.parent = null;
        }
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        this.parent = newParent;
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    public TreeNode getChildAt(int index) {
        return childList().get(index);
    }

    public int getChildCount() {
        return children == null ? 0 : children.size();
    }

    public int getIndex(TreeNode node) {
        for (int i = 0; i < getChildCount(); i++) {
            if (children.get(i) == node) {
                return i;
            }
        }
        return -1;
    }

    public boolean getAllowsChildren() {
        return allowsChildren;
    }

    public void setAllowsChildren(boolean allows) {
        this.allowsChildren = allows;
    }

    public boolean isLeaf() {
        return getChildCount() == 0;
    }

    public Object getUserObject() {
        return userObject;
    }

    /** 先序遍历（Mp4AtomTree 用于查找指定原子节点） */
    public Enumeration preorderEnumeration() {
        List<Object> nodes = new ArrayList<>();
        collectPreorder(this, nodes);
        return Collections.enumeration(nodes);
    }

    private static void collectPreorder(DefaultMutableTreeNode node, List<Object> out) {
        out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            TreeNode child = node.getChildAt(i);
            if (child instanceof DefaultMutableTreeNode) {
                collectPreorder((DefaultMutableTreeNode) child, out);
            } else {
                out.add(child);
            }
        }
    }

    public boolean isNodeAncestor(TreeNode anotherNode) {
        if (anotherNode == null) {
            return false;
        }
        TreeNode ancestor = this;
        do {
            if (ancestor == anotherNode) {
                return true;
            }
        } while ((ancestor = ancestor.getParent()) != null);
        return false;
    }

    public boolean isRoot() {
        return getParent() == null;
    }

    public TreeNode getRoot() {
        TreeNode ancestor = this;
        TreeNode next;
        while ((next = ancestor.getParent()) != null) {
            ancestor = next;
        }
        return ancestor;
    }

    public int getLevel() {
        TreeNode p = getParent();
        if (p instanceof DefaultMutableTreeNode) {
            return ((DefaultMutableTreeNode) p).getLevel() + 1;
        }
        return 0;
    }

    public DefaultMutableTreeNode getPreviousSibling() {
        MutableTreeNode p = parent;
        if (p instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) p;
            int index = parentNode.getIndex(this) - 1;
            if (index >= 0) {
                TreeNode sibling = parentNode.getChildAt(index);
                if (sibling instanceof DefaultMutableTreeNode) {
                    return (DefaultMutableTreeNode) sibling;
                }
            }
        }
        return null;
    }

    public DefaultMutableTreeNode getNextSibling() {
        MutableTreeNode p = parent;
        if (p instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) p;
            int index = parentNode.getIndex(this) + 1;
            if (index < parentNode.getChildCount()) {
                TreeNode sibling = parentNode.getChildAt(index);
                if (sibling instanceof DefaultMutableTreeNode) {
                    return (DefaultMutableTreeNode) sibling;
                }
            }
        }
        return null;
    }

    /** 添加子节点（若已在本节点下则移动到末尾） */
    public void add(MutableTreeNode newChild) {
        if (newChild == null) {
            return;
        }
        if (newChild.getParent() == this) {
            insert(newChild, getChildCount() - 1);
        } else {
            insert(newChild, getChildCount());
        }
    }

    private List<MutableTreeNode> childList() {
        if (children == null) {
            children = new ArrayList<>();
        }
        return children;
    }

    @Override
    public String toString() {
        return userObject == null ? "" : userObject.toString();
    }
}
