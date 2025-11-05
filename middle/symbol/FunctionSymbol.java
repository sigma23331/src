package middle.symbol;


import middle.component.type.FunctionType;

public class FunctionSymbol extends Symbol {
    // 我们可以直接把参数名也存进来
    private final java.util.List<String> paramNames;

    public FunctionSymbol(String name, FunctionType type, java.util.List<String> paramNames,int defineLine) {
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
