package middle;

import middle.symbol.Symbol;
import middle.symbol.SymbolType;

import java.util.ArrayList;
import java.util.LinkedHashMap;

// 维护符号表
public class SymbolTable {
    private static int counter = 0;
    private final LinkedHashMap<String, Symbol> symbols = new LinkedHashMap<>();
    private final SymbolTable parent;
    private final SymbolType returnType;
    private final ArrayList<SymbolTable> children = new ArrayList<>();

    public SymbolTable(SymbolType returnType,SymbolTable parent) {
        this.returnType = returnType;
        this.parent = parent;
    }

    public void addChild(SymbolTable newTable) {
        children.add(newTable);
    }

    public SymbolTable getParent() {
        return this.parent;
    }
}
