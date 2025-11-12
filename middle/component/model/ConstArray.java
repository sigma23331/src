package middle.component.model;

import middle.component.type.ArrayType;
import middle.component.type.Type;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 常量数组 (例如 [i32 10, i32 20])。
 * 继承自 Constant。
 */
public class ConstArray extends Constant {

    /**
     * 属性：数组的元素列表，它们 *必须* 也是 Constant。
     */
    private final ArrayList<Constant> elements;

    /**
     * 构造函数。
     * @param type (必须是 ArrayType)
     * @param elements 常量元素列表
     */
    public ConstArray(ArrayType type, ArrayList<Constant> elements) {
        super(type);
        this.elements = elements;
    }

    public ArrayList<Constant> getElements() {
        return this.elements;
    }

    @Override
    public String toString() {
        // 提示：
        // 1. 拼装所有元素的 toString()
        //    例如: "i32 10, i32 20"
        String elemsStr = this.elements.stream()
            .map(elem -> elem.getType().toString() + " " + elem.toString())
            .collect(Collectors.joining(", "));
        // 2. 拼装数组
        //    例如: "[i32 10, i32 20]"
        return "[" + elemsStr + "]";
    }
}