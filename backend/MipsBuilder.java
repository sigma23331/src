package backend;

// 导入你需要的所有包
import backend.enums.Register;
import backend.utils.*; // 假设你有这些工具类
import middle.component.*;
import middle.component.inst.*;
import middle.component.inst.io.*;
import middle.component.model.*;
import middle.component.model.Module;
import middle.component.type.*; // 导入类型系统

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class MipsBuilder {
    // 1. 定义核心字段
    private final Module module;
    // 指令分发器：将指令类映射到具体的处理函数
    private final Map<Class<? extends Instruction>, Consumer<Instruction>> instructionHandlers = new HashMap<>();

    private final boolean optimizeOn; // 优化开关

    // 下面这三个是维护函数栈帧的核心变量
    private int curStackOffset; // 当前栈顶相对于 FP/SP 的偏移量
    private HashMap<Value, Register> var2reg; // 变量 -> 寄存器映射 (由寄存器分配器填充)
    private HashMap<Value, Integer> var2Offset; // 变量 -> 栈偏移映射 (溢出到栈的变量)

    private boolean isInMain = false; // 标记当前是否在 main 函数中 (影响 ret 指令的行为)
    private Function currentFunction; // 当前正在处理的函数

    // 2. 实现构造函数
    public MipsBuilder(Module module, boolean optimizeOn) {
        this.module = module;
        this.optimizeOn = optimizeOn;

        // 如果开启优化，按顺序执行优化 Pass
        if (optimizeOn) {
            // TODO: 填入源代码中的优化步骤
            // 1. ZextRemoval
            // 2. RegAlloc (这是核心，填充 var2reg)
            // 3. RemovePhi
            // 4. 更新 Module ID
        }

        // 初始化指令分发器
        initInstructionHandlers();
    }

    // 3. 注册所有指令的处理函数
    private void initInstructionHandlers() {
        // 示例：AllocInst -> buildAllocInst
        // 请参照源代码，将所有 IR 指令类型 (BinaryInst, BrInst, LoadInst 等)
        // 映射到对应的 buildXxx 方法（虽然这些方法还没实现，先写上 lambda）

        instructionHandlers.put(AllocInst.class, inst -> buildAllocInst((AllocInst) inst));

        // TODO: 补全剩余的 put 语句
        // BinaryInst (注意要区分逻辑运算和其他运算), BrInst, CallInst, GepInst...
        // IO 指令: Getint, Getchar, Putint, Putch, Putstr
        // 其他: RetInst, StoreInst, TruncInst, ZextInst
    }

    // 4. 实现主构建流程
    public void build(boolean optimize) {
        // 4.1 遍历 module.getConstStrings()，调用 buildConstString
        for (ConstString constString : module.getConstStrings()) {
            buildConstString(constString);
        }
        // 4.2 遍历 module.getGlobalVars()，调用 buildGlobalVar
        for (GlobalVar globalVar : module.getGlobalVars()) {
            buildGlobalVar(globalVar);
        }
        // 4.3 标记 isInMain = true
        // 遍历 functions，找到名为 "@main" 的函数进行 buildFunction，然后 break
        isInMain = true;
        for (Function function : module.getFunctions()) {
            if (Objects.equals(function.getName(), "@main")) {
                //buildFunction(function);
            }
        }
        // 4.4 标记 isInMain = false
        // 再次遍历 functions，处理所有名字 **不是** "@main" 的函数
        isInMain = false;
        for (Function function : module.getFunctions()) {
            if (!Objects.equals(function.getName(), "@main")) {
                //buildFunction(function);
            }
        }
        // 4.5 后端窥孔优化 (PeepHole)
        if (optimize) {
            // PeepHole.run();
        }
    }

    // 5. 实现字符串常量生成
    private void buildConstString(ConstString constString) {
        // TODO: 使用你之前写的 Asciiz 类
        // 逻辑：标签名通常处理一下（比如去掉前缀），内容直接传进去
        // new Asciiz(...)
    }

    // 6. 实现全局变量生成
    private void buildGlobalVar(GlobalVar globalVar) {
        // 逻辑：判断是 int 还是 array
        Type targetType = ((PointerType) globalVar.getType()).getPointeeType();

        if (targetType instanceof IntegerType) {
            // TODO: 处理标量
            // 检查 getInitialValue().getElements() 是否为空
            // 为空则初始值为 0，否则取第一个元素
            // new Word(...)
        } else if (targetType instanceof ArrayType) {
            // TODO: 处理数组
            // 获取元素列表和长度
            // new Word(...)
        } else {
            throw new RuntimeException("Unknown global variable type");
        }
    }

    // ----------------------------------------------------
    // 下面这些是占位符方法，为了让 initInstructionHandlers 不报错
    // 我们会在后续步骤一一实现它们。
    // ----------------------------------------------------

    private void buildAllocInst(AllocInst inst) {}
    private void buildBinaryInst(BinaryInst inst) {}
    private void buildIcmp(BinaryInst inst) {} // 逻辑比较单独处理
    private void buildBrInst(BrInst inst) {} // 包含条件和无条件
    private void buildCallInst(CallInst inst) {}
    private void buildGepInst(GepInst inst) {}
    private void buildLoadInst(LoadInst inst) {}
    private void buildMoveInst(MoveInst inst) {}
    private void buildGetintInst(GetintInst inst) {}
    private void buildPutintInst(PutintInst inst) {}
    private void buildPutstrInst(PutstrInst inst) {}
    private void buildRetInst(RetInst inst) {}
    private void buildStoreInst(StoreInst inst) {}
    private void buildTruncInst(TruncInst inst) {}
    private void buildZextInst(ZextInst inst) {}

    // 针对 BrInst 的特殊分流，源代码是在 lambda 里做的判断，你可以选择
    // 在 initInstructionHandlers 里写 if，或者定义一个总的 buildBrInst
    private void buildCondBrInst(BrInst inst) {}
    private void buildNoCondBrInst(BrInst inst) {}
}