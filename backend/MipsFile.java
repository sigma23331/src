package backend;

import backend.global.GlobalAssembly;
import backend.text.TextAssembly;
import middle.component.type.ArrayType;

import java.util.ArrayList;

public class MipsFile {
    private static final MipsFile INSTANCE = new MipsFile();
    private final ArrayList<GlobalAssembly> dataSegment = new ArrayList<>();//数据段
    private final ArrayList<TextAssembly> textSegment = new ArrayList<>();
    private boolean insert = true;

    private MipsFile() {

    }

    public static MipsFile getInstance() {
        return INSTANCE;
    }

    public void toData(GlobalAssembly globalAssembly) {
        if (insert) {
            textSegment
        }
    }
}
