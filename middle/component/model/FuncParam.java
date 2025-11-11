package middle.component.model;

import middle.component.type.Type;


/**
 * 函数参数 (Function Parameter / Argument)。
 * 它是一个 Value。
 */
public class FuncParam extends Value {

    /**
     * 属性：这个参数属于哪个函数。
     */
    private Function parent;

    /**
     * 属性：这个参数是第几个参数 (索引)。
     */
    private final int index;

    /**
     * 构造函数。
     */
    public FuncParam(Type type, String name, int index) {
        super(type);
        this.setName(name);
        this.index = index;
        this.parent = null; // 将由 Function 的构造函数设置
    }

    public Function getParent() {
        return this.parent;
    }

    public int getIndex() {
        return this.index;
    }

    /**
     * 由 Function 构造函数或 addParam 调用
     */
    public void setParent(Function parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        // // 提示：
        // // 在函数定义中，参数显示为 "Type Name"
        // // 例如: "i32 %arg1"
        return this.getType().toString() + " " + this.getName();
    }
}
