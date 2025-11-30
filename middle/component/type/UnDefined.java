package middle.component.type;

import middle.component.model.ConstInt;
import middle.component.type.IntegerType;

public class UnDefined extends ConstInt {
    public UnDefined() {
        super(IntegerType.get32(), 0);
    }

    @Override
    public String toString() {
        return "undefined";
    }
}
