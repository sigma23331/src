package backend;

import backend.global.GlobalAssembly;
import backend.text.Label;
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
            dataSegment.add(globalAssembly);
        }
    }

    public void toText(TextAssembly textAssembly) {
        if (insert) {
            textSegment.add(textAssembly);
        }
    }

    public ArrayList<GlobalAssembly> getDataSegment() {
        return dataSegment;
    }

    public ArrayList<TextAssembly> getTextSegment() {
        return textSegment;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(".data:\n");
        for (GlobalAssembly globalAssembly : dataSegment) {
            sb.append(globalAssembly).append("\n");
        }
        sb.append("\n");
        sb.append(".text:\n");
        for (int i = 0; i < textSegment.size(); i++) {
            TextAssembly assembly = textSegment.get(i);
            if (assembly instanceof Label) {
                sb.append(assembly).append("\n");
                continue;
            }
            sb.append("    ").append(assembly).append("\n");
            if (i < textSegment.size() - 1
                    && textSegment.get(i + 1) instanceof Label) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
