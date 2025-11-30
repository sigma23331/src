package middle.component.model;

import backend.enums.Register; // 引入后端的寄存器枚举
import middle.component.type.ArrayType;
import middle.component.type.PointerType;
import middle.component.type.Type;
import middle.component.type.VoidType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 函数 (Function)。
 */
public class Function extends Value {

    private final Type returnType;
    private final ArrayList<FuncParam> params;
    private final LinkedList<BasicBlock> basicBlocks;
    private final boolean isDeclaration;

    // ==========================================
    // 后端优化所需字段 (RegAlloc 结果)
    // ==========================================
    /**
     * 变量到物理寄存器的映射结果。
     * 由 RegAlloc Pass 填充，由 MipsBuilder 使用。
     */
    private Map<Value, Register> var2reg = new HashMap<>();

    /**
     * 构造函数 (用于“定义”或“声明”)
     */
    public Function(String name, Type returnType, ArrayList<Type> paramTypes, boolean isDeclaration) {
        super(returnType); // 这里 super 传入 FunctionType 会更准确，不过目前逻辑也行
        this.returnType = returnType;

        // 注意：函数名在 IR 中通常带 @，但在 Function 对象里你可以存纯名字
        // 建议：构造时去掉 @，toString 时加上，或者反之。保持一致即可。
        this.setName(name);

        this.isDeclaration = isDeclaration;
        this.basicBlocks = new LinkedList<>();
        this.params = new ArrayList<>();

        ArrayList<Type> irParamTypes = new ArrayList<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            Type astType = paramTypes.get(i);
            Type irType;
            if (astType instanceof ArrayType) {
                irType = PointerType.get(((ArrayType) astType).getElementType());
            } else {
                irType = astType;
            }
            irParamTypes.add(irType);
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
        this.basicBlocks.add(bb);
        bb.setParent(this);
    }

    public BasicBlock getEntryBlock() {
        return this.basicBlocks.isEmpty() ? null : this.basicBlocks.getFirst();
    }

    // ==========================================
    // 后端接口实现
    // ==========================================

    public void setVar2reg(Map<Value, Register> var2reg) {
        this.var2reg = var2reg;
    }

    public Map<Value, Register> getVar2reg() {
        return this.var2reg;
    }

    @Override
    public String toString() {
        String paramStr = this.params.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        if (this.isDeclaration) {
            return "declare " + this.returnType.toString() + " @" +
                    this.getName() + "(" + paramStr + ")";
        } else {
            String bbStr = this.basicBlocks.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));
            return "define dso_local " + this.returnType.toString() + " @" +
                    this.getName() + "(" + paramStr + ") {\n" +
                    bbStr +
                    "\n}";
        }
    }
}
