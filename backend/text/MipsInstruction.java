package backend.text;

import backend.MipsFile;

// 实现 TextAssembly 接口，以便能被放入 MipsFile 的 textSegment
public abstract class MipsInstruction implements TextAssembly {

    public MipsInstruction() {
        // 核心修改：创建指令时，自动加入到全局单例 MipsFile 中
        MipsFile.getInstance().addToTextSegment(this);
    }

    @Override
    public abstract String toString();
}
