package middle.symbol;
import middle.component.model.Value;
import middle.component.type.Type;

public abstract class Symbol {
    private final String name;
    private final Type type;
    protected final int defineLine;
    private Value irValue;

    public Symbol(String name, Type type,int defineLine) {
        this.name = name;
        this.type = type;
        this.defineLine = defineLine;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public int getDefineLine() {
        return defineLine;
    }

    /**
     * (由 IRBuilder 调用) 链接此符号到它对应的 IR Value。
     */
    public void setIrValue(Value irValue) {
        this.irValue = irValue;
    }

    /**
     * (由 IRBuilder 调用) 获取此符号链接的 IR Value。
     */
    public Value getIrValue() {
        if (this.irValue == null) {
            throw new RuntimeException("Symbol '" + name + "' has not been linked to an IR Value.");
        }
        return this.irValue;
    }
}
