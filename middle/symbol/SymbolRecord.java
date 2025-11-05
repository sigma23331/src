package middle.symbol;

import java.util.Comparator;

/**
 * 一个简单的数据类，用于存储符合输出要求的符号信息。
 */
public class SymbolRecord {
    private final int scopeId;
    private final String name;
    private final String typeName;

    public SymbolRecord(int scopeId, String name, String typeName) {
        this.scopeId = scopeId;
        this.name = name;
        this.typeName = typeName;
    }

    public int getScopeId() {
        return scopeId;
    }

    @Override
    public String toString() {
        return scopeId + " " + name + " " + typeName;
    }
}
