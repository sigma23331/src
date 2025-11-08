package middle.component.type;

import java.util.HashMap;
import java.util.Map;

public class ArrayType implements Type {

    /**
     * 属性：数组元素是什么类型
     */
    private final Type elementType;

    /**
     * 属性：数组有多少个元素
     */
    private final int numElements;

    /**
     * 策略：使用缓存 (享元模式)
     * 这是一个嵌套Map：Map<ElementType, Map<NumElements, ArrayType>>
     * 确保 [10 x i32] 永远是同一个对象
     */
    private static final Map<Type, Map<Integer, ArrayType>> cache = new HashMap<>();

    /**
     * 构造函数设为私有，强制使用 .get() 方法
     */
    public ArrayType(Type elementType, int numElements) {
        this.elementType = elementType;
        this.numElements = numElements;
    }

    /**
     * 静态工厂方法：获取 ArrayType 实例
     */
    public static ArrayType get(Type elementType, int numElements) {
        // 提示：使用 computeIfAbsent 来安全地操作嵌套 Map
        // 1. 查找或创建第一层 Map (基于 elementType)
        Map<Integer, ArrayType> innerMap = cache.computeIfAbsent(
            elementType,
            k -> new HashMap<>()
        );
        // 2. 查找或创建第二层 Map (基于 numElements)，并返回最终的 ArrayType
        //    lambda (k -> new ArrayType(...)) 只会在实例不存在时被调用
        return innerMap.computeIfAbsent(
            numElements,
            k -> new ArrayType(elementType, numElements)
        );
    }

    public Type getElementType() {
        return elementType;
    }

    public int getNumElements() {
        return numElements;
    }

    @Override
    public String toString() {
        // 提示：返回 "[NumElements x ElementType]" 格式的字符串
        return "[" + numElements + " x " + elementType.toString() + "]";
    }
}