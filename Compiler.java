import error.Error;
import error.ErrorHandler;
import frontend.Lexer;
import frontend.Parser;
import frontend.Token.Token;
import frontend.syntax.CompileUnit;
import middle.*;
import middle.component.model.Module;
import middle.symbol.SymbolRecord;

// 【新增：引入优化器】
import middle.Optimizer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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
        String mipsOutputFile = "mips.txt";

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

                // --- 【新增：中间代码优化】 ---
                // 在输出 IR 和生成后端代码之前，运行优化器

                // 开关：控制是否开启优化（方便调试，如果出错了改为 false 对比）
                boolean openOptimize = true;

                if (openOptimize) {
                    // 创建优化器并传入 Module
                    Optimizer optimizer = new Optimizer(irModule);
                    // 执行优化 (调用你之前定义的 optimize() 方法)
                    optimizer.run();
                }

                // 2. 输出 IR 到文件
                // (注意：如果开启了优化，这里输出的就是优化后的 IR)
                printIR(irModule, irOutputFile);

                // --- 第 4 遍：后端生成 MIPS ---

                // 3. 创建 MipsBuilder
                // 参数2: optimizeOn (告诉后端前端是否已经优化过，或者控制后端特有的优化)
                MipsBuilder mipsBuilder = new MipsBuilder(irModule, openOptimize);

                // 4. 执行构建
                mipsBuilder.build(true);

                // 5. 输出 MIPS 汇编到文件
                printMips(mipsOutputFile);
            }

        } catch (IOException e) {
            System.err.println("Error reading or writing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ... (printMips, printIR, printSymbols, printErrors 等辅助方法保持不变) ...

    private static void printMips(String filePath) throws IOException {
        String mipsCode = MipsFile.getInstance().toString();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(mipsCode);
        }
    }

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