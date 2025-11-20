package backend.text;

import backend.MipsFile;

public class Label implements TextAssembly {
    private final String name;

    public Label(String name) {
        this.name = name;
        // 标签也是 Text 段的一部分，创建时自动加入
        MipsFile.getInstance().toText(this);
    }

    @Override
    public String toString() {
        // MIPS 汇编中标签以冒号结尾
        return name + ":";
    }
}
