package middle.symbol;


import middle.component.type.FunctionType;

import java.util.ArrayList;

public class FunctionSymbol extends Symbol {
    // 我们可以直接把参数名也存进来
    private final ArrayList<String> paramNames;

    public FunctionSymbol(String name, FunctionType type, ArrayList<String> paramNames,int defineLine) {
        super(name, type,defineLine);
        this.paramNames = paramNames;
    }

    @Override
    public FunctionType getType() {
        // 重载父类的方法，返回更具体的类型
        return (FunctionType) super.getType();
    }

    public java.util.List<String> getParamNames() {
        return paramNames;
    }

    public int getDefineLine() {
        return defineLine;
    }
}
