// ----------------------------------------------------
// 文件: middle/component/model/Value.java
// ----------------------------------------------------
package middle.component.model;

import middle.component.type.Type; // 确保您使用的是新Type接口

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * IR 中所有“值”的基类 (新版本，带 Use)。
 */
public class Value { // 设为 abstract，与您的源代码不同

    /**
     * 属性：这个值的类型。
     */
    private final Type type;

    /**
     * 属性：这个值的名字 (如 %v1, %arg1, @g_var)。
     */
    private String name;

    /**
     * 策略：使用 LinkedList 存储“使用列表”(Use List)。
     * 记录了 *哪些 Use* 对象使用了 *这个 Value*。
     * (这与您源代码的 ArrayList<User> 完全不同)
     */
    private final LinkedList<Use> useList;

    public Value(Type type) {
        this.type = type;
        this.useList = new LinkedList<>();
        this.name = "";
    }

    public Type getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * (包内可见或 public) 添加一个“使用”。
     * 这个方法由 Use 类的构造函数调用。
     */
    public void addUse(Use use) {
        // 提示：将 'use' 添加到 this.useList 中
        this.useList.add(use);
    }

    /**
     * (包内可见或 public) 移除一个“使用”。
     * 这个方法由 Use 类的析构/清理方法调用 (见 User.setOperand)。
     */
    public void removeUse(Use use) {
        // 提示：从 this.useList 中移除 'use'
        this.useList.remove(use);
    }

    public LinkedList<Use> getUseList() {
        return useList;
    }

    public ArrayList<User> getUsers() {
        ArrayList<User> users = new ArrayList<>();
        for (Use use : useList) {
            users.add(use.getUser());
        }
        return users;
    }

    /**
     * 替换所有对 *这个* Value 的使用，改为使用 *newValue*。
     * (这对应于您源代码中的 replaceByNewValue)
     */
    public void replaceAllUsesWith(Value newValue) {
        // 提示：
        // 1. 必须复制列表，因为在遍历时 this.useList 会被修改
        LinkedList<Use> usesToUpdate = new LinkedList<>(this.useList);

        // 2. 遍历所有“使用”
        for (Use use : usesToUpdate) {
            User user = use.getUser();
            user.replaceOperandFromUse(use, newValue);
        }
    }

    @Override
    public String toString() {
        // 提示：返回 "Type Name" 的格式
        return this.type.toString() + " " + this.name;
    }
}
