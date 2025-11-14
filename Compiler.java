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

public class Compiler {
    public static void main(String[] args) {
        // 定义输入输出文件名
        String inputFile = "testfile.txt";
        String symbolOutputFile = "symbol.txt"; // 用于成功时的符号表输出
        String errorOutputFile = "error.txt";   // 用于错误时的输出
        String irOutputFile = "llvm_ir.txt";

        try {
            // 1. 读取 testfile.txt 中的全部内容
            byte[] bytes = Files.readAllBytes(Paths.get(inputFile));
            String sourceCode = new String(bytes, StandardCharsets.UTF_8);

            // 2. 初始化错误处理器和符号记录器
            ErrorHandler errorHandler = ErrorHandler.getInstance();
            SymbolLogger.getInstance().clear(); // 清空上次运行的记录

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

            // 5. --- 【修改】检查最终结果并输出 ---
            if (errorHandler.hasErrors()) {
                // 如果在任何阶段发现了错误，则输出错误并终止
                printErrors(errorHandler.getSortedErrors(), errorOutputFile);
            } else {
                // --- 成功！ ---

                // 1. (原逻辑) 输出符号表
                printSymbols(SymbolLogger.getInstance().getRecords(), symbolOutputFile);

                // --- 【新增：第 3 遍：IR 生成】 ---
                // 2. 创建 IRBuilder (Pass 3)
                //    (我们重用了 Pass 1 填充好的 ScopeManager)
                IRBuilder irBuilder = new IRBuilder(scopeManager);

                // 3. *关键*：执行 IR 生成，获取 IR Module
                Module irModule = irBuilder.build(astRoot);

                // 4. 将生成的 IR Module 写入文件
                printIR(irModule, irOutputFile);
            }

        } catch (IOException e) {
            System.err.println("Error reading or writing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 【新增方法】将 IR Module 写入 llvm_ir.txt
     */
    private static void printIR(Module module, String filePath) throws IOException {
        // // 提示：
        // // 1. 调用我们 Module 类的 toString() 方法
        String irCode = module.toString();
        //
        // // 2. 将字符串写入文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(irCode);
        }
    }

    /**
     * 【新增方法】将符号记录列表写入 symbol.txt
     */
    private static void printSymbols(List<SymbolRecord> records, String filePath) throws IOException {
        records.sort(Comparator.comparingInt(SymbolRecord::getScopeId));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (SymbolRecord record : records) {
                writer.write(record.toString());
                writer.newLine();
            }
        }
    }

    /**
     * 将 Error 列表按照指定格式写入文件
     */
    private static void printErrors(List<Error> errors, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Error error : errors) {
                writer.write(error.toString());
                writer.newLine();
            }
        }
    }
}