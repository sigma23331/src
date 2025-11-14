package middle.component.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class Module {

    private static final Module instance = new Module();

    /**
     * 属性：模块中的所有“函数声明” (例如 @getint)
     * (替换了您代码中的 builtInFunctions)
     */
    private final ArrayList<Function> declarations;

    /**
     * 属性：模块中的所有字符串常量 (例如 @.str.0)
     */
    private final ArrayList<ConstString> constStrings;

    /**
     * 属性：模块中的所有全局变量 (例如 @g)
     */
    private final ArrayList<GlobalVar> globalVars;

    /**
     * 属性：模块中的所有“函数定义” (例如 @main)
     */
    private final ArrayList<Function> functions;

    // --- 字符串常量缓存 (新) ---
    private final Map<String, ConstString> constStringCache;
    private int strNameCounter;

    /**
     * 私有构造函数 (与您的代码一致)
     */
    private Module() {
        this.declarations = new ArrayList<>();
        this.constStrings = new ArrayList<>();
        this.globalVars = new ArrayList<>();
        this.functions = new ArrayList<>();
        this.constStringCache = new HashMap<>();
        this.strNameCounter = 0;
    }

    public static Module getInstance() {
        return instance;
    }

    // --- 列表管理 (由 IRBuilder 调用) ---

    public void addDeclaration(Function func) {
        this.declarations.add(func);
    }

    public void addGlobalVar(GlobalVar gv) {
        this.globalVars.add(gv);
    }

    public void addFunction(Function func) {
        this.functions.add(func);
    }

    /**
     * (新) 获取或创建 ConstString 实例。
     * * 关键区别：这是 IRBuilder 创建字符串的唯一入口。
     * 它取代了您 ConstString 构造函数中的副作用。
     * * @param rawString 原始 Java 字符串 (例如 "Hello\n")
     * @return 缓存的或新创建的 ConstString 对象
     */
    public ConstString getOrAddConstString(String rawString) {

        // 1. 缓存的 Key *必须* 是原始字符串 (rawString)
        return this.constStringCache.computeIfAbsent(rawString, k -> {
            // --- (Lambda: 仅在缓存未命中时执行) ---
            // k 就是 rawString (例如 "Hello\n")

            // 2. 【新】处理逻辑：转义
            String processed = k.replace("\\n", "\\0A");
            String length =  k.replace("\\n", "1");
            String llvmString = processed + "\\00"; // (例如 "Hello\0A\00")

            // 3. 【新】处理逻辑：计算 *正确* 的字节长度
            //    (基于 *原始* 字符串的长度 + 1)
            int byteLength = length.length() + 1; // (例如 "Hello\n" -> 6 + 1 = 7)

            // 4. 创建名字
            String name = "@.str." + (this.strNameCounter++);

            // 5. 【新】调用 *新* 的 ConstString 构造函数
            ConstString cs = new ConstString(name, llvmString, byteLength);

            // 6. 添加到 *模块的列表* 中，以便最终打印
            this.constStrings.add(cs);

            // 7. 返回新对象 (它会被放入 cache)
            return cs;
        });
    }

    // --- Getters ---

    public ArrayList<Function> getDeclarations() { return this.declarations; }
    public ArrayList<ConstString> getConstStrings() { return this.constStrings; }
    public ArrayList<GlobalVar> getGlobalVars() { return this.globalVars; }
    public ArrayList<Function> getFunctions() { return this.functions; }

    @Override
    public String toString() {
        // // 提示：
        // // 1. 打印所有声明
        String declStr = this.declarations.stream()
            .map(Object::toString)
            .collect(Collectors.joining("\n"));
        // 2. 打印所有字符串
        String strStr = this.constStrings.stream()
            .map(Object::toString)
            .collect(Collectors.joining("\n"));
        // 3. 打印所有全局变量
        String gvStr = this.globalVars.stream()
            .map(Object::toString)
            .collect(Collectors.joining("\n"));
        // 4. 打印所有函数定义
        String funcStr = this.functions.stream()
            .map(Object::toString)
            .collect(Collectors.joining("\n\n"));
        // 5. (确保在非空字符串之间添加换行符)
        StringJoiner joiner = new StringJoiner("\n\n");
        if (!declStr.isEmpty()) joiner.add(declStr);
        if (!strStr.isEmpty()) joiner.add(strStr);
        if (!gvStr.isEmpty()) joiner.add(gvStr);
        if (!funcStr.isEmpty()) joiner.add(funcStr);
        return joiner.toString();
    }
}
