package frontend.syntax;

import frontend.Token.Token;
import frontend.Token.TokenType;
import middle.Scope;

import java.util.ArrayList;

//语句块 Block → '{' { BlockItem } '}'

public class Block extends SyntaxNode {
    private final ArrayList<BlockItem> blockItems;
    private final Token finalToken; //标注结尾的}的行号

    public Block(ArrayList<BlockItem> blockItems,Token finalToken) {
        this.blockItems = blockItems;
        this.finalToken = finalToken;
    }

    @Override
    public void print() {
        System.out.println(TokenType.printType(TokenType.LBRACE));
        for (BlockItem item : blockItems) {
            item.print();
        }
        System.out.println(TokenType.printType(TokenType.RBRACE));
        System.out.println("<Block>");
    }

    public ArrayList<BlockItem> getBlockItems() {
        return blockItems;
    }

    public Token getFinalToken() {
        return finalToken;
    }
}
