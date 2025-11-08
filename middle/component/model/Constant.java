package middle.component.model;

import middle.component.type.Type;

/**
 * 所有“常量”的抽象基类。
 * 常量是一种特殊的 Value，它没有操作数 (Operands)。
 * 它 *不* 继承 User。
 */
public abstract class Constant extends Value {

    /**
     * 构造函数。
     */
    public Constant(Type type) {
        super(type); // 调用 Value(Type type) 构造函数
    }
}
