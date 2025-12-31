package backend;

// 导入你需要的所有包
import backend.enums.AsmOp;
import backend.enums.Register;
//import backend.utils.*; // 假设你有这些工具类
import backend.global.Asciiz;
import backend.global.Word;
import backend.text.*;
import backend.utils.RegAlloc;
import com.sun.jdi.connect.Connector;
import middle.component.inst.*;
import middle.component.inst.io.*;
import middle.component.model.*;
import middle.component.model.Module;
import middle.component.type.*; // 导入类型系统

import java.util.*;
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
    private List<Object> mipsInstructions = new ArrayList<>();

    // 2. 实现构造函数
    public MipsBuilder(Module module, boolean optimizeOn) {
        this.module = module;
        this.optimizeOn = optimizeOn;

        if (optimizeOn) {
            // 1. 先进行寄存器分配 (填充 var2reg)
            RegAlloc regAlloc = new RegAlloc();
            regAlloc.run(module);

            // 2. 然后基于分配好的寄存器消除 Phi
            // (RemovePhi 内部会读取 function.getVar2reg())
            optimize.RemovePhi.run(module);
        } else {
            // 如果不优化，也需要消除 Phi (但此时 var2reg 为空，只做简单的 Move 插入)
            // optimize.RemovePhi.run(module);
        }

        // 初始化指令分发器
        initInstructionHandlers();
    }

    /**
     * 将指令加入缓存列表
     */
    private void emit(Object asm) {
        this.mipsInstructions.add(asm);
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
        // 【修改点】：先处理 GlobalVar (int/array)，确保它们从数据段首地址(对齐)开始存放
        // 4.1 遍历 module.getGlobalVars()，调用 buildGlobalVar
        for (GlobalVar globalVar : module.getGlobalVars()) {
            buildGlobalVar(globalVar);
        }

        // 【修改点】：后处理 ConstString，字符串不需要对齐，放在后面没问题
        // 4.2 遍历 module.getConstStrings()，调用 buildConstString
        for (ConstString constString : module.getConstStrings()) {
            buildConstString(constString);
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
            // 传入原始列表，获取优化后的新列表
            this.mipsInstructions = backend.utils.PeepHole.run(this.mipsInstructions);
        }

        MipsFile.getInstance().updateTextSegment(this.mipsInstructions);
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
        Type targetType = ((PointerType) globalVar.getType()).getPointeeType();
        // 使用 helper 方法统一处理名字
        String labelName = parseLabel(globalVar.getName());

        if (targetType instanceof IntegerType) {
            int initVal = 0;
            if (globalVar.getInitializer() != null) {
                initVal = ((ConstInt) globalVar.getInitializer()).getValue();
            }
            new Word(labelName, initVal);
        } else if (targetType instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) targetType;
            // 【关键修复】使用数组声明的总长度，而不是初始化列表的长度
            int totalLength = arrayType.getNumElements();

            ArrayList<Integer> integerArrayList = new ArrayList<>();

            // 获取初始化值
            if (globalVar.getInitializer() instanceof ConstArray) {
                ArrayList<Constant> elements = ((ConstArray) globalVar.getInitializer()).getElements();
                for (Constant element : elements) {
                    integerArrayList.add(((ConstInt) element).getValue());
                }
            } else if (globalVar.getInitializer() instanceof ConstInt) {
                // 某些极其特殊情况，虽然不太可能出现在数组里
                integerArrayList.add(((ConstInt)globalVar.getInitializer()).getValue());
            }

            // 【关键修复】用 0 填充剩余空间，防止 .data 段越界
            while (integerArrayList.size() < totalLength) {
                integerArrayList.add(0);
            }

            new Word(labelName, integerArrayList, totalLength);
        } else {
            throw new RuntimeException("Unknown global variable type");
        }
    }

    private void buildFunction(Function function) {
        this.currentFunction = function;
        this.var2Offset = new HashMap<>();
        this.curStackOffset = 0;

        // 7.1 初始化寄存器分配表
        this.var2reg = optimizeOn ? new HashMap<>(function.getVar2reg()) : new HashMap<>();

        // 7.2 生成函数标签
        emit(new Label(parseLabel(function.getName())));

        // 7.3 处理函数参数 (保持不变)
        ArrayList<FuncParam> funcParams = function.getParams();
        int totalStackArgs = Math.max(0, funcParams.size() - 4);

        for (int i = 0; i < funcParams.size(); i++) {
            FuncParam arg = funcParams.get(i);
            if (i < 4) {
                curStackOffset -= 4;
                var2Offset.put(arg, curStackOffset);
                Register argReg = Register.getByOffset(Register.A0, i);
                emit(new MemAsm(AsmOp.SW, argReg, Register.SP, curStackOffset));
            } else {
                int stackArgIndex = i - 4;
                int stackArgOffset = (totalStackArgs - 1 - stackArgIndex) * 4;
                var2Offset.put(arg, stackArgOffset);
            }
        }

        // 7.4 【关键修改】遍历所有指令，为局部变量分配栈空间
        for (BasicBlock block : function.getBasicBlocks()) {
            for (Instruction inst : block.getInstructions()) {

                // 【关键位置】：必须在 VoidType 检查之前处理 MoveInst
                if (inst instanceof MoveInst) {
                    Value dest = ((MoveInst) inst).getToValue();
                    // 如果目标是临时变量或Phi变量，且没分配过，则分配栈空间
                    if (!var2reg.containsKey(dest) && !var2Offset.containsKey(dest)) {
                        curStackOffset -= 4;
                        var2Offset.put(dest, curStackOffset);
                    }
                }

                // 跳过无返回值的指令 (注意：MoveInst 也是 VoidType，所以必须在上面处理完)
                if (inst.getType() instanceof VoidType) continue;

                // 如果已经分配了寄存器或栈空间，跳过
                if (var2reg.containsKey(inst)) continue;
                if (var2Offset.containsKey(inst)) continue;

                // 为普通指令分配栈空间
                curStackOffset -= 4;
                var2Offset.put(inst, curStackOffset);
            }
        }

        // 7.5 生成指令
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

        emit(new Label(labelName));

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
        Type targetType = ((PointerType) allocInst.getType()).getPointeeType();

        int sizeBytes = 4; // 默认分配 4 字节
        if (targetType instanceof ArrayType) {
            // 【关键修复】无论元素是 i32 还是 i8，都强制按 4 字节分配
            // 这样能保证栈始终 4 字节对齐，且配合 GEP 的 *4 逻辑
            sizeBytes = 4 * ((ArrayType) targetType).getNumElements();
        }

        // 在栈上分配空间
        curStackOffset -= sizeBytes;

        // 获取 AllocInst 对应的寄存器（或临时寄存器 K0）
        Register destReg = var2reg.getOrDefault(allocInst, Register.K0);

        // 计算地址: destReg = $sp + curStackOffset
        emit(new CalcAsm(destReg, AsmOp.ADDIU, Register.SP, curStackOffset));

        // 如果 AllocInst 溢出到栈上，将计算出的地址存回栈槽
        if (!var2reg.containsKey(allocInst)) {
            int allocInstOffset = var2Offset.get(allocInst);
            emit(new MemAsm(AsmOp.SW, Register.K0, Register.SP, allocInstOffset));
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
            emit(new LaAsm(addrReg, parseLabel(gVar.getName())));
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
            emit(new MemAsm(AsmOp.LW, addrReg, Register.SP, offset));
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
        emit(new MemAsm(AsmOp.LW, destReg, addrReg, 0));

        // 4. 如果结果需要存回栈 (溢出情况)
        if (!var2reg.containsKey(loadInst)) {
            // 例子：%val 对应的栈槽是 -8($sp)
            // MIPS: sw $k1, -8($sp)
            int instOffset = var2Offset.get(loadInst);
            emit(new MemAsm(AsmOp.SW, destReg, Register.SP, instOffset));
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
            emit(new LaAsm(addrReg, parseLabel(pointer.getName())));
        } else if (var2reg.containsKey(pointer)) {
            // Case: 指针在 $t0
            addrReg = var2reg.get(pointer);
        } else {
            // Case: 指针在栈 -4($sp)
            // MIPS: lw $k0, -4($sp)
            emit(new MemAsm(AsmOp.LW, addrReg, Register.SP, var2Offset.get(pointer)));
        }

        // 2. 准备数据寄存器 ($k1) - 把要存的值拿到 $k1
        Register dataReg = Register.K1;
        if (value instanceof ConstInt) {
            // Case A: 存立即数
            // 例子：store i32 5, ...
            // MIPS: li $k1, 5
            int imm = ((ConstInt) value).getValue();
            emit(new LiAsm(dataReg, imm));
        }
        else if (value instanceof GlobalVar) {
            // 【新增 Case】: 存全局变量的地址
            // 例如: store i32* @g, i32** %ptr
            // MIPS: la $k1, g
            emit(new LaAsm(dataReg, parseLabel(value.getName())));
        }
        else if (value instanceof ConstString) {
            // 【新增 Case】: 存字符串常量的地址
            // MIPS: la $k1, str_label
            String label = parseLabel(value.getName());
            if (label.startsWith("@")) label = label.substring(1); // 防御性处理
            // 处理字符串命名规则 (参考 buildConstString)
            if (!label.startsWith("str_")) {
                label = label.replace("@", "").replace(".", "_");
                if (!label.startsWith("str_")) label = "s_" + label; // 简单 hack，请确保和 buildConstString 一致
                // 最稳妥的是复用 parseLabel 或保持一致的命名逻辑
                // 假设 parseLabel 已经处理了 . -> _
            }
            emit(new LaAsm(dataReg, label));
        }
        else if (var2reg.containsKey(value)) {
            // Case B: 值在寄存器
            dataReg = var2reg.get(value);
        }
        else {
            // Case C: 值在栈上
            Integer offset = var2Offset.get(value);
            if (offset == null) {
                // 【NPE 保护】抛出详细异常
                throw new RuntimeException("GenCode Error: Store value not found in stack. Inst=" + storeInst + ", Val=" + value.getName());
            }
            emit(new MemAsm(AsmOp.LW, dataReg, Register.SP, offset));
        }

        // ==========================================
        // 3. 执行存储
        // ==========================================
        // MIPS: sw $data, 0($addr)
        emit(new MemAsm(AsmOp.SW, dataReg, addrReg, 0));
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
            emit(new LiAsm(dstReg, imm));
        } else if (src instanceof GlobalVar) {
            // 【关键修复】源操作数是全局变量（地址），使用 la
            emit(new LaAsm(dstReg, parseLabel(src.getName())));
        } else if (var2reg.containsKey(src)) {
            // Case: move %dst, $t1
            // MIPS: move $t0, $t1
            emit(new MoveAsm(dstReg, var2reg.get(src)));
        } else {
            Integer offset = var2Offset.get(src);

            if (offset == null) {
                // 【终极修复】Panic Mode
                // 发现了一个没有分配栈空间的变量 (可能是 Undef，或者是丢失的临时变量)
                // 为了防止崩溃，我们生成 li $reg, 0
                // 对于 SysY 来说，读取未定义变量的值为 0 是符合逻辑的兜底行为

                // System.err.println("Warning: Panic handling for Unknown REG: " + src.getName()); // 调试用
                emit(new LiAsm(dstReg, 0));
            } else {
                // 正常情况
                emit(new MemAsm(AsmOp.LW, dstReg, Register.SP, offset));
            }
        }

        // 3. 如果目标原本是在栈上的 (溢出)
        if (!var2reg.containsKey(dst)) {
            // 注意：这里必须确保 dst 在 var2Offset 中
            Integer dstOffset = var2Offset.get(dst);
            if (dstOffset == null) {
                // 如果目标变量也没有栈空间，这是一个严重的逻辑错误，说明 7.4 步没有扫描到这条指令
                throw new RuntimeException("Panic: Move dest not allocated in stack: " + dst.getName());
            }
            emit(new MemAsm(AsmOp.SW, dstReg, Register.SP, dstOffset));
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
            emit(new MemAsm(AsmOp.SW,targetReg,Register.SP,var2Offset.get(binaryInst)));
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

        emit(new LiAsm(targetReg,result));
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
                emit(new MemAsm(AsmOp.LW,varReg,Register.SP,var2Offset.get(value))); //先存k0里
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
                emit(new MemAsm(AsmOp.LW, varReg, Register.SP, var2Offset.get(var)));
            }
        }


        // 2. 根据 OpCode 生成指令

        // Branch 1: 加法 (ADD)
        // 例子: a = b + 10
        // MIPS: addiu $t2, $t1, 10
        if (binaryInst.getOpCode() == BinaryOpCode.ADD) {
            emit(new CalcAsm(targetReg,AsmOp.ADDIU,varReg,imm));
        }

        // Branch 2: 减法 (SUB)
        // 例子: a = b - 10  -> 视为 b + (-10) -> addiu $t2, $t1, -10
        // 例子: a = 10 - b  -> li $at, 10; subu $t2, $at, $t1 (反过来不能用 addiu)
        if (binaryInst.getOpCode() == BinaryOpCode.SUB) {
            if (constIsFirst) {
                // 必须用 subu，不能用 addiu。所以要把 100 先加载到寄存器
                emit(new LiAsm(Register.K1, imm)); // $k1 = 100
                emit(new CalcAsm(targetReg, AsmOp.SUBU, Register.K1, varReg));
            }
            else {
                // 可以看作 a + (-100)，直接用 addiu 优化
                emit(new CalcAsm(targetReg, AsmOp.ADDIU, varReg, -imm));
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
                emit(new CalcAsm(targetReg, AsmOp.SLL, varReg, power));
            } else {
                // 普通乘法: 必须先把立即数 li 到寄存器
                makeVarMulConst(varReg,imm,targetReg);
            }
        }

        // Branch 4: 除法/取模 (SDIV/SREM)
        // 必须先把常量 li 到寄存器，然后用 div 指令
        // 例子: a = b / 10
        // MIPS: li $at, 10; div $t1, $at; mflo $t2
        // Branch 4: 除法/取模 (SDIV/SREM)
        if (binaryInst.getOpCode() == BinaryOpCode.SDIV) {
            // 【核心修改点】集成除法优化
            // 只有当：1.除数是常量 2.除数不为0 3.开启优化 时，才使用魔数优化
            if (!constIsFirst && imm != 0 && optimizeOn) {
                // 调用下面定义的私有方法
                emitDivOptimization(varReg, imm, targetReg);
            }
            else {
                // 原有逻辑：普通除法
                emit(new LiAsm(Register.K1, imm));
                if (constIsFirst) {
                    emit(new MulDivAsm(Register.K1, AsmOp.DIV, varReg));
                } else {
                    emit(new MulDivAsm(varReg, AsmOp.DIV, Register.K1));
                }
                emit(new MDRegAsm(AsmOp.MFLO, targetReg));
            }
        }
        else if (binaryInst.getOpCode() == BinaryOpCode.SREM) {
            // 取模优化相对复杂，通常依赖除法优化算出商，再用 a - (a/b)*b 算余数
            // 这里暂时保持原逻辑，或者你可以手动实现：Rem = a - (DivOpt(a, b) * b)
            emit(new LiAsm(Register.K1, imm));
            if (constIsFirst) {
                emit(new MulDivAsm(Register.K1, AsmOp.DIV, varReg));
            } else {
                emit(new MulDivAsm(varReg, AsmOp.DIV, Register.K1));
            }
            emit(new MDRegAsm(AsmOp.MFHI, targetReg));
        }

        if (binaryInst.getOpCode() == BinaryOpCode.SREM) {
            emit(new LiAsm(Register.K1, imm));

            if (constIsFirst) {
                emit(new MulDivAsm(Register.K1, AsmOp.DIV, varReg));
            } else {
                emit(new MulDivAsm(varReg, AsmOp.DIV, Register.K1));
            }
            // 结果在 HI 寄存器，取出来
            emit(new MDRegAsm(AsmOp.MFHI, targetReg));
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
            emit(new MemAsm(AsmOp.LW, reg1, Register.SP, var2Offset.get(op1)));
        }

        Register reg2;
        if (var2reg.containsKey(op2)) {
            reg2 = var2reg.get(op2);
        } else {
            // 注意：如果 reg1 用了 K0，这里必须用 K1，否则会覆盖 reg1
            reg2 = Register.K1;
            emit(new MemAsm(AsmOp.LW, reg2, Register.SP, var2Offset.get(op2)));
        }

        // 2. 生成指令
        switch (binaryInst.getOpCode()) {
            case ADD:
                // R-Type: addu rd, rs, rt
                emit(new CalcAsm(targetReg, AsmOp.ADDU, reg1, reg2));
                break;

            case SUB:
                // R-Type: subu rd, rs, rt
                emit(new CalcAsm(targetReg, AsmOp.SUBU, reg1, reg2));
                break;

            case MUL:
                // R-Type: mul rd, rs, rt (伪指令，写回通用寄存器)
                // 这里使用 CalcAsm，因为结果直接进 targetReg
                // emit(new MulDivAsm(reg1, AsmOp.MULT, reg2);
                // emit(new MDRegAsm(AsmOp.MFLO, targetReg);
                emit(new CalcAsm(targetReg,MUL,reg1,reg2));
                break;

            case SDIV:
                // Div: div rs, rt (结果进 HI/LO)
                // 这里使用 MulDivAsm
                emit(new MulDivAsm(reg1, AsmOp.DIV, reg2));
                // 取商: mflo rd
                emit(new MDRegAsm(AsmOp.MFLO, targetReg));
                break;

            case SREM:
                // Rem: div rs, rt
                emit(new MulDivAsm(reg1, AsmOp.DIV, reg2));
                // 取余: mfhi rd
                emit(new MDRegAsm(AsmOp.MFHI, targetReg));
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
            emit(new LiAsm(targetReg, 0));
            return;
        }
        if (constInt == 1) {
            emit(new MoveAsm(targetReg, varReg));
            return;
        }
        if (constInt == -1) {
             emit(new NegAsm(targetReg, varReg));
            return;
        }
        boolean isNegative = constInt < 0;
        int absConstInt = Math.abs(constInt);

        // 尝试使用移位优化 (Shift Optimization)
        // ... (这部分的 switch-case 保持不变，因为移位指令如 sll 是合法的 R-Type) ...
        switch (absConstInt) {
            case 2:
                emit(new CalcAsm(targetReg, AsmOp.ADDU, varReg, varReg));
                break;
            case 3:
                emit(new CalcAsm(Register.V0, AsmOp.ADDU, varReg, varReg));
                emit(new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, varReg));
                break;
            case 4:
                emit(new CalcAsm(targetReg, AsmOp.SLL, varReg, 2));
                break;
            case 5:
                emit(new CalcAsm(Register.V0, AsmOp.SLL, varReg, 2));
                emit(new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, varReg));
                break;
            case 6:
                emit(new CalcAsm(Register.V0, AsmOp.SLL, varReg, 2));
                emit(new CalcAsm(Register.V1, AsmOp.ADDU, varReg, varReg));
                emit(new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, Register.V1));
                break;
            case 7:
                emit(new CalcAsm(Register.V0, AsmOp.SLL, varReg, 3));
                emit(new CalcAsm(targetReg, AsmOp.SUBU, Register.V0, varReg));
                break;
            case 8:
                emit(new CalcAsm(targetReg, AsmOp.SLL, varReg, 3));
                break;
            case 9:
                emit(new CalcAsm(Register.V0, AsmOp.SLL, varReg, 3));
                emit(new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, varReg));
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
                        emit(new CalcAsm(targetReg, AsmOp.SLL, varReg, shifts[0]));
                    } else {
                        emit(new CalcAsm(Register.V0, AsmOp.SLL, varReg, shifts[0]));
                        emit(new CalcAsm(Register.V1, AsmOp.SLL, varReg, shifts[1]));
                        emit(new CalcAsm(targetReg, AsmOp.ADDU, Register.V0, Register.V1));
                    }
                } else {
                    // 【关键修改点】 对于其他情况，使用标准乘法 MULT

                    // 1. 将绝对值常量加载到临时寄存器 V0
                    emit(new LiAsm(Register.V0, absConstInt));

                    // 2. 使用 MUL 指令
                    emit(new CalcAsm(targetReg, AsmOp.MUL, varReg, Register.V0));
                }
        }

        if (isNegative) {
             emit(new NegAsm(targetReg, targetReg));
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
        emit(new JumpAsm(AsmOp.J, blockLabel));
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

        // --- 【修复 1】：处理条件是常量的情况 (常量折叠) ---
        // 这一步能防止后面去 var2Offset 查常量导致 NPE
        if (cond instanceof ConstInt) {
            int val = ((ConstInt) cond).getValue();
            if (val != 0) {
                // 条件恒为真 -> 直接跳转到 trueLabel
                emit(new JumpAsm(AsmOp.J, trueLabel));
            } else {
                // 条件恒为假 -> 直接跳转到 falseLabel
                emit(new JumpAsm(AsmOp.J, falseLabel));
            }
            return; // 处理完毕，直接返回
        }

        // --- 3. 尝试融合比较指令 (Peephole Optimization) ---
        // 检查条件是否是一个比较指令 (BinaryInst 且是 Compare 类型)
        // 且该比较指令只被当前跳转使用 (如果被多次使用，可能需要保留计算结果，这里假设可以融合)
        if (cond instanceof BinaryInst && ((BinaryInst) cond).getOpCode().isCompare()) {
            BinaryInst compareInst = (BinaryInst) cond;

            // 3.1 准备操作数
            Value op1 = compareInst.getOp1();
            Value op2 = compareInst.getOp2();

            // --- 加载 op1 (左操作数) ---
            // 策略：如果是常量->li到K0; 如果是寄存器->直接用; 如果是栈->lw到K0
            Register reg1;
            if (op1 instanceof ConstInt) {
                reg1 = Register.K0;
                emit(new LiAsm(reg1, ((ConstInt) op1).getValue()));
            } else if (var2reg.containsKey(op1)) {
                reg1 = var2reg.get(op1);
            } else {
                // 栈加载保护
                Integer offset = var2Offset.get(op1);
                if (offset == null) throw new RuntimeException("CondBr Op1 not found in stack: " + op1.getName());
                reg1 = Register.K0;
                emit(new MemAsm(AsmOp.LW, reg1, Register.SP, offset));
            }

            // --- 加载 op2 (右操作数) ---
            // 策略：如果是常量->记录数值(用于立即数优化); 否则同上，但使用 K1 防止覆盖 K0
            Register reg2 = null;
            int immVal = 0;
            boolean op2IsConst = (op2 instanceof ConstInt);

            if (op2IsConst) {
                immVal = ((ConstInt) op2).getValue();
            } else {
                if (var2reg.containsKey(op2)) {
                    reg2 = var2reg.get(op2);
                } else {
                    // 【关键】：这里必须用 K1，因为 K0 可能正在存放 op1
                    Integer offset = var2Offset.get(op2);
                    if (offset == null) throw new RuntimeException("CondBr Op2 not found in stack: " + op2.getName());
                    reg2 = Register.K1;
                    emit(new MemAsm(AsmOp.LW, reg2, Register.SP, offset));
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

            // 3.3 生成条件跳转指令 (满足条件跳到 True Block)
            if (op2IsConst) {
                // 优化：利用伪指令支持立即数比较 (例如: beq $t0, 100, label)
                emit(new BrAsm(trueLabel, reg1, asmOp, immVal));
            } else {
                // 标准：寄存器比较 (例如: beq $t0, $t1, label)
                emit(new BrAsm(trueLabel, reg1, asmOp, reg2));
            }

        } else {
            // --- 4. 情况 B: 条件只是一个普通的 i1 变量 ---
            // 逻辑：if (cond != 0) goto True

            Register condReg;
            if (var2reg.containsKey(cond)) {
                condReg = var2reg.get(cond);
            } else {
                // 【修复 2】：增加空指针检查，防止再次 NPE
                Integer offset = var2Offset.get(cond);
                if (offset == null) {
                    throw new RuntimeException("GenCode Error: Boolean condition value not found in stack/reg. Val=" + cond.getName());
                }
                // 使用 K0 加载
                condReg = Register.K0;
                emit(new MemAsm(AsmOp.LW, condReg, Register.SP, offset));
            }

            // 生成: bne $cond, $zero, trueLabel
            emit(new BrAsm(trueLabel, condReg, AsmOp.BNE, Register.ZERO));
        }

        // 5. 无条件跳转到 False Block (Fall-through 的替代)
        // 如果上面的 Branch 没跳走，说明条件不满足，去 False 分支
        emit(new JumpAsm(AsmOp.J, falseLabel));
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
            emit(new LiAsm(reg1, ((ConstInt) op1).getValue()));
        } else if (var2reg.containsKey(op1)) {
            reg1 = var2reg.get(op1);
        } else {
            emit(new MemAsm(AsmOp.LW, reg1, Register.SP, var2Offset.get(op1)));
        }

        Register reg2 = Register.K1;
        if (op2 instanceof ConstInt) {
            emit(new LiAsm(reg2, ((ConstInt) op2).getValue()));
        } else if (var2reg.containsKey(op2)) {
            reg2 = var2reg.get(op2);
        } else {
            emit(new MemAsm(AsmOp.LW, reg2, Register.SP, var2Offset.get(op2)));
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
        emit(new CmpAsm(targetReg, asmOp, reg1, reg2));

        // 2.5 溢出处理
        if (targetReg == Register.K0) {
            emit(new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(binaryInst)));
        }
    }

    private void buildRetInst(RetInst retInst) {
        // 1. 特殊处理 Main 函数
        // 在 MIPS 模拟器中，main 函数返回通常意味着程序终止
        if (isInMain) {
            // MIPS: li $v0, 10
            //       syscall
            emit(new LiAsm(Register.V0,10));
            emit(new SyscallAsm());
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
                emit(new LiAsm(Register.V0,((ConstInt)retVal).getValue()));
            } else if (var2reg.containsKey(retVal)) {
                // Case B: 返回值在寄存器中
                // MIPS: move $v0, $reg
                emit(new MoveAsm(Register.V0,var2reg.get(retVal)));
            } else {
                // Case C: 返回值在栈上
                // MIPS: lw $v0, offset($sp)
                emit(new MemAsm(AsmOp.LW,Register.V0,Register.SP,var2Offset.get(retVal)));
            }
        }

        // 3. 跳转回调用者
        // MIPS: jr $ra
        emit(new JumpAsm(AsmOp.JR,Register.RA));
    }

    private void buildCallInst(CallInst callInst) {
        Function targetFunc = (Function) callInst.getOperand(0);
        String funcName = targetFunc.getName();

        // =========================================================
        // 0. 库函数拦截 (保持你原有的高效逻辑)
        // =========================================================
        if (funcName.equals("putstr") || funcName.equals("@putstr")) {
            buildPutstrCore(callInst.getOperand(1)); // 假设你提取了这个 helper
            return;
        }
        if (funcName.equals("putint") || funcName.equals("@putint")) {
            Value val = callInst.getOperand(1);
            if (val instanceof ConstInt) {
                emit(new LiAsm(Register.A0, ((ConstInt) val).getValue()));
            } else if (var2reg.containsKey(val)) {
                emit(new MoveAsm(Register.A0, var2reg.get(val)));
            } else {
                emit(new MemAsm(AsmOp.LW, Register.A0, Register.SP, var2Offset.get(val)));
            }
            emit(new LiAsm(Register.V0, 1));
            emit(new SyscallAsm());
            return;
        }
        if (funcName.equals("getint") || funcName.equals("@getint")) {
            emit(new LiAsm(Register.V0, 5));
            emit(new SyscallAsm());
            Register targetReg = var2reg.getOrDefault(callInst, Register.K0);
            emit(new MoveAsm(targetReg, Register.V0));
            if (targetReg == Register.K0 && var2Offset.containsKey(callInst)) {
                emit(new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(callInst)));
            }
            return;
        }

        // =========================================================
        // 1. 【关键修改】确定需要保存的寄存器 (Caller-Saved Optimization)
        // =========================================================
        Set<Register> activeRegs = callInst.getActiveReg(); // 从指令中获取分析结果
        ArrayList<Register> savedRegs = new ArrayList<>();

        if (activeRegs != null && !activeRegs.isEmpty()) {
            // 【优化路径】：只保存 RegAlloc 认为活跃的寄存器
            for (Register reg : activeRegs) {
                if (isValidGeneralReg(reg)) {
                    savedRegs.add(reg);
                }
            }
        } else {
            // 【兜底路径】：如果 activeRegs 为空 (可能是没开优化，或者分析失败)
            // 回退到保守策略：保存所有 var2reg 中已分配的寄存器
            for (Register reg : var2reg.values()) {
                if (isValidGeneralReg(reg)) {
                    if (!savedRegs.contains(reg)) {
                        savedRegs.add(reg);
                    }
                }
            }
        }

        // 必须排序！保证压栈和出栈顺序的一致性 (避免随机性 Bug)
        Collections.sort(savedRegs);

        // =========================================================
        // 2. 执行压栈 (Save Context)
        // =========================================================
        for (Register reg : savedRegs) {
            curStackOffset -= 4;
            emit(new MemAsm(AsmOp.SW, reg, Register.SP, curStackOffset));
        }

        // =========================================================
        // 3. 保存 $ra
        // =========================================================
        curStackOffset -= 4;
        int raOffset = curStackOffset;
        emit(new MemAsm(AsmOp.SW, Register.RA, Register.SP, raOffset));

        // =========================================================
        // 4. 准备参数 (保持原有逻辑)
        // =========================================================
        int argCount = callInst.getNumOperands() - 1;

        for (int i = 0; i < argCount; i++) {
            Value arg = callInst.getOperand(i + 1);

            if (i < 4) {
                // 前 4 个参数 -> 寄存器 $a0-$a3
                Register argReg = Register.getByOffset(Register.A0, i);
                if (arg instanceof ConstInt) {
                    emit(new LiAsm(argReg, ((ConstInt) arg).getValue()));
                } else if (var2reg.containsKey(arg)) {
                    emit(new MoveAsm(argReg, var2reg.get(arg)));
                } else {
                    emit(new MemAsm(AsmOp.LW, argReg, Register.SP, var2Offset.get(arg)));
                }
            } else {
                // 栈参数：存放在 RA 下方
                int paramOffset = raOffset - 4 * (i - 4 + 1);
                Register temp = Register.K0;

                if (arg instanceof ConstInt) {
                    emit(new LiAsm(temp, ((ConstInt) arg).getValue()));
                } else if (var2reg.containsKey(arg)) {
                    temp = var2reg.get(arg);
                } else {
                    emit(new MemAsm(AsmOp.LW, temp, Register.SP, var2Offset.get(arg)));
                }
                emit(new MemAsm(AsmOp.SW, temp, Register.SP, paramOffset));
            }
        }

        // =========================================================
        // 5. 调整栈指针 ($sp) 并跳转
        // =========================================================
        int extraArgs = Math.max(0, argCount - 4);
        int stackSpaceForArgs = extraArgs * 4;

        // SP 下降：覆盖当前栈帧 + 保存的寄存器 + RA + 栈参数
        emit(new CalcAsm(Register.SP, AsmOp.ADDIU, Register.SP, curStackOffset - stackSpaceForArgs));

        // 跳转 (记得处理符号)
        emit(new JumpAsm(AsmOp.JAL, parseLabel(targetFunc.getName())));

        // =========================================================
        // 6. 恢复 SP
        // =========================================================
        emit(new CalcAsm(Register.SP, AsmOp.ADDIU, Register.SP, -(curStackOffset - stackSpaceForArgs)));

        // =========================================================
        // 7. 恢复 $ra
        // =========================================================
        emit(new MemAsm(AsmOp.LW, Register.RA, Register.SP, raOffset));
        curStackOffset += 4; // 逻辑弹栈 RA

        // =========================================================
        // 8. 恢复 Caller-Saved 寄存器 (倒序)
        // =========================================================
        // 这里的 tempOffset 逻辑是你原有的，它是对的。
        // 压栈顺序：RegA(High) -> RegB(Low) -> RA
        // 此时 curStackOffset 在 RA 上方，也就是 RegB 的位置
        // 你的逻辑是：从 RA 的位置 (raOffset) 往回找
        int tempOffset = raOffset;
        for (int i = savedRegs.size() - 1; i >= 0; i--) {
            tempOffset += 4; // 往回找上一个存的位置 (即 RegB, 然后 RegA)
            emit(new MemAsm(AsmOp.LW, savedRegs.get(i), Register.SP, tempOffset));
        }
        curStackOffset += (savedRegs.size() * 4); // 逻辑弹栈 Regs

        // =========================================================
        // 9. 处理返回值 (保持原有逻辑)
        // =========================================================
        if (!(callInst.getType() instanceof VoidType)) {
            Register targetReg = var2reg.getOrDefault(callInst, Register.K0);
            emit(new MoveAsm(targetReg, Register.V0));
            if (targetReg == Register.K0 && var2Offset.containsKey(callInst)) {
                emit(new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(callInst)));
            }
        }
    }

    /**
     * 辅助方法：判断是否是需要保存的通用寄存器
     */
    private boolean isValidGeneralReg(Register reg) {
        return reg != Register.SP && reg != Register.RA && reg != Register.ZERO
                && reg != Register.K0 && reg != Register.K1 && reg != Register.GP;
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
        Register targetReg = var2reg.getOrDefault(gepInst, Register.K0);
        Value basePointer = gepInst.getPointer();

        // 1. 先计算所有索引的总偏移量，存入 $t8
        // 这样可以确保在覆盖 targetReg 之前，所有索引值都已被读取
        Register totalOffsetReg = Register.T8;
        emit(new LiAsm(totalOffsetReg, 0)); // init offset = 0

        for (int i = 1; i < gepInst.getNumOperands(); i++) {
            Value index = gepInst.getOperand(i);

            // 使用 V1 作为当前索引的临时寄存器
            Register currIdxReg = Register.V1;

            if (index instanceof ConstInt) {
                int val = ((ConstInt) index).getValue();
                if (val == 0) continue;
                emit(new LiAsm(currIdxReg, val));
            } else if (var2reg.containsKey(index)) {
                emit(new MoveAsm(currIdxReg, var2reg.get(index)));
            } else {
                Integer offset = var2Offset.get(index);
                if (offset == null) throw new RuntimeException("GEP index missing");
                emit(new MemAsm(AsmOp.LW, currIdxReg, Register.SP, offset));
            }

            // currIdx * 4
            emit(new CalcAsm(currIdxReg, AsmOp.SLL, currIdxReg, 2));

            // totalOffset += currIdxOffset
            emit(new CalcAsm(totalOffsetReg, AsmOp.ADDU, totalOffsetReg, currIdxReg));
        }

        // 2. 现在可以安全地加载基地址到 targetReg 了
        // 即使 targetReg 和 index 寄存器冲突也没关系，因为 index 已经用完了

        if (basePointer instanceof GlobalVar || basePointer instanceof ConstString) {
            // 全局变量/常量：la targetReg, label
            String label = parseLabel(basePointer.getName());
            emit(new LaAsm(targetReg, label));
        } else if (var2reg.containsKey(basePointer)) {
            // 寄存器：move targetReg, baseReg
            emit(new MoveAsm(targetReg, var2reg.get(basePointer)));
        } else {
            // 栈：lw targetReg, offset($sp)
            Integer offset = var2Offset.get(basePointer);
            if (offset == null) throw new RuntimeException("GEP base missing");
            emit(new MemAsm(AsmOp.LW, targetReg, Register.SP, offset));
        }

        // 3. 最终相加：Result = Base + TotalOffset
        emit(new CalcAsm(targetReg, AsmOp.ADDU, targetReg, totalOffsetReg));

        // 4. 溢出处理
        if (!var2reg.containsKey(gepInst)) {
            Integer offset = var2Offset.get(gepInst);
            if (offset != null) {
                emit(new MemAsm(AsmOp.SW, targetReg, Register.SP, offset));
            }
        }
    }



    private void buildGetintInst(GetintInst inst) {
        // 1. 系统调用 5 (read_int)
        emit(new LiAsm(Register.V0, 5));
        emit(new SyscallAsm());

        // 2. 将读入的结果 ($v0) 存入目标变量
        if (var2reg.containsKey(inst)) {
            // 目标在寄存器: move $reg, $v0
            emit(new MoveAsm(var2reg.get(inst), Register.V0));
        } else {
            // 目标在栈上: sw $v0, offset($sp)
            emit(new MemAsm(AsmOp.SW, Register.V0, Register.SP, var2Offset.get(inst)));
        }
    }

    // 提取出的通用 Putstr 生成逻辑
    // 参数 argValue 就是要输出的那个字符串地址（可能是 GEP，可能是 GlobalVar）
    private void buildPutstrCore(Value argValue) {
        // 1. GEP 透视优化 (上一轮提到的修复)
        if (argValue instanceof GepInst) {
            Value ptr = ((GepInst) argValue).getPointer();
            if (ptr instanceof ConstString) {
                argValue = ptr;
            }
        }

        // 2. 加载地址到 $a0
        if (argValue instanceof ConstString) {
            // 字符串常量优化
            String labelName = parseLabel(argValue.getName());
            emit(new LaAsm(Register.A0, labelName));
        } else {
            // 普通指针变量
            if (var2reg.containsKey(argValue)) {
                emit(new MoveAsm(Register.A0, var2reg.get(argValue)));
            } else {
                Integer offset = var2Offset.get(argValue);
                if (offset == null) {
                    // 容错处理：如果是指针是指向全局变量的
                    if(argValue instanceof GlobalVar) {
                        emit(new LaAsm(Register.A0, parseLabel(argValue.getName())));
                    } else {
                        throw new RuntimeException("Putstr Error: Arg not found. " + argValue);
                    }
                } else {
                    emit(new MemAsm(AsmOp.LW, Register.A0, Register.SP, offset));
                }
            }
        }

        // 3. Syscall
        emit(new LiAsm(Register.V0, 4));
        emit(new SyscallAsm());
    }

    private void buildPutintInst(PutintInst inst) {
        // 1. 准备参数到 $a0
        Value val = inst.getOperand(1); // 假设 getOperand(0) 是要输出的值

        if (val instanceof ConstInt) {
            emit(new LiAsm(Register.A0, ((ConstInt) val).getValue()));
        } else if (var2reg.containsKey(val)) {
            emit(new MoveAsm(Register.A0, var2reg.get(val)));
        } else {
            emit(new MemAsm(AsmOp.LW, Register.A0, Register.SP, var2Offset.get(val)));
        }

        // 2. 系统调用 1 (print_int)
        emit(new LiAsm(Register.V0, 1));
        emit(new SyscallAsm());
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
            emit(new LaAsm(Register.A0, labelName));

        } else {
            // --- 情况 B: 传入的是一个指针变量，例如 %v0 (通用路径) ---
            // 这里的 val 存的就是字符串的地址，我们需要把它放到 $a0 中
            // 这和 buildPutintInst 的逻辑类似，只是不用考虑立即数

            if (var2reg.containsKey(val)) {
                // 地址已经在寄存器里了 -> move $a0, $reg
                emit(new MoveAsm(Register.A0, var2reg.get(val)));
            } else {
                // 地址溢出在栈上 -> lw $a0, offset($sp)
                // 注意：这里必须加非空检查，防止像之前 GEP 那样报空指针
                Integer offset = var2Offset.get(val);
                if (offset == null) {
                    throw new RuntimeException("Putstr error: String pointer not found. Val=" + val.getName());
                }
                emit(new MemAsm(AsmOp.LW, Register.A0, Register.SP, offset));
            }
        }

        // 4. 系统调用 4 (print_string)
        emit(new LiAsm(Register.V0, 4));
        emit(new SyscallAsm());
    }

    private void buildTruncInst(TruncInst inst) {
        Value src = inst.getOperand(0);
        Register srcReg = Register.K0;

        // 1. 准备源操作数
        if (var2reg.containsKey(src)) {
            srcReg = var2reg.get(src);
        } else {
            emit(new MemAsm(AsmOp.LW, srcReg, Register.SP, var2Offset.get(src)));
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
        emit(new CalcAsm(targetReg, AsmOp.ANDI, srcReg, mask));

        // 5. 溢出处理
        if (!var2reg.containsKey(inst)) {
            emit(new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(inst)));
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
            emit(new LiAsm(targetReg, ((ConstInt) src).getValue()));
        } else if (var2reg.containsKey(src)) {
            emit(new MoveAsm(targetReg, var2reg.get(src)));
        } else {
            emit(new MemAsm(AsmOp.LW, targetReg, Register.SP, var2Offset.get(src)));
        }

        // 2. 溢出处理
        if (!var2reg.containsKey(inst)) {
            emit(new MemAsm(AsmOp.SW, targetReg, Register.SP, var2Offset.get(inst)));
        }
    }

    private String parseLabel(String irName) {
        // 1. 如果以 @ 开头，去掉它
        String name = irName.startsWith("@") ? irName.substring(1) : irName;

        // 2. 替换非法字符 (如 . 替换为 _)
        // @test.x0 -> test_x0
        return name.replace(".", "_");
    }




    private void emitDivOptimization(Register src, int divisor, Register dst) {
        // 1. 处理特殊边界情况
        if (divisor == 1) {
            emit(new MoveAsm(dst, src));
            return;
        }
        if (divisor == -1) {
            emit(new NegAsm(dst, src));
            return;
        }

        int absDivisor = Math.abs(divisor);
        boolean isNegative = (divisor < 0);

        // 2. 检查是否为 2 的幂次
        if ((absDivisor & (absDivisor - 1)) == 0) {
            emitPowerOfTwoDiv(src, absDivisor, dst);
        } else {
            // 3. 通用情况：计算魔数并生成指令
            emitGeneralDiv(src, absDivisor, dst);
        }

        // 4. 如果原除数是负数，结果取反
        if (isNegative) {
            emit(new NegAsm(dst, dst));
        }
    }

    /**
     * 针对除数是 2 的幂次的优化
     */
    private void emitPowerOfTwoDiv(Register src, int absDiv, Register dst) {
        int shiftBits = Integer.numberOfTrailingZeros(absDiv);
        Register tempReg = Register.V0; // 使用临时寄存器

        // sra $v0, $src, 31  -> 提取符号位
        emit(new CalcAsm(tempReg, AsmOp.SRA, src, 31));

        // srl $v0, $v0, (32 - k) -> 构造偏置值
        emit(new CalcAsm(tempReg, AsmOp.SRL, tempReg, 32 - shiftBits));

        // addu $v1, $src, $v0 -> 被除数 + 偏置
        Register adjustedSrc = Register.V1; // 使用另一个临时寄存器
        emit(new CalcAsm(adjustedSrc, AsmOp.ADDU, src, tempReg));

        // 最后算术右移
        emit(new CalcAsm(dst, AsmOp.SRA, adjustedSrc, shiftBits));
    }

    /**
     * 针对普通整数的优化 (Magic Number)
     */
    private void emitGeneralDiv(Register src, int absDiv, Register dst) {
        MagicData magic = computeMagicParams(absDiv);

        // 将魔数加载到 $v0
        emit(new LiAsm(Register.V0, (int) magic.multiplier));

        if (magic.multiplier >= 0x80000000L) {
            // 魔数超出范围，使用 MTHI + MADD
            emit(new MDRegAsm(AsmOp.MTHI, src));
            emit(new MulDivAsm(src, AsmOp.MADD, Register.V0));
        } else {
            // 正常乘法
            emit(new MulDivAsm(src, AsmOp.MULT, Register.V0));
        }

        // 取出高位结果
        emit(new MDRegAsm(AsmOp.MFHI, Register.V1));

        // 移位修正
        if (magic.shift > 0) {
            emit(new CalcAsm(Register.V0, AsmOp.SRA, Register.V1, magic.shift));
        } else {
            emit(new MoveAsm(Register.V0, Register.V1));
        }

        // 符号位修正：result += (src >> 31)
        emit(new CalcAsm(Register.A0, AsmOp.SRL, src, 31));
        emit(new CalcAsm(dst, AsmOp.ADDU, Register.V0, Register.A0));
    }

    /**
     * 计算魔数参数 (纯逻辑计算，不生成指令)
     */
    private MagicData computeMagicParams(int d) {
        long divisor = d;
        long twoPower31 = 1L << 31;
        long limit = (twoPower31 - (twoPower31 % divisor) - 1) / divisor;

        int p = 32;
        long one = 1L;
        while ((one << p) <= (limit * (divisor - ((one << p) % divisor)))) {
            p++;
        }

        long resMult = ((one << p) + divisor - ((one << p) % divisor)) / divisor;
        int resShift = p - 32;

        return new MagicData(resMult, resShift);
    }

    // 内部类：保存魔数结果
    private static class MagicData {
        long multiplier;
        int shift;

        public MagicData(long multiplier, int shift) {
            this.multiplier = multiplier;
            this.shift = shift;
        }
    }
}