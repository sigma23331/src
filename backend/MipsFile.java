package backend;

import backend.global.GlobalAssembly;
import backend.text.Label;
import backend.text.TextAssembly;
import middle.component.type.ArrayType;

import java.util.ArrayList;
import java.util.List;

public class MipsFile {
    private static final MipsFile INSTANCE = new MipsFile();
    private final ArrayList<GlobalAssembly> dataSegment = new ArrayList<>(); // 数据段
    private final ArrayList<TextAssembly> textSegment = new ArrayList<>();   // 代码段
    private boolean insert = true;

    private MipsFile() {
    }

    public static MipsFile getInstance() {
        return INSTANCE;
    }

    // ... 原有的 toData, toText, getter ...

    public void toData(GlobalAssembly globalAssembly) {
        if (insert) {
            dataSegment.add(globalAssembly);
        }
    }

    public void toText(TextAssembly textAssembly) {
        if (insert) {
            textSegment.add(textAssembly);
        }
    }

    /**
     * 【新增方法】接收优化后的指令列表，更新 Text 段
     * @param optimizedInstructions 经过 PeepHole 优化的指令列表
     */
    public void updateTextSegment(List<Object> optimizedInstructions) {
        // 1. 清空原有的代码段 (防止重复)
        this.textSegment.clear();

        // 2. 将优化后的指令加入
        for (Object obj : optimizedInstructions) {
            if (obj instanceof TextAssembly) {
                this.textSegment.add((TextAssembly) obj);
            } else {
                // 如果 Object 不是 TextAssembly (比如是 String 注释等)，根据你的架构处理
                // 通常你的 MemAsm, CalcAsm 都应该继承自 TextAssembly
                System.err.println("Warning: Object is not TextAssembly: " + obj.getClass());
            }
        }
    }

    public ArrayList<GlobalAssembly> getDataSegment() {
        return dataSegment;
    }

    public ArrayList<TextAssembly> getTextSegment() {
        return textSegment;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(".data\n");
        // sb.append(".align 2\n"); // 通常不需要在这里全局加，GlobalVar 自己会处理
        for (GlobalAssembly globalAssembly : dataSegment) {
            // sb.append(".align 2\n"); // 同上
            sb.append(globalAssembly).append("\n");
        }
        sb.append("\n");
        sb.append(".text\n"); // 注意：标准 MIPS 是 .text 不是 .text:
        for (int i = 0; i < textSegment.size(); i++) {
            TextAssembly assembly = textSegment.get(i);
            // Label 顶格写，指令缩进
            if (assembly instanceof backend.text.Label) {
                sb.append(assembly).append("\n");
            } else {
                sb.append("\t").append(assembly).append("\n");
            }
        }
        return sb.toString();
    }
}
