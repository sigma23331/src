package middle.component.model;

import middle.component.type.ArrayType;
import middle.component.type.FunctionType;
import middle.component.type.PointerType;
import middle.component.type.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * 函数 (Function)。
 * * 关键区别：
 * 1. 继承自 Value (不是 User)。
 * 2. 构造函数干净，无副作用 (不依赖 IRData)。
 * 3. 不包含后端 (var2reg) 或分析 (getPostOrder) 代码。
 * 4. 使用 LinkedList 存储 BasicBlock。
 * 5. 使用 isDeclaration (不是 isBuiltIn)。
 */
public class Function extends Value {

    private final Type returnType;
    private final ArrayList<FuncParam> params;
    private final LinkedList<BasicBlock> basicBlocks;

    /**
     * 属性：这是否只是一个“声明” (例如 declare i32 @getint())
     */
    private final boolean isDeclaration;

    /**
     * 构造函数 (用于“定义”或“声明”)
     * @param name 函数名 (例如 @main)
     * @param returnType 返回类型
     * @param paramTypes 参数类型列表 (用于创建 FuncParam)
     * @param isDeclaration 这是声明(true)还是定义(false)
     */
    public Function(String name, Type returnType, ArrayList<Type> paramTypes, boolean isDeclaration) {
        // // 提示：
        // // 1. 创建此函数的 FunctionType
        super(returnType);
        this.returnType = returnType;
        this.setName(name);
        this.isDeclaration = isDeclaration;
        this.basicBlocks = new LinkedList<>();
        this.params = new ArrayList<>();
        // // 2. 根据 paramTypes 自动创建 FuncParam 对象
        ArrayList<Type> irParamTypes = new ArrayList<>();

        for (int i = 0; i < paramTypes.size(); i++) {
            Type astType = paramTypes.get(i);
            Type irType; // 這是我們將要創建的 FuncParam 的類型

            if (astType instanceof ArrayType) {
                // --- 情況 A：數組參數 (int a[]) ---
                // (astType 是 ArrayType(i32, -1))
                // *必須* 將其退化為指針 (i32*)
                irType = PointerType.get(((ArrayType) astType).getElementType());
            } else {
                // --- 情況 B：標量參數 (int n) ---
                irType = astType;
            }

            // 將 *正確* 的 IR 類型添加到列表中
            irParamTypes.add(irType);

            // 創建 FuncParam (現在使用 irType)
            FuncParam param = new FuncParam(irType, "", i);
            param.setParent(this);
            this.params.add(param);
        }
    }

    // --- Getters ---
    public Type getReturnType() { return this.returnType; }
    public ArrayList<FuncParam> getParams() { return this.params; }
    public LinkedList<BasicBlock> getBasicBlocks() { return this.basicBlocks; }
    public boolean isDeclaration() { return this.isDeclaration; }

    /**
     * 由 IRBuilder 或 BasicBlock 构造函数调用
     */
    public void addBasicBlock(BasicBlock bb) {
        // // 提示：
        this.basicBlocks.add(bb);
        bb.setParent(this); // 确保双向链接
    }

    public BasicBlock getEntryBlock() {
        // // 提示：
        // // 获取第一个基本块 (入口)
        return this.basicBlocks.isEmpty() ? null : this.basicBlocks.getFirst();
    }

    @Override
    public String toString() {
        // // 提示：
        // // 1. 拼装参数字符串 (例如 "i32 %arg0, i32 %arg1")
        String paramStr = this.params.stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "));
        if (this.isDeclaration) {
            // 2. 如果是“声明”
            // 例如: "declare i32 @getint()"
            return "declare " + this.returnType.toString() + " @" +
                   this.getName() + "(" + paramStr + ")";
        } else {
            // 3. 如果是“定义”
            // 拼装所有 BasicBlock 的 toString()
            String bbStr = this.basicBlocks.stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
            // 例如: "define dso_local i32 @main() { ... }"
            return "define dso_local " + this.returnType.toString() + " @" +
                   this.getName() + "(" + paramStr + ") {\n" +
                   bbStr +
                   "\n}";
        }
    }
}
