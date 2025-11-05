package middle;

import middle.symbol.SymbolRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * 符号记录器 (Singleton)
 * 职责：在第一遍符号收集中，按顺序记录所有被定义的符号，以便在编译成功时输出。
 */
public class SymbolLogger {
    private static final SymbolLogger instance = new SymbolLogger();
    private final List<SymbolRecord> records = new ArrayList<>();

    private SymbolLogger() {}

    public static SymbolLogger getInstance() {
        return instance;
    }

    /**
     * 记录一个新发现的符号。
     * @param record 包含作用域ID、名称和类型名称的记录对象。
     */
    public void record(SymbolRecord record) {
        records.add(record);
    }

    /**
     * 获取所有已记录的符号。
     * 因为我们是按声明顺序记录的，所以这个列表自然满足了排序要求。
     * @return 符号记录的列表。
     */
    public List<SymbolRecord> getRecords() {
        return records;
    }

    /**
     * 清空所有记录，用于多次编译运行的场景。
     */
    public void clear() {
        records.clear();
    }
}
