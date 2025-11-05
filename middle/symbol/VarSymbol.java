package middle.symbol;

import middle.component.InitialValue;
import middle.component.type.Type;

public class VarSymbol extends Symbol {
    private final boolean isConstant;
    private final boolean isStatic;
    private final InitialValue initialValue;

    public VarSymbol(String name, Type type, boolean isConstant,
                     boolean isStatic, InitialValue initialValue,int defineLine) { // <--- 在构造函数中添加
        super(name, type,defineLine);
        this.isConstant = isConstant;
        this.isStatic = isStatic; // <--- 在这里初始化
        this.initialValue = initialValue;
    }

    public boolean isConstant() {
        return isConstant;
    }

    public boolean isStatic() { // <--- 添加 getter
        return isStatic;
    }

    public InitialValue getInitialValue() {
        return initialValue;
    }

    public int getDefineLine() {
        return defineLine;
    }
}
