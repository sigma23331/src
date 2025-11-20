package backend.global;

import backend.MipsFile;
import java.util.ArrayList;
import java.util.List;

public class Word implements GlobalAssembly {
    private final String varName;
    private final int singleValue;
    private final List<Integer> arrayValues;
    private final int arraySize;
    private final boolean isArray;

    /**
     * 构造标量全局变量
     * 对应 MipsBuilder: new Word("var", 0)
     */
    public Word(String varName, int value) {
        this.varName = varName;
        this.singleValue = value;
        this.arrayValues = null;
        this.arraySize = 0;
        this.isArray = false;

        MipsFile.getInstance().toData(this);
    }

    /**
     * 构造数组全局变量
     * 对应 MipsBuilder: new Word("arr", list, length)
     */
    public Word(String varName, ArrayList<Integer> values, int length) {
        this.varName = varName;
        this.singleValue = 0;
        this.arrayValues = values; // 如果 values 为 null，代表全 0 初始化
        this.arraySize = length;
        this.isArray = true;

        MipsFile.getInstance().toData(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(varName).append(": .word ");

        if (!isArray) {
            // 标量情况
            sb.append(singleValue);
        } else {
            // 数组情况
            if (arrayValues != null && !arrayValues.isEmpty()) {
                // 这一步将数组元素展开，用逗号分隔
                // 例如: .word 1, 2, 3, 0, 0 ...
                for (int i = 0; i < arraySize; i++) {
                    if (i < arrayValues.size()) {
                        sb.append(arrayValues.get(i));
                    } else {
                        // 如果初始值列表短于数组长度，剩余部分补零
                        sb.append(0);
                    }

                    if (i < arraySize - 1) {
                        sb.append(", ");
                    }
                }
            } else {
                // 如果没有初始值列表，则全 0
                // 虽然可以用 .space，但 .word 0:length 也是一种写法，
                // 这里为了稳妥，循环输出 0
                for (int i = 0; i < arraySize; i++) {
                    sb.append("0");
                    if (i < arraySize - 1) {
                        sb.append(", ");
                    }
                }
            }
        }
        return sb.toString();
    }
}
