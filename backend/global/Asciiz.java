package backend.global;

import backend.MipsFile;

public class Asciiz implements GlobalAssembly {
    private final String labelName;
    private final String strContent;

    /**
     * 构造字符串常量
     * 对应 MipsBuilder: new Asciiz("label", "content")
     */
    public Asciiz(String labelName, String strContent) {
        this.labelName = labelName;
        this.strContent = strContent;
        // 自动加入数据段
        MipsFile.getInstance().toData(this);
    }

    @Override
    public String toString() {
        // 格式: label: .asciiz "content"
        // 注意: 需要将原本的换行符转义形式处理一下，确保输出符合 MIPS 汇编格式
        String safeContent = strContent.replace("\n", "\\n").replace("\"", "\\\"");
        return String.format("%s: .asciiz \"%s\"", labelName, safeContent);
    }
}