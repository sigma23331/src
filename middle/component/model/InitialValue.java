package middle.component.model;

import middle.component.type.Type;

import java.util.ArrayList;

/**
 * InitialValue 类用于封装变量在声明时的初始值信息。
 * 在语义分析阶段，它的主要职责是作为一个数据容器，
 * 完整地存储从源代码解析出的初始值，以便进行类型检查、常量求值等操作。
 */
public class InitialValue {

    /**
     * 变量的数据类型（例如 i32 或一个数组类型）。
     * 在语义分析时用于类型检查。
     */
    private final Type valueType;

    /**
     * 变量的总长度（对于数组而言）。
     * 例如 int arr[5]，length 就是 5。
     */
    private final int length;

    /**
     * 一个整数列表，存储源代码中明确提供的初始值。
     * - 对于 int a = 10; -> [10]
     * - 对于 int b[3] = {1, 2}; -> [1, 2]
     * - 对于 int c; (未初始化) -> null
     * 在语义分析时，此列表用于常量求值和检查初始值数量是否越界。
     */
    private final ArrayList<Integer> elements;

    /**
     * 构造函数。
     * 语义分析器（Visitor）在遍历AST时，会收集所需信息并调用此构造函数来创建对象。
     *
     * @param valueType 变量的数据类型。
     * @param length    变量声明的总长度。
     * @param elements  从源代码解析出的初始值列表；如果未初始化，则为 null。
     */
    public InitialValue(Type valueType, int length, ArrayList<Integer> elements) {
        this.valueType = valueType;
        this.length = length;
        this.elements = elements;
    }

    /**
     * 获取变量的数据类型。
     * 主要用于语义检查，确保初始值类型与变量声明类型兼容。
     *
     * @return ValueType 对象。
     */
    public Type getValueType() {
        return valueType;
    }

    /**
     * 获取初始值列表。
     * 在语义分析阶段至关重要：
     * 1. 用于常量求值（例如 const int a = 10;）。
     * 2. 用于推断数组长度（例如 int arr[] = {1,2,3};）。
     * 3. 用于检查初始值数量是否超过数组声明长度。
     *
     * @return 包含初始值的 ArrayList<Integer>，如果未初始化则返回 null。
     */
    public ArrayList<Integer> getElements() {
        return elements;
    }

    /**
     * 获取变量声明的总长度。
     *
     * @return 变量的长度。
     */
    public int getLength() {
        return length;
    }
}
