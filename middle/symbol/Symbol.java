package middle.symbol;
import middle.component.type.Type;

public abstract class Symbol {
    private final String name;
    private final Type type;
    protected final int defineLine;

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
}
