package backend;

// 导入你需要的所有包
import backend.enums.AsmOp;
import backend.enums.Register;
//import backend.utils.*; // 假设你有这些工具类
import backend.global.Asciiz;
import backend.global.Word;
import backend.text.*;
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

import static backend.enums.AsmOp.AND;

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
            new LaAsm(addrReg, gVar.getName().substring(1));
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
            new LaAsm(addrReg, pointer.getName().substring(1));
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

        // TODO: 生成 LiAsm 指令将 result 加载到 targetReg
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
        // TODO: 请实现以下分支逻辑

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
                new MulDivAsm(reg1, AsmOp.MULT, reg2);
                new MDRegAsm(AsmOp.MFLO, targetReg);
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

                    // 2. 使用 MULT 指令 (只有两个操作数: rs, rt)
                    // 格式: mult $var, $v0
                    // 结果: 存放在 HI 和 LO 寄存器中
                    new MulDivAsm(varReg, AsmOp.MULT, Register.V0);

                    // 3. 从 LO 寄存器取出低 32 位结果到目标寄存器
                    // 格式: mflo $target
                    new MDRegAsm(AsmOp.MFLO, targetReg);
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
        String blockLabel = currentFunction.getName().substring(1) + "_" +
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
        String trueLabel = currentFunction.getName().substring(1) + "_" +
                trueBlock.getName().replace(".", "_");
        String falseLabel = currentFunction.getName().substring(1) + "_" +
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

    private void buildCallInst(CallInst inst) {}
    private void buildGepInst(GepInst inst) {}
    private void buildGetintInst(GetintInst inst) {}
    private void buildPutintInst(PutintInst inst) {}
    private void buildPutstrInst(PutstrInst inst) {}
    private void buildRetInst(RetInst inst) {}
    private void buildTruncInst(TruncInst inst) {}
    private void buildZextInst(ZextInst inst) {}
}