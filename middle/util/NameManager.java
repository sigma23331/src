package middle.util;

/**
 * 命名管理器。
 * 负责在 IR 生成期间为匿名的 Value (指令、基本块) 生成唯一ID。
 * * 关键区别：这是一个专用的类，而不是像 IRData 那样的静态混杂类。
 */
public class NameManager {

    private int valueCounter; // %v0, %v1, ...
    private int blockCounter; // %b0, %b1, ...

    public NameManager() {
        this.valueCounter = 0;
        this.blockCounter = 0;
    }

    /**
     * 当进入一个新函数时，必须调用此方法重置计数器。
     */
    public void reset() {
        this.valueCounter = 0;
        this.blockCounter = 0;
    }

    /**
     * 获取一个新的匿名 *值* (指令) 的名字。
     * @return 例如 "%v1"
     */
    public String newVarName() {
        // // 提示：
        return "%v" + (valueCounter++);
    }

    public String newVarName(String hint) {
        // // 提示：
        // // 为了简单起见，我们直接使用 hint 作为名字。
        // // 注意：LLVM 要求局部变量名以 % 开头。
        String cleanHint = hint.replace("[", "").replace("]", "")
                .replace("*", "p").replace(" ", "_");
        return "%" + cleanHint + "." + (valueCounter++);
    }

    /**
     * 获取一个新的 *基本块* 的名字。
     * @return 例如 "%b1" (LLVM 风格的标签通常不带%)
     */
    public String newBlockName() {
        // // 提示：
        // // LLVM 标签名通常不带 '%'，
        // // 但如果您的 BasicBlock.toString() 负责添加 '%'，这里就可以不加
        return "b" + (blockCounter++);
    }

    /**
     * (可选) 获取一个有意义的块名
     */
    public String newBlockName(String hint) {
        // // 提示：
        return hint + (blockCounter++);
    }
}
