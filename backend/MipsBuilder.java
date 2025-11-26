package backend;

// 导入你需要的所有包
import backend.enums.AsmOp;
import backend.enums.Register;
//import backend.utils.*; // 假设你有这些工具类
import backend.global.Asciiz;
import backend.global.Word;
import backend.text.*;
import com.sun.jdi.connect.Connector;
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

import static backend.enums.AsmOp.AND;
import static backend.enums.AsmOp.MUL;

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
            if (function.getName().equals("main") || function.getName().equals("@main")) {
                buildFunction(function);
                break;
            }
        }
        // 4.4 标记 isInMain = false
        // 再次遍历 functions，处理所有名字 **不是** "@main" 的函数
        isInMain = false;
        for (Function function : module.getFunctions()) {
            if (!(function.getName().equals("main") || function.getName().equals("@main"))) {
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
            // 检查 getInitialValue().getElements() 是否为空
            if (globalVar.getInitializer() == null) {
                String labelName = globalVar.getName()
                        .replace("@", "")
                        .replace(".", "_");
                new Word(labelName,0);
            }
            // 为空则初始值为 0，否则取第一个元素
            else {
                int initVal = ((ConstInt)globalVar.getInitializer()).getValue();
                String labelName = globalVar.getName()
                        .replace("@", "")
                        .replace(".", "_");
                new Word(labelName,initVal);
            }
        } else if (targetType instanceof ArrayType) {
            // 获取元素列表和长度
            ArrayList<Constant> elements = ((ConstArray)globalVar.getInitializer()).getElements();
            ArrayList<Integer> integerArrayList = new ArrayList<>();
            for (Constant element : elements) {
                integerArrayList.add(((ConstInt)element).getValue());
            }
            int length = elements.size();
            String labelName = globalVar.getName()
                    .replace("@", "")
                    .replace(".", "_");
            new Word(labelName, integerArrayList,length);
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
        new Label(parseLabel(function.getName()));

        // 7.3 处理函数参数
        // MIPS 标准：前 4 个参数在寄存器 $a0-$a3，剩下的在栈上
        ArrayList<FuncParam> funcParams = function.getParams();
        // 计算栈上参数的总数
        int totalStackArgs = Math.max(0, funcParams.size() - 4);

        for (int i = 0; i < funcParams.size(); i++) {
            FuncParam arg = funcParams.get(i);

            if (i < 4) {
                // 前 4 个参数：分配到寄存器，同时也预留栈空间
                curStackOffset -= 4;
                var2Offset.put(arg, curStackOffset);
                var2reg.put(arg, Register.getByOffset(Register.A0, i));
            } else {
                // 【关键修正】栈上参数 (第 5 个及以后)
                // 这里的内存布局是：[Arg N] [Arg N-1] ... [Arg 5] [RA]
                // SP 指向 Arg N (0($sp))
                // 所以 Arg i 的偏移量应该是：(总栈参数个数 - 1 - (当前是第几个栈参数)) * 4

                int stackArgIndex = i - 4; // 第 5 个参数 index 为 0
                int stackArgOffset = (totalStackArgs - 1 - stackArgIndex) * 4;

                var2Offset.put(arg, stackArgOffset);

                // 举例验证：假设共有 6 个参数 (Arg0-Arg5)。栈参数有 2 个 (Arg4, Arg5)。
                // totalStackArgs = 2.
                // i=4 (Arg4): index=0. offset = (2-1-0)*4 = 4. -> 对应 4($sp) -> 正确 (高地址)
                // i=5 (Arg5): index=1. offset = (2-1-1)*4 = 0. -> 对应 0($sp) -> 正确 (低地址/栈底)
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
        String labelName = parseLabel(currentFunction.getName()) + "_" + safeBlockName;

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
        int sizeBytes = 4;
        if (targetType instanceof ArrayType) {
            // 例子：int arr[10]; -> alloca [10 x i32]
            // 大小 = 10 * 4 = 40 字节
            sizeBytes = 4 * ((ArrayType) targetType).getNumElements();
        }

        // 3. 在栈上“挖”出空间
        // 例子：假设之前 curStackOffset 是 -4，现在分配 40 字节，变为 -44
        curStackOffset -= sizeBytes;

        // 4. 计算新空间的地址，并存入 AllocInst 变量中
        // 目标：我们要把这个新挖出来的空间的地址算出来。

        // 4.1 确定存放结果的目标寄存器
        // 例子：假设分配器给 %1 (allocInst) 分配了 $t0
        Register destReg = var2reg.getOrDefault(allocInst, Register.K0);

        // 4.2 生成地址计算指令
        // MIPS: addiu $t0, $sp, -44
        // 此时 $t0 里存的就是数组 arr 的首地址
        new CalcAsm(destReg, AsmOp.ADDIU, Register.SP, curStackOffset);

        // 4.3 如果 allocInst 没被分配寄存器 (溢出情况)
        if (!var2reg.containsKey(allocInst)) {
            // 例子：allocInst 这个“指针变量”本身也被挤到了栈上，位置是 -8($sp)
            // 我们刚算出来的数组地址在 $k0 中
            int allocInstOffset = var2Offset.get(allocInst);

            // MIPS: sw $k0, -8($sp)
            // 含义：把“数组的地址”存到“指针变量的栈槽”里
            new MemAsm(AsmOp.SW, Register.K0, Register.SP, allocInstOffset);
        }
    }

    private void buildLoadInst(LoadInst loadInst) {
        // 1. 获取指针操作数 (%ptr)
        Value pointer = loadInst.getPointer();
        Register addrReg = Register.K0; // 我们需要把地址先搞到 $k0 里

        // 2. 将地址加载到 addrReg ($k0) 中
        if (pointer instanceof GlobalVar) {
            // Case A: 全局变量
            // 例子：load i32, i32* @count
            // MIPS: la $k0, count
            GlobalVar gVar = (GlobalVar) pointer;
            new LaAsm(addrReg, parseLabel(gVar.getName()));
        } else if (var2reg.containsKey(pointer)) {
            // Case B: 指针变量在寄存器中
            // 例子：指针 %ptr 在 $t0 中
            // 不需要生成指令，addrReg 直接变成 $t0
            addrReg = var2reg.get(pointer);
        } else {
            // Case C: 指针变量在栈上 (溢出)
            // 例子：指针 %ptr 存在栈的 -4($sp) 处
            // MIPS: lw $k0, -4($sp) -> 此时 $k0 拿到了指针的值(即目标地址)
            int offset = var2Offset.get(pointer);
            new MemAsm(AsmOp.LW, addrReg, Register.SP, offset);
        }

        // 3. 执行加载操作: result = *addrReg
        // 确定存放结果(%val)的目标寄存器
        Register destReg;
        if (var2reg.containsKey(loadInst)) {
            // 例子：结果 %val 分配到了 $t1
            destReg = var2reg.get(loadInst);
        } else {
            // 例子：结果 %val 也没寄存器，暂时借用 $k1
            destReg = Register.K1;
        }

        // 生成真正取值的指令
        // MIPS: lw $t1, 0($k0)  (如果 ptr 在 t0，就是 lw $t1, 0($t0))
        // 含义：去 $k0 指向的地址，读 4 字节放入 destReg
        new MemAsm(AsmOp.LW, destReg, addrReg, 0);

        // 4. 如果结果需要存回栈 (溢出情况)
        if (!var2reg.containsKey(loadInst)) {
            // 例子：%val 对应的栈槽是 -8($sp)
            // MIPS: sw $k1, -8($sp)
            int instOffset = var2Offset.get(loadInst);
            new MemAsm(AsmOp.SW, destReg, Register.SP, instOffset);
        }
    }

    private void buildStoreInst(StoreInst storeInst) {
        Value pointer = storeInst.getPointer(); // 目标地址 (%ptr)
        Value value = storeInst.getValue();     // 要存的值 (%val)

        // 1. 准备地址寄存器 ($k0) - 逻辑同 LoadInst
        Register addrReg = Register.K0;
        if (pointer instanceof GlobalVar) {
            // Case: 全局变量 @g
            // MIPS: la $k0, g
            new LaAsm(addrReg, parseLabel(pointer.getName()));
        } else if (var2reg.containsKey(pointer)) {
            // Case: 指针在 $t0
            addrReg = var2reg.get(pointer);
        } else {
            // Case: 指针在栈 -4($sp)
            // MIPS: lw $k0, -4($sp)
            new MemAsm(AsmOp.LW, addrReg, Register.SP, var2Offset.get(pointer));
        }

        // 2. 准备数据寄存器 ($k1) - 把要存的值拿到 $k1
        Register dataReg = Register.K1;
        if (value instanceof ConstInt) {
            // Case A: 存立即数
            // 例子：store i32 5, ...
            // MIPS: li $k1, 5
            int imm = ((ConstInt) value).getValue();
            new LiAsm(dataReg, imm);
        } else if (var2reg.containsKey(value)) {
            // Case B: 值在寄存器 $t1
            // dataReg 直接引用 $t1
            dataReg = var2reg.get(value);
        } else {
            // Case C: 值在栈上 -8($sp)
            // MIPS: lw $k1, -8($sp)
            new MemAsm(AsmOp.LW, dataReg, Register.SP, var2Offset.get(value));
        }

        // 3. 执行存储
        // MIPS: sw $k1, 0($k0) (或者 sw $t1, 0($t0))
        // 含义：把 dataReg 的值写入 addrReg 指向的内存
        new MemAsm(AsmOp.SW, dataReg, addrReg, 0);
    }

    private void buildMoveInst(MoveInst moveInst) {
        Value src = moveInst.getFromValue();
        Value dst = moveInst.getToValue();

        // 1. 确定目标寄存器
        // 如果 dst 分配了 $t0，就用 $t0；如果 dst 在栈上，先用 $k0 暂存
        Register dstReg = var2reg.getOrDefault(dst, Register.K0);

        // 2. 将源操作数的值放入 dstReg
        if (src instanceof ConstInt) {
            // Case: move %dst, 10
            // MIPS: li $t0, 10
            int imm = ((ConstInt) src).getValue();
            new LiAsm(dstReg, imm);
        } else if (var2reg.containsKey(src)) {
            // Case: move %dst, $t1
            // MIPS: move $t0, $t1
            new MoveAsm(dstReg, var2reg.get(src));
        } else {
            // Case: src 在栈上 -4($sp)
            // MIPS: lw $t0, -4($sp)
            new MemAsm(AsmOp.LW, dstReg, Register.SP, var2Offset.get(src));
        }

        // 3. 如果目标原本是在栈上的 (溢出)
        if (!var2reg.containsKey(dst)) {
            // 我们刚才把值暂存在了 $k0 (dstReg)
            // 现在把它刷回栈槽 -8($sp)
            // MIPS: sw $k0, -8($sp)
            new MemAsm(AsmOp.SW, dstReg, Register.SP, var2Offset.get(dst));
        }
    }

    private void buildBinaryInst(BinaryInst binaryInst) {
        Value op1 = binaryInst.getOp1();
        Value op2 = binaryInst.getOp2();

        // 统计常量的个数
        int constCount = 0;
        if (op1 instanceof ConstInt) constCount++;
        if (op2 instanceof ConstInt) constCount++;

        // 1. 确定存放结果的目标寄存器
        // 如果结果分配了寄存器(如 $t2)，直接用；
        // 如果结果溢出到栈，先用临时寄存器 $k0 计算，最后再 sw 回去。
        Register targetReg = var2reg.getOrDefault(binaryInst, Register.K0);

        // 2. 根据常量数量分流处理
        if (constCount == 2) {
            // Case A: 两个操作数都是常量 (Constant Folding)
            // 既然都是常量，编译器直接算出结果，生成一条 li 指令即可
            makeTwoConst(binaryInst, targetReg);
        } else if (constCount == 1) {
            // Case B: 一个是常量，一个是变量 (Immediate Optimization)
            // 尝试使用 addiu, sll 等立即数指令优化
            makeOneConst(binaryInst, targetReg);
        } else {
            // Case C: 两个都是变量 (Standard R-type)
            // 标准的 addu, subu, mul, div
            makeNonConst(binaryInst, targetReg);
        }

        // 3. 溢出处理：如果 targetReg 是 $k0，说明结果属于栈变量，需要存回去
        if (targetReg == Register.K0) {
            // 例子：结果在栈偏移 -12($sp)
            // MIPS: sw $k0, -12($sp)
            new MemAsm(AsmOp.SW,targetReg,Register.SP,var2Offset.get(binaryInst));
        }
    }

    private void makeTwoConst(BinaryInst binaryInst, Register targetReg) {
        ConstInt c1 = (ConstInt) binaryInst.getOp1();
        ConstInt c2 = (ConstInt) binaryInst.getOp2();
        int v1 = c1.getValue();
        int v2 = c2.getValue();

        // 逻辑：直接在 Java 里算出结果，然后 li 加载到目标寄存器
        // 例子：result = 3 + 5
        // MIPS: li $t2, 8

        int result = 0;
        switch (binaryInst.getOpCode()) {
            case ADD -> result = v1 + v2;
            case SUB -> result = v1 - v2;
            case MUL -> result = v1 * v2;
            case SDIV -> result = v1 / v2; //测试数据应该不会出现这种情况
            case SREM -> result = v1 % v2;
        }

        new LiAsm(targetReg,result);
    }

    private void makeOneConst(BinaryInst binaryInst, Register targetReg) {
        Value op1 = binaryInst.getOp1();
        Value op2 = binaryInst.getOp2();

        // 1. 识别哪个是变量，哪个是常量
        // 注意：如果是减法 (a - 100)，顺序很重要；加法 (a + 100) 无所谓
        // 建议统一把变量加载到临时寄存器 temp (如 $k0)，常量的值记为 imm
        int imm;
        Register varReg;
        boolean constIsFirst;
        if (op1 instanceof ConstInt) {
            imm = ((ConstInt) op1).getValue();
            constIsFirst = true;

            Value value = op2;
            if (var2reg.containsKey(value))  {
                varReg = var2reg.get(value);
            }
            else {
                varReg = Register.K0;
                new MemAsm(AsmOp.LW,varReg,Register.SP,var2Offset.get(value)); //先存k0里
            }
        } else {
            // 情况 B: 变量 op 常量 (例如: a - 100)
            imm = ((ConstInt) op2).getValue();
            constIsFirst = false;

            // 加载 op1 (变量)
            Value var = op1;
            if (var2reg.containsKey(var)) {
                varReg = var2reg.get(var);
            } else {
                varReg = Register.K0;
                new MemAsm(AsmOp.LW, varReg, Register.SP, var2Offset.get(var));
            }
        }


        // 2. 根据 OpCode 生成指令

        // Branch 1: 加法 (ADD)
        // 例子: a = b + 10
        // MIPS: addiu $t2, $t1, 10
        if (binaryInst.getOpCode() == BinaryOpCode.ADD) {
            new CalcAsm(targetReg,AsmOp.ADDIU,varReg,imm);
        }

        // Branch 2: 减法 (SUB)
        // 例子: a = b - 10  -> 视为 b + (-10) -> addiu $t2, $t1, -10
        // 例子: a = 10 - b  -> li $at, 10; subu $t2, $at, $t1 (反过来不能用 addiu)
        if (binaryInst.getOpCode() == BinaryOpCode.SUB) {
            if (constIsFirst) {
                // 必须用 subu，不能用 addiu。所以要把 100 先加载到寄存器
                new LiAsm(Register.K1, imm); // $k1 = 100
                new CalcAsm(targetReg, AsmOp.SUBU, Register.K1, varReg);
            }
            else {
                // 可以看作 a + (-100)，直接用 addiu 优化
                new CalcAsm(targetReg, AsmOp.ADDIU, varReg, -imm);
            }
        }

        // Branch 3: 乘法 (MUL)
        // 优化点：如果乘以 2 的幂次 (如 *4, *8)，用移位 (sll) 代替乘法
        // 例子: a = b * 8
        // MIPS: sll $t2, $t1, 3
        // 普通情况: li $at, 100; mul $t2, $t1, $at
        if (binaryInst.getOpCode() == BinaryOpCode.MUL) {
            // 乘法优化：如果是乘以 2 的幂 (例如 *4, *8)，可以用移位 sll
            int power = getPowerOfTwo(imm); // 我们可以写个小辅助方法，或者直接在这里判断
            if (power != -1) {
                // 例子: a * 8  ->  a << 3
                new CalcAsm(targetReg, AsmOp.SLL, varReg, power);
            } else {
                // 普通乘法: 必须先把立即数 li 到寄存器
                makeVarMulConst(varReg,imm,targetReg);
            }
        }

        // Branch 4: 除法/取模 (SDIV/SREM)
        // 必须先把常量 li 到寄存器，然后用 div 指令
        // 例子: a = b / 10
        // MIPS: li $at, 10; div $t1, $at; mflo $t2
        if (binaryInst.getOpCode() == BinaryOpCode.SDIV) {
            // 除法：使用 MulDivAsm (AsmOp.DIV) + MDRegAsm (MFLO)
            new LiAsm(Register.K1, imm); // 必须把立即数加载到寄存器

            if (constIsFirst) {
                // Case: 100 / a -> div $k1, varReg
                new MulDivAsm(Register.K1, AsmOp.DIV, varReg);
            } else {
                // Case: a / 100 -> div varReg, $k1
                new MulDivAsm(varReg, AsmOp.DIV, Register.K1);
            }
            // 结果在 LO 寄存器，取出来
            new MDRegAsm(AsmOp.MFLO, targetReg);
        }

        if (binaryInst.getOpCode() == BinaryOpCode.SREM) {
            new LiAsm(Register.K1, imm);

            if (constIsFirst) {
                new MulDivAsm(Register.K1, AsmOp.DIV, varReg);
            } else {
                new MulDivAsm(varReg, AsmOp.DIV, Register.K1);
            }
            // 结果在 HI 寄存器，取出来
            new MDRegAsm(AsmOp.MFHI, targetReg);
        }
    }

    private void makeNonConst(BinaryInst binaryInst, Register targetReg) {
        Value op1 = binaryInst.getOp1();
        Value op2 = binaryInst.getOp2();

        // 1. 准备操作数寄存器 reg1, reg2
        Register reg1;
        if (var2reg.containsKey(op1)) {
            reg1 = var2reg.get(op1);
        } else {
            reg1 = Register.K0;
            new MemAsm(AsmOp.LW, reg1, Register.SP, var2Offset.get(op1));
        }

        Register reg2;
        if (var2reg.containsKey(op2)) {
            reg2 = var2reg.get(op2);
        } else {
            // 注意：如果 reg1 用了 K0，这里必须用 K1，否则会覆盖 reg1
            reg2 = Register.K1;
            new MemAsm(AsmOp.LW, reg2, Register.SP, var2Offset.get(op2));
        }

        // 2. 生成指令
        switch (binaryInst.getOpCode()) {
            case ADD:
                // R-Type: addu rd, rs, rt
                new CalcAsm(targetReg, AsmOp.ADDU, reg1, reg2);
                break;

            case SUB:
                // R-Type: subu rd, rs, rt
                new CalcAsm(targetReg, AsmOp.SUBU, reg1, reg2);
                break;

            case MUL:
                // R-Type: mul rd, rs, rt (伪指令，写回通用寄存器)
                // 这里使用 CalcAsm，因为结果直接进 targetReg
                // new MulDivAsm(reg1, AsmOp.MULT, reg2);
                // new MDRegAsm(AsmOp.MFLO, targetReg);
                new CalcAsm(targetReg,MUL,reg1,reg2);
                break;

            case SDIV:
                // Div: div rs, rt (结果进 HI/LO)
                // 这里使用 MulDivAsm
                new MulDivAsm(reg1, AsmOp.DIV, reg2);
                // 取商: mflo rd
                new MDRegAsm(AsmOp.MFLO, targetReg);
                break;

            case SREM:
                // Rem: div rs, rt
                new MulDivAsm(reg1, AsmOp.DIV, reg2);
                // 取余: mfhi rd
                new MDRegAsm(AsmOp.MFHI, targetReg);
                break;

            // 可以在这里补充 AND, OR, XOR 等逻辑运算(binaryopcode里没有）
        }
    }

    private int getPowerOfTwo(int val) {
        if (val > 0 && (val & (val - 1)) == 0) {
            return Integer.numberOfTrailingZeros(val);
        }
        return -1;
    }

    private void makeVarMulConst(Register varReg, int constInt, Register targetReg) {
        if (constInt == 0) {
            new LiAsm(targetReg, 0);
            return;
        }
        if (constInt == 1) {
            new MoveAsm(targetReg, varReg);
            return;
        }
        if (constInt == -1) {
            new NegAsm(targetReg, varReg);
            return;
        }
        boolean isNegative = constInt < 0;
        int absConstInt = Math.abs(constInt);

        // 尝试使用移位优化 (Shift Optimization)
        // ... (这部分的 switch-case 保持不变，因为移位指令如 sll 是合法的 R-Type) ...
        switch (absConstInt) {
            case 2:
                new CalcAsm(targetReg, AsmOp.ADDU, varReg, varReg);
                break;
            case 3:
                new CalcAsm(Register.V0, AsmOp.ADDU, varReg, varReg);
                new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, varReg);
                break;
            case 4:
                new CalcAsm(targetReg, AsmOp.SLL, varReg, 2);
                break;
            case 5:
                new CalcAsm(Register.V0, AsmOp.SLL, varReg, 2);
                new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, varReg);
                break;
            case 6:
                new CalcAsm(Register.V0, AsmOp.SLL, varReg, 2);
                new CalcAsm(Register.V1, AsmOp.ADDU, varReg, varReg);
                new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, Register.V1);
                break;
            case 7:
                new CalcAsm(Register.V0, AsmOp.SLL, varReg, 3);
                new CalcAsm(targetReg, AsmOp.SUBU, Register.V0, varReg);
                break;
            case 8:
                new CalcAsm(targetReg, AsmOp.SLL, varReg, 3);
                break;
            case 9:
                new CalcAsm(Register.V0, AsmOp.SLL, varReg, 3);
                new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, varReg);
                break;
            default:
                int bitCnt = Integer.bitCount(absConstInt);
                if (bitCnt <= 2) {
                    // ... (移位加法逻辑保持不变) ...
                    // 为了节省篇幅我省略了这一大段，和你之前的一样
                    int[] shifts = new int[2];
                    int index = 0;
                    for (int i = 0; i < 32; i++) {
                        if ((absConstInt & (1 << i)) != 0) {
                            shifts[index++] = i;
                            if (index == 2) break;
                        }
                    }
                    if (bitCnt == 1) {
                        new CalcAsm(targetReg, AsmOp.SLL, varReg, shifts[0]);
                    } else {
                        new CalcAsm(Register.V0, AsmOp.SLL, varReg, shifts[0]);
                        new CalcAsm(Register.V1, AsmOp.SLL, varReg, shifts[1]);
                        new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, Register.V1);
                    }
                } else {
                    // 【关键修改点】 对于其他情况，使用标准乘法 MULT

                    // 1. 将绝对值常量加载到临时寄存器 V0
                    new LiAsm(Register.V0, absConstInt);

                    // 2. 使用 MUL 指令
                    new CalcAsm(targetReg, AsmOp.MUL, varReg, Register.V0);
                }
        }

        if (isNegative) {
            new NegAsm(targetReg, targetReg);
        }
    }

    private void buildNoCondBrInst(BrInst brInst) {
        // 1. 获取目标基本块 (注意：强制类型转换 Value -> BasicBlock)
        BasicBlock targetBlock = (BasicBlock) brInst.getTrueDest();

        // 2. 构造目标标签名
        // 逻辑：函数名(去掉@) + "_" + 块名(去掉.)
        String blockLabel = parseLabel(currentFunction.getName()) + "_" +
                targetBlock.getName().replace(".", "_");

        // 3. 生成跳转指令: j label
        new JumpAsm(AsmOp.J, blockLabel);
    }

    private void buildCondBrInst(BrInst brInst) {
        // 1. 获取条件和跳转目标
        Value cond = brInst.getCondition();
        // 强制转换为 BasicBlock
        BasicBlock trueBlock = (BasicBlock) brInst.getTrueDest();
        BasicBlock falseBlock = (BasicBlock) brInst.getFalseDest();

        // 2. 构造标签名
        String trueLabel = parseLabel(currentFunction.getName()) + "_" +
                trueBlock.getName().replace(".", "_");
        String falseLabel = parseLabel(currentFunction.getName()) + "_" +
                falseBlock.getName().replace(".", "_");

        // --- 3. 尝试融合比较指令 (Peephole Optimization) ---
        // 检查条件是否是一个比较指令 (BinaryInst 且是 Compare 类型)
        if (cond instanceof BinaryInst && ((BinaryInst) cond).getOpCode().isCompare()) {
            BinaryInst compareInst = (BinaryInst) cond;

            // 3.1 准备操作数
            Value op1 = compareInst.getOp1();
            Value op2 = compareInst.getOp2();

            // 加载 op1 到寄存器
            Register reg1 = Register.K0;
            if (op1 instanceof ConstInt) {
                new LiAsm(reg1, ((ConstInt) op1).getValue());
            } else if (var2reg.containsKey(op1)) {
                reg1 = var2reg.get(op1);
            } else {
                new MemAsm(AsmOp.LW, reg1, Register.SP, var2Offset.get(op1));
            }

            // 加载/准备 op2
            Register reg2 = null;
            int immVal = 0;
            boolean op2IsConst = (op2 instanceof ConstInt);

            if (op2IsConst) {
                immVal = ((ConstInt) op2).getValue();
            } else {
                reg2 = Register.K1; // 暂存到 K1
                if (var2reg.containsKey(op2)) {
                    reg2 = var2reg.get(op2);
                } else {
                    new MemAsm(AsmOp.LW, reg2, Register.SP, var2Offset.get(op2));
                }
            }

            // 3.2 映射比较操作符 (IR -> MIPS Branch Op)
            AsmOp asmOp = null;
            switch (compareInst.getOpCode()) {
                case EQ -> asmOp = AsmOp.BEQ;
                case NE -> asmOp = AsmOp.BNE;
                case SLT -> asmOp = AsmOp.BLT;
                case SLE -> asmOp = AsmOp.BLE;
                case SGT -> asmOp = AsmOp.BGT;
                case SGE -> asmOp = AsmOp.BGE;
                default -> throw new RuntimeException("Unknown compare op");
            }

            // 3.3 生成条件跳转指令 (跳到 True Block)
            if (op2IsConst) {
                // 优化：利用立即数比较 (beq $t0, 100, label)
                new BrAsm(trueLabel, reg1, asmOp, immVal);
            } else {
                // 标准：寄存器比较 (beq $t0, $t1, label)
                new BrAsm(trueLabel, reg1, asmOp, reg2);
            }

        } else {
            // --- 4. 情况 B: 条件只是一个普通的 i1 变量 ---
            // 逻辑：if (cond != 0) goto True

            Register condReg = Register.K0;
            if (var2reg.containsKey(cond)) {
                condReg = var2reg.get(cond);
            } else {
                new MemAsm(AsmOp.LW, condReg, Register.SP, var2Offset.get(cond));
            }

            // 生成: bne $cond, $zero, trueLabel
            new BrAsm(trueLabel, condReg, AsmOp.BNE, Register.ZERO);
        }

        // 5. 无条件跳转到 False Block (Fall-through 的替代)
        // 如果上面的 Branch 没跳走，就继续执行这一句跳去 False
        new JumpAsm(AsmOp.J, falseLabel);
    }

    private void buildIcmp(BinaryInst binaryInst) {
        // --- 1. 窥孔优化检查 ---
        // 检查这个比较指令的所有使用者(Users)
        // 如果所有的使用者都是 BrInst (分支指令)，那么我们在这里不生成代码。
        // 让 buildCondBrInst 直接生成 "bne", "blt" 等跳转指令，而不是先 "slt" 再 "bne"。
        boolean onlyUsedByBranch = true;
        for (Use use : binaryInst.getUseList()) {
            if (!(use.getUser() instanceof BrInst)) {
                onlyUsedByBranch = false;
                break;
            }
        }
        if (onlyUsedByBranch) {
            return; // 直接跳过，留给 BrInst 处理
        }

        // --- 2. 如果结果被存储变量使用 (例如 int x = a < b)，则必须生成指令 ---

        Value op1 = binaryInst.getOp1();
        Value op2 = binaryInst.getOp2();

        // 2.1 准备操作数寄存器 (reg1, reg2)
        Register reg1 = Register.K0;
        if (op1 instanceof ConstInt) {
            new LiAsm(reg1, ((ConstInt) op1).getValue());
        } else if (var2reg.containsKey(op1)) {
            reg1 = var2reg.get(op1);
        } else {
            new MemAsm(AsmOp.LW, reg1, Register.SP, var2Offset.get(op1));
        }

        Register reg2 = Register.K1;
        if (op2 instanceof ConstInt) {
            new LiAsm(reg2, ((ConstInt) op2).getValue());
        } else if (var2reg.containsKey(op2)) {
            reg2 = var2reg.get(op2);
        } else {
            new MemAsm(AsmOp.LW, reg2, Register.SP, var2Offset.get(op2));
        }

        // 2.2 确定目标寄存器
        Register targetReg = var2reg.getOrDefault(binaryInst, Register.K0);

        // 2.3 映射操作码 (IR -> ASM)
        // seq: set equal, slt: set less than, etc.
        AsmOp asmOp = null;
        switch (binaryInst.getOpCode()) {
            case EQ -> asmOp = AsmOp.SEQ;
            case NE -> asmOp = AsmOp.SNE;
            case SLT -> asmOp = AsmOp.SLT; // signed less than
            case SLE -> asmOp = AsmOp.SLE;
            case SGT -> asmOp = AsmOp.SGT;
            case SGE -> asmOp = AsmOp.SGE;
            default -> throw new RuntimeException("Unknown icmp op: " + binaryInst.getOpCode());
        }

        // 2.4 生成比较指令
        // MIPS: seq $t0, $t1, $t2  (if t1==t2 then t0=1 else t0=0)
        new CmpAsm(targetReg, asmOp, reg1, reg2);

        // 2.5 溢出处理
        if (targetReg == Register.K0) {
            new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(binaryInst));
        }
    }

    private void buildRetInst(RetInst retInst) {
        // 1. 特殊处理 Main 函数
        // 在 MIPS 模拟器中，main 函数返回通常意味着程序终止
        if (isInMain) {
            // MIPS: li $v0, 10
            //       syscall
            new LiAsm(Register.V0,10);
            new SyscallAsm();
            return;
        }

        // 2. 处理返回值 (将结果放入 $v0)
        // 检查 RetInst 是否有操作数 (非 void 函数)
        if (retInst.getNumOperands() > 0) {
            Value retVal = retInst.getOperand(0);

            // 2.1 准备数据：将 retVal 加载到 $v0
            if (retVal instanceof ConstInt) {
                // Case A: 返回常量
                // MIPS: li $v0, imm
                new LiAsm(Register.V0,((ConstInt)retVal).getValue());
            } else if (var2reg.containsKey(retVal)) {
                // Case B: 返回值在寄存器中
                // MIPS: move $v0, $reg
                new MoveAsm(Register.V0,var2reg.get(retVal));
            } else {
                // Case C: 返回值在栈上
                // MIPS: lw $v0, offset($sp)
                new MemAsm(AsmOp.LW,Register.V0,Register.SP,var2Offset.get(retVal));
            }
        }

        // 3. 跳转回调用者
        // MIPS: jr $ra
        new JumpAsm(AsmOp.JR,Register.RA);
    }

    private void buildCallInst(CallInst callInst) {
        // 1. 保存 $ra (返回地址)
        // 为什么要保存？因为 jal 会修改 $ra，如果不存，等会儿就回不去上一层函数了。
        // 我们把它存在当前栈顶的再下一个位置 (curStackOffset - 4)
        int raOffset = curStackOffset - 4;
        new MemAsm(AsmOp.SW, Register.RA, Register.SP, raOffset);

        // 2. 准备参数
        // 获取被调用函数的参数列表（注意：是实参，即 CallInst 的操作数）
        // CallInst 的第 0 个操作数通常是 Function 指针，实参从第 1 个开始 (根据你的 IR 结构确认)
        // 假设：operand 0 是函数，operand 1~N 是参数

        Function targetFunc = (Function) callInst.getOperand(0); // 获取目标函数
        // 实参列表（排除掉第一个操作数）
        int argCount = callInst.getNumOperands() - 1;

        // 我们需要计算新栈帧的大小。
        // 除了 $ra (4字节)，如果有超过4个参数，多出的参数也要占栈空间。
        // 这里我们简单粗暴：新栈帧大小 = 当前偏移 + RA空间 + 参数空间
        // 但为了符合 buildFunction 的逻辑，我们在 buildFunction 里并没有移动 SP，
        // 所以这里我们需要暂时手动移动 SP。

        // --- 步骤 2.1: 填参数 ---
        for (int i = 0; i < argCount; i++) {
            Value arg = callInst.getOperand(i + 1); // 实参值

            if (i < 4) {
                // 前 4 个参数 -> 寄存器 $a0 - $a3
                Register argReg = Register.getByOffset(Register.A0, i);

                // 将实参的值加载到 argReg
                if (arg instanceof ConstInt) {
                    new LiAsm(argReg, ((ConstInt) arg).getValue());
                } else if (var2reg.containsKey(arg)) {
                    new MoveAsm(argReg, var2reg.get(arg));
                } else {
                    new MemAsm(AsmOp.LW, argReg, Register.SP, var2Offset.get(arg));
                }
            } else {
                // 超过 4 个参数 -> 存入栈 (参数区)
                // 这些参数应该放在新栈帧的顶部，或者旧栈帧的底部。
                // 简单约定：第 5 个参数放在 -8($sp) (因为 -4 是 $ra), 第 6 个放在 -12...
                // 注意：这是相对于“调用前”的 $sp 的偏移

                int paramOffset = raOffset - 4 * (i - 4 + 1);

                // 先把参数 load 到临时寄存器 $k0
                Register temp = Register.K0;
                if (arg instanceof ConstInt) {
                    new LiAsm(temp, ((ConstInt) arg).getValue());
                } else if (var2reg.containsKey(arg)) {
                    temp = var2reg.get(arg);
                } else {
                    new MemAsm(AsmOp.LW, temp, Register.SP, var2Offset.get(arg));
                }

                // 再 store 到栈上指定位置
                new MemAsm(AsmOp.SW, temp, Register.SP, paramOffset);
            }
        }

        // 3. 调整栈指针 ($sp) 并跳转
        // 我们刚才计算的最深偏移是：raOffset (存$ra) 或者是 最后一个参数的位置
        // 实际上，为了安全，我们可以直接让 $sp 下降到 (curStackOffset - 4 - 额外参数空间)
        // 或者更简单：直接下降当前函数已用的所有栈空间 + 额外空间。

        // 此时 curStackOffset 已经是负数，表示当前函数的局部变量用到哪了。
        // 我们在这个基础上，再给 $ra 和 溢出参数 留空间。
        int extraArgs = Math.max(0, argCount - 4);
        int totalStackSize = -curStackOffset + 4 + (extraArgs * 4);
        // 注意：MIPS 栈要求 8 字节对齐，最好处理一下，这里先忽略

        // 3.1 下降 SP
        new CalcAsm(Register.SP, AsmOp.ADDIU, Register.SP, -totalStackSize);

        // 3.2 跳转 (JAL)
        // 目标标签去掉 @
        new JumpAsm(AsmOp.JAL, parseLabel(targetFunc.getName()));

        // 3.3 恢复 SP
        new CalcAsm(Register.SP, AsmOp.ADDIU, Register.SP, totalStackSize);

        // 4. 恢复现场 ($ra)
        // 从之前存的位置把 $ra 读回来
        new MemAsm(AsmOp.LW, Register.RA, Register.SP, raOffset);

        // 5. 处理返回值
        // 如果 CallInst 不是 void，结果在 $v0，需要搬运到 CallInst 对应的位置
        if (!(callInst.getType() instanceof VoidType)) {
            Register targetReg = var2reg.getOrDefault(callInst, Register.K0);

            // 把 $v0 的值移到 targetReg
            new MoveAsm(targetReg, Register.V0);

            // 如果 targetReg 是 K0，说明溢出了，刷回栈
            if (targetReg == Register.K0) {
                new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(callInst));
            }
        }
    }

    // 辅助方法：递归计算类型的大小 (字节)
    private int getSize(Type type) {
        if (type instanceof IntegerType && ((IntegerType)type).getBitWidth() == 32) {
            return 4;
        } else if (type instanceof PointerType) {
            return 4; // 指针在 MIPS32 下是 4 字节
        } else if (type instanceof ArrayType) {
            ArrayType arr = (ArrayType) type;
            // 数组大小 = 元素个数 * 元素大小
            return arr.getNumElements() * getSize(arr.getElementType());
        } else if (type instanceof IntegerType && ((IntegerType)type).getBitWidth() == 8) {
            // 虽然 MIPS 是按字寻址优化，但 i8 占 1 字节
            // 注意：如果在结构体里通常涉及对齐，但你只支持数组，1 字节即可
            return 1;
        } else if (type instanceof IntegerType && ((IntegerType)type).getBitWidth() == 1) {
            return 1; // bool 通常按 1 字节存
        }
        return 0;
    }

    private void buildGepInst(GepInst gepInst) {
        // 1. 确定目标寄存器
        // 最终地址要存在这里。如果溢出到栈，先用 K0 暂存
        Register targetReg = var2reg.getOrDefault(gepInst, Register.K0);

        // 2. 准备基地址 -> 放入 targetReg
        // 这样我们后续所有的加法都在 targetReg 上进行，不破坏源寄存器
        Value basePointer = gepInst.getPointer();

        if (basePointer instanceof GlobalVar) {
            new LaAsm(targetReg, parseLabel(basePointer.getName()));
        } else if (var2reg.containsKey(basePointer)) {
            new MoveAsm(targetReg, var2reg.get(basePointer));
        } else if (basePointer instanceof ConstString) {
            // 逻辑同 GlobalVar，加载标签地址
            new LaAsm(targetReg, parseLabel(basePointer.getName()));
        } else {
            new MemAsm(AsmOp.LW, targetReg, Register.SP, var2Offset.get(basePointer));
        }

        // 3. 遍历索引，累加偏移到 targetReg
        Type baseType = basePointer.getType();
        Type curType;

        if (baseType instanceof PointerType) {
            curType = ((PointerType) baseType).getPointeeType();
        } else {
            // 针对 ConstString 或某些 GlobalVar，类型可能直接就是 ArrayType
            curType = baseType;
        }

        for (int i = 1; i < gepInst.getNumOperands(); i++) {
            Value index = gepInst.getOperand(i);
            int elementSize = getSize(curType);

            if (index instanceof ConstInt) {
                // --- 常量索引 ---
                int offset = ((ConstInt) index).getValue() * elementSize;
                if (offset != 0) {
                    new CalcAsm(targetReg, AsmOp.ADDIU, targetReg, offset);
                }
            } else {
                // --- 变量索引 ---
                // 1. 加载索引到 K1
                Register idxReg = Register.K1;
                if (var2reg.containsKey(index)) {
                    idxReg = var2reg.get(index);
                } else {
                    new MemAsm(AsmOp.LW, idxReg, Register.SP, var2Offset.get(index));
                }

                // 2. 计算 offset = index * elementSize -> 存入 GP (避免污染 K1/Target)
                Register offsetReg = Register.GP;

                // 简单的乘法/移位逻辑
                boolean usedShift = false;
                if (elementSize > 0 && (elementSize & (elementSize - 1)) == 0) {
                    int shift = Integer.numberOfTrailingZeros(elementSize);
                    new CalcAsm(offsetReg, AsmOp.SLL, idxReg, shift);
                    usedShift = true;
                }

                if (!usedShift) {
                    new LiAsm(Register.V0, elementSize);
                    new MulDivAsm(idxReg, AsmOp.MULT, Register.V0);
                    new MDRegAsm(AsmOp.MFLO, offsetReg);
                }

                // 3. 累加: targetReg = targetReg + offsetReg
                new CalcAsm(targetReg, AsmOp.ADDU, targetReg, offsetReg);
            }

            // 下钻类型
            if (curType instanceof ArrayType) {
                curType = ((ArrayType) curType).getElementType();
            }
        }

        // 4. 溢出处理
        if (!var2reg.containsKey(gepInst)) {
            new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(gepInst));
        }
    }

    private void buildGetintInst(GetintInst inst) {
        // 1. 系统调用 5 (read_int)
        new LiAsm(Register.V0, 5);
        new SyscallAsm();

        // 2. 将读入的结果 ($v0) 存入目标变量
        if (var2reg.containsKey(inst)) {
            // 目标在寄存器: move $reg, $v0
            new MoveAsm(var2reg.get(inst), Register.V0);
        } else {
            // 目标在栈上: sw $v0, offset($sp)
            new MemAsm(AsmOp.SW, Register.V0, Register.SP, var2Offset.get(inst));
        }
    }

    private void buildPutintInst(PutintInst inst) {
        // 1. 准备参数到 $a0
        Value val = inst.getOperand(1); // 假设 getOperand(0) 是要输出的值

        if (val instanceof ConstInt) {
            new LiAsm(Register.A0, ((ConstInt) val).getValue());
        } else if (var2reg.containsKey(val)) {
            new MoveAsm(Register.A0, var2reg.get(val));
        } else {
            new MemAsm(AsmOp.LW, Register.A0, Register.SP, var2Offset.get(val));
        }

        // 2. 系统调用 1 (print_int)
        new LiAsm(Register.V0, 1);
        new SyscallAsm();
    }

    private void buildPutstrInst(PutstrInst inst) {
        // 【修正 1】CallInst 的第 0 个操作数是函数本身，第 1 个才是参数
        Value val = inst.getOperand(1);

        if (val instanceof ConstString) {
            // --- 情况 A: 直接传入了全局字符串常量 (优化路径) ---
            ConstString str = (ConstString) val;

            // 清洗标签名逻辑 (保持你原有的逻辑)
            String originalName = str.getName();
            String labelName;
            if (originalName.startsWith("@str")) {
                labelName = "s" + originalName.substring(4);
            } else {
                labelName = "s_" + originalName.replace("@", "");
            }

            // 加载地址: la $a0, label
            new LaAsm(Register.A0, labelName);

        } else {
            // --- 情况 B: 传入的是一个指针变量，例如 %v0 (通用路径) ---
            // 这里的 val 存的就是字符串的地址，我们需要把它放到 $a0 中
            // 这和 buildPutintInst 的逻辑类似，只是不用考虑立即数

            if (var2reg.containsKey(val)) {
                // 地址已经在寄存器里了 -> move $a0, $reg
                new MoveAsm(Register.A0, var2reg.get(val));
            } else {
                // 地址溢出在栈上 -> lw $a0, offset($sp)
                // 注意：这里必须加非空检查，防止像之前 GEP 那样报空指针
                Integer offset = var2Offset.get(val);
                if (offset == null) {
                    throw new RuntimeException("Putstr error: String pointer not found. Val=" + val.getName());
                }
                new MemAsm(AsmOp.LW, Register.A0, Register.SP, offset);
            }
        }

        // 4. 系统调用 4 (print_string)
        new LiAsm(Register.V0, 4);
        new SyscallAsm();
    }

    private void buildTruncInst(TruncInst inst) {
        Value src = inst.getOperand(0);
        Register srcReg = Register.K0;

        // 1. 准备源操作数
        if (var2reg.containsKey(src)) {
            srcReg = var2reg.get(src);
        } else {
            new MemAsm(AsmOp.LW, srcReg, Register.SP, var2Offset.get(src));
        }

        // 2. 准备目标寄存器
        Register targetReg = var2reg.getOrDefault(inst, Register.K1);

        // 3. 根据目标类型生成掩码 (Mask)
        // 如果转成 i1 -> 0x1, i8 -> 0xFF
        int mask = 0xFFFFFFFF; // 默认
        Type type = inst.getType();
        if (type instanceof IntegerType && ((IntegerType)type).getBitWidth() == 1) {
            mask = 1;
        } else if (type instanceof IntegerType && ((IntegerType)type).getBitWidth() == 8) {
            mask = 0xFF;
        }

        // 4. 执行截断: andi target, src, mask
        new CalcAsm(targetReg, AsmOp.ANDI, srcReg, mask);

        // 5. 溢出处理
        if (!var2reg.containsKey(inst)) {
            new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(inst));
        }
    }

    private void buildZextInst(ZextInst inst) {
        // Zext 逻辑很简单：MIPS 寄存器已经是 32 位的
        // i1 的 1 在寄存器里就是 1，扩展到 i32 还是 1。
        // 所以本质上这就是一个 Move 操作。

        Value src = inst.getOperand(0);
        Register targetReg = var2reg.getOrDefault(inst, Register.K0);

        // 1. 加载/移动源操作数到目标
        if (src instanceof ConstInt) {
            new LiAsm(targetReg, ((ConstInt) src).getValue());
        } else if (var2reg.containsKey(src)) {
            new MoveAsm(targetReg, var2reg.get(src));
        } else {
            new MemAsm(AsmOp.LW, targetReg, Register.SP, var2Offset.get(src));
        }

        // 2. 溢出处理
        if (!var2reg.containsKey(inst)) {
            new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(inst));
        }
    }

    private String parseLabel(String irName) {
        // 1. 如果以 @ 开头，去掉它
        String name = irName.startsWith("@") ? irName.substring(1) : irName;

        // 2. 替换非法字符 (如 . 替换为 _)
        // @test.x0 -> test_x0
        return name.replace(".", "_");
    }
}