import error.Error;
import error.ErrorHandler;
import frontend.Lexer;
import frontend.Parser;
import frontend.Token.Token;
import frontend.syntax.CompileUnit; // 引入 CompUnit
import middle.*;
import middle.component.model.Module;
import middle.symbol.SymbolRecord;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream; // 引入 PrintStream 用于重定向
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import backend.MipsBuilder;
import backend.MipsFile;

public class Compiler {
    public static void main(String[] args) {
        // 定义输入输出文件名
        String inputFile = "testfile.txt";
        String symbolOutputFile = "symbol.txt";
        String errorOutputFile = "error.txt";
        String irOutputFile = "llvm_ir.txt";
        String mipsOutputFile = "mips.txt"; // --- 【新增：MIPS 输出文件】 ---

        try {
            // 1. 读取 testfile.txt 中的全部内容
            byte[] bytes = Files.readAllBytes(Paths.get(inputFile));
            String sourceCode = new String(bytes, StandardCharsets.UTF_8);

            // 2. 初始化错误处理器和符号记录器
            ErrorHandler errorHandler = ErrorHandler.getInstance();
            SymbolLogger.getInstance().clear();

            // 3. 词法分析
            Lexer lexer = new Lexer(sourceCode);
            ArrayList<Token> tokens = lexer.tokenize();

            // 4. 语法分析
            Parser parser = new Parser(tokens);
            CompileUnit astRoot = parser.parse();

            // 4a. 创建作用域管理器
            ScopeManager scopeManager = new ScopeManager(errorHandler);

            // 4b. 第一遍：符号收集
            SymbolCollector collector = new SymbolCollector(scopeManager, errorHandler);
            collector.visit(astRoot);

            // 4c. 第二遍：语义验证
            SemanticValidator validator = new SemanticValidator(scopeManager, errorHandler);
            validator.visit(astRoot);

            // --- 【语义分析结束】 ---

            // 5. 检查最终结果并输出
            if (errorHandler.hasErrors()) {
                // 如果在任何阶段发现了错误，则输出错误并终止
                printErrors(errorHandler.getSortedErrors(), errorOutputFile);
            } else {
                // --- 成功！ ---

                // 1. 输出符号表
                printSymbols(SymbolLogger.getInstance().getRecords(), symbolOutputFile);

                // --- 第 3 遍：IR 生成 ---
                IRBuilder irBuilder = new IRBuilder(scopeManager);
                Module irModule = irBuilder.build(astRoot);

                // 2. 输出 IR 到文件
                printIR(irModule, irOutputFile);

                // --- 【新增：第 4 遍：后端生成 MIPS】 ---

                // 3. 创建 MipsBuilder
                // 参数2: optimizeOn (是否开启优化)，目前我们传入 false
                MipsBuilder mipsBuilder = new MipsBuilder(irModule, false);

                // 4. 执行构建
                // 这会将指令写入 MipsFile 的单例对象中
                mipsBuilder.build(false);

                // 5. 输出 MIPS 汇编到文件
                printMips(mipsOutputFile);
            }

        } catch (IOException e) {
            System.err.println("Error reading or writing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 【新增方法】将 MipsFile 单例中的内容写入 mips.txt
     */
    private static void printMips(String filePath) throws IOException {
        // 获取全局单例生成的汇编字符串
        String mipsCode = MipsFile.getInstance().toString();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(mipsCode);
        }
    }

    // ... (printIR, printSymbols, printErrors 保持不变) ...

    private static void printIR(Module module, String filePath) throws IOException {
        String irCode = module.toString();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(irCode);
        }
    }

    private static void printSymbols(List<SymbolRecord> records, String filePath) throws IOException {
        records.sort(Comparator.comparingInt(SymbolRecord::getScopeId));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (SymbolRecord record : records) {
                writer.write(record.toString());
                writer.newLine();
            }
        }
    }

    private static void printErrors(List<Error> errors, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Error error : errors) {
                writer.write(error.toString());
                writer.newLine();
            }
        }
    }
}