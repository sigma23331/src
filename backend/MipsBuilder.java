package backend;

// 导入你需要的所有包
import backend.enums.AsmOp;
import backend.enums.Register;
//import backend.utils.*; // 假设你有这些工具类
import backend.global.Asciiz;
import backend.global.Word;
import backend.text.CalcAsm;
import backend.text.Label;
import backend.text.MemAsm;
import com.sun.jdi.connect.Connector;
import middle.component.*;
import middle.component.inst.*;
import middle.component.inst.io.*;
import middle.component.model.*;
import middle.component.model.Module;
import middle.component.type.*; // 导入类型系统

import java.util.ArrayList;
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
        // 1. 内存分配
        instructionHandlers.put(AllocInst.class, inst -> buildAllocInst((AllocInst) inst));

        // 2. 二元运算 (核心分流逻辑)
        instructionHandlers.put(BinaryInst.class, inst -> {
            BinaryInst binaryInst = (BinaryInst) inst;
            // 检查操作符类型：如果是逻辑运算(==, !=, <, > 等)，走 icmp 构建
            if (binaryInst.getOpCode().isCompare()) {
                buildIcmp(binaryInst);
            } else {
                // 否则是算术运算(+, -, *, / 等)，走普通 binary 构建
                buildBinaryInst(binaryInst);
            }
        });

        // 3. 分支跳转 (核心分流逻辑)
        instructionHandlers.put(BrInst.class, inst -> {
            BrInst brInst = (BrInst) inst;
            // 检查是否有条件
            if (brInst.isConditional()) {
                buildCondBrInst(brInst);
            } else {
                buildNoCondBrInst(brInst);
            }
        });

        // 4. 函数调用与指针计算
        instructionHandlers.put(CallInst.class, inst -> buildCallInst((CallInst) inst));
        instructionHandlers.put(GepInst.class, inst -> buildGepInst((GepInst) inst));

        // 5. 访存指令
        instructionHandlers.put(LoadInst.class, inst -> buildLoadInst((LoadInst) inst));
        instructionHandlers.put(StoreInst.class, inst -> buildStoreInst((StoreInst) inst));
        instructionHandlers.put(MoveInst.class, inst -> buildMoveInst((MoveInst) inst));

        // 6. IO 指令
        instructionHandlers.put(GetintInst.class, inst -> buildGetintInst((GetintInst) inst));
        instructionHandlers.put(PutintInst.class, inst -> buildPutintInst((PutintInst) inst));
        instructionHandlers.put(PutstrInst.class, inst -> buildPutstrInst((PutstrInst) inst));

        // 7. 其他指令
        instructionHandlers.put(RetInst.class, inst -> buildRetInst((RetInst) inst));
        instructionHandlers.put(TruncInst.class, inst -> buildTruncInst((TruncInst) inst));
        instructionHandlers.put(ZextInst.class, inst -> buildZextInst((ZextInst) inst));
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
                buildFunction(function);
                break;
            }
        }
        // 4.4 标记 isInMain = false
        // 再次遍历 functions，处理所有名字 **不是** "@main" 的函数
        isInMain = false;
        for (Function function : module.getFunctions()) {
            if (!Objects.equals(function.getName(), "@main")) {
                buildFunction(function);
            }
        }
        // 4.5 后端窥孔优化 (PeepHole)
        if (optimize) {
            // PeepHole.run();
        }
    }

    // 5. 实现字符串常量生成
    private void buildConstString(ConstString constString) {
        // 1. 清洗标签名
        // 例如: "@.str.0" -> "str_0"
        String labelName = constString.getName()
                .replace("@", "")
                .replace(".", "_");

        // 2. 获取并清洗内容
        // 假设你加了 getLlvmString() 方法
        String content = constString.getLlvmString();

        // 将 LLVM 的换行符 "\0A" 替换为 Java/MIPS 的换行符 "\n"
        content = content.replace("\\0A", "\n");

        // 去掉 LLVM 显式的结束符 "\00"，因为 backend.global.Asciiz 会自动处理
        content = content.replace("\\00", "");

        // 创建 Asciiz 对象 (自动注册到 .data 段)
        new Asciiz(labelName, content);
    }

    // 6. 实现全局变量生成
    private void buildGlobalVar(GlobalVar globalVar) {
        // 逻辑：判断是 int 还是 array
        Type targetType = ((PointerType) globalVar.getType()).getPointeeType();

        if (targetType instanceof IntegerType) {
            // TODO: 处理标量
            // 检查 getInitialValue().getElements() 是否为空
            if (globalVar.getInitializer() == null) {
                new Word(globalVar.getName(),0);
            }
            // 为空则初始值为 0，否则取第一个元素
            else {
                int initVal = ((ConstInt)globalVar.getInitializer()).getValue();
                new Word(globalVar.getName(),initVal);
            }
            // new Word(...)
        } else if (targetType instanceof ArrayType) {
            // TODO: 处理数组
            // 获取元素列表和长度
            ArrayList<Constant> elements = ((ConstArray)globalVar.getInitializer()).getElements();
            ArrayList<Integer> integerArrayList = new ArrayList<>();
            for (Constant element : elements) {
                integerArrayList.add(((ConstInt)element).getValue());
            }
            int length = elements.size();
            new Word(globalVar.getName(), integerArrayList,length);
            // new Word(...)
        } else {
            throw new RuntimeException("Unknown global variable type");
        }
    }

    private void buildFunction(Function function) {
        this.currentFunction = function;
        this.var2Offset = new HashMap<>();
        this.curStackOffset = 0;

        // 7.1 初始化寄存器分配表
        // 如果开启优化，后续会有 Pass 填充 var2reg；否则为空，所有变量走栈
        this.var2reg = optimizeOn ? new HashMap<>(/* function.getVar2reg() TODO:如果未来有 */) : new HashMap<>();

        // 7.2 生成函数标签 (去掉 @ 前缀)
        new Label(function.getName().substring(1));

        // 7.3 处理函数参数
        // MIPS 标准：前 4 个参数在寄存器 $a0-$a3，剩下的在栈上
        ArrayList<FuncParam> funcParams = function.getParams();
        for (int i = 0; i < funcParams.size(); i++) {
            FuncParam arg = funcParams.get(i);

            // 先为所有参数预留栈空间 (这是 MIPS 调用惯例的一种，方便 spill)
            curStackOffset -= 4;
            var2Offset.put(arg, curStackOffset);

            // 如果是前 4 个参数，分配到寄存器 A0 - A3
            if (i < 4) {
                var2reg.put(arg, Register.getByOffset(Register.A0, i));
            }
        }

        // 7.4 遍历所有基本块中的指令，为局部变量分配栈空间
        // 凡是产生值(Value)且没有被分配寄存器的指令，都需要栈空间
        for (BasicBlock block : function.getBasicBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                // 跳过没有名字/返回值的指令 (如 store, br, ret)
                // 假设你的 Instruction 如果没有结果，名字可能是空或者类型是 VoidType
                if (inst.getType() instanceof VoidType) {
                    continue;
                }

                // 如果已经分配了寄存器(在优化开启时)，则不需要栈空间
                if (var2reg.containsKey(inst)) {
                    continue;
                }

                // 如果已经分配过栈空间(极少见，防止重复)，跳过
                if (var2Offset.containsKey(inst)) {
                    continue;
                }

                // ！！分配栈空间！！
                curStackOffset -= 4;
                var2Offset.put(inst, curStackOffset);
            }
        }

        // 7.5 生成具体的指令代码
        for (BasicBlock block : function.getBasicBlocks()) {
            buildBasicBlock(block);
        }
    }

    // 8. 基本块构建
    private void buildBasicBlock(BasicBlock block) {
        // 8.1 生成块标签
        // 逻辑修正：处理 BasicBlock 名字中的 "."
        // 例如函数名是 "main"，块名是 "entry.0" -> "main_entry_0"
        String safeBlockName = block.getName().replace(".", "_");

        // 组合标签名: funcName_blockName
        // 注意：currentFunction.getName() 带有 @，substring(1) 去掉它
        String labelName = currentFunction.getName().substring(1) + "_" + safeBlockName;

        new Label(labelName);

        // 8.2 遍历并生成指令
        for (Instruction instruction : block.getInstructions()) {
            // (可选) 插入注释方便调试，强烈建议打开，调试汇编时非常有用
            // new Comment("# " + instruction.toString());

            // 调用分发器
            buildInstruction(instruction);
        }
    }

    // 9. 指令分发 helper
    private void buildInstruction(Instruction instruction) {
        Consumer<Instruction> handler = instructionHandlers.get(instruction.getClass());
        if (handler != null) {
            handler.accept(instruction);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported instruction: " + instruction.getClass().getSimpleName());
        }
    }

    // ----------------------------------------------------
    // 下面这些是占位符方法，为了让 initInstructionHandlers 不报错
    // 我们会在后续步骤一一实现它们。
    // ----------------------------------------------------

    private void buildAllocInst(AllocInst allocInst) {
        // 1. 获取要分配的目标类型
        Type targetType = ((PointerType) allocInst.getType()).getPointeeType();

        // 2. 计算需要分配的字节数
        int sizeBytes = 4; // 默认标量为 4 字节
        if (targetType instanceof ArrayType) {
            // 如果是数组，大小 = 元素个数 * 4
            // 注意：这里假设你的 ArrayType 有 getNumElements() 方法
            sizeBytes = 4 * ((ArrayType) targetType).getNumElements();
        }

        // 3. 在栈上“挖”出空间
        curStackOffset -= sizeBytes;

        // 4. 计算新空间的地址，并存入 AllocInst 变量中
        // 目标：allocInst 的值 = $sp + curStackOffset

        // 4.1 确定存放结果的目标寄存器 (如果分配了寄存器则用分配的，否则用临时寄存器 K0)
        Register destReg = var2reg.getOrDefault(allocInst, Register.K0);

        // 4.2 生成地址计算指令: destReg = $sp + curStackOffset
        new CalcAsm(destReg, AsmOp.ADDIU, Register.SP, curStackOffset);

        // 4.3 如果 allocInst 没被分配寄存器，说明它的值（那个地址）得保存在栈上
        if (!var2reg.containsKey(allocInst)) {
            // 获取 allocInst 自己在栈上的偏移（在 buildFunction 里分配的）
            int allocInstOffset = var2Offset.get(allocInst);
            // 将计算出的地址存入栈槽: sw $k0, offset($sp)
            new MemAsm(AsmOp.SW, Register.K0, Register.SP, allocInstOffset);
        }
    }

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