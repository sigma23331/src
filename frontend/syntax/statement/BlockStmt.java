package frontend.syntax.statement;

import frontend.syntax.Block;
import middle.Scope;

// Block

public class BlockStmt extends Stmt {
    private final Block block;

    public BlockStmt(Block block) {
        this.block = block;
    }

    @Override
    public void print() {
        block.print();
        System.out.println("<Stmt>");
    }

    public Block getBlock() {
        return block;
    }

    private Scope scope; // 用于存储此函数对应的作用域

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return this.scope;
    }
}
