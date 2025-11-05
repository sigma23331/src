package error; // 推荐包名使用全小写

import java.util.ArrayList;
import java.util.List;

public class ErrorHandler {
    private final List<Error> errors;

    // 2. 提供一个公共的静态方法来获取唯一的实例
    public static ErrorHandler getInstance() {
        return SingletonHolder.INSTANCE;
    }

    // 1. 私有化构造函数，防止外部直接实例化
    private ErrorHandler() {
        errors = new ArrayList<>();
    }

    // 3. 内部静态类，用于持有单例实例
    private static class SingletonHolder {
        private static final ErrorHandler INSTANCE = new ErrorHandler();
    }

    // 记录一个错误
    public void addError(ErrorType type, int line) {
        errors.add(new Error(type, line));
    }

    // 检查是否有错误发生
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    // 【新增方法】获取排好序的错误列表，供 Compiler 类写入文件
    public List<Error> getSortedErrors() {
        errors.sort((e1, e2) -> Integer.compare(e1.getLine(), e2.getLine())); // 按行号排序
        return errors;
    }

    // (原有的 printAllErrors 方法可以保留，用于调试时在控制台输出)
    public void printAllErrors() {
        for (Error error : getSortedErrors()) { // 直接复用新方法
            System.out.println(error);
        }
    }
}