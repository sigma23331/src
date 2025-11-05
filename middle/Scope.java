package middle;

import middle.symbol.Symbol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 代表作用域树中的一个节点。这是一个持久的数据结构。
 */
public class Scope {
    private final int id;                  // 作用域的唯一ID (用于 symbol.txt 输出)
    private final Scope parent;            // 指向父作用域 (null 代表全局作用域)
    private final HashMap<String, Symbol> symbols; // 当前作用域【本地】定义的符号
    private final List<Scope> children;    // 子作用域列表

    public Scope(int id, Scope parent) {
        this.id = id;
        this.parent = parent;
        this.symbols = new HashMap<>();
        this.children = new ArrayList<>();
    }

    // --- Getters ---
    public int getId() { return id; }
    public Scope getParent() { return parent; }

    /**
     * 添加一个子作用域
     */
    public void addChild(Scope child) {
        children.add(child);
    }

    /**
     * 在当前作用域【本地】定义一个符号
     */
    public void addSymbol(Symbol symbol) {
        symbols.put(symbol.getName(), symbol);
    }

    /**
     * 在当前作用域【本地】查找一个符号
     */
    public Symbol lookupLocally(String name) {
        return symbols.get(name);
    }
}
