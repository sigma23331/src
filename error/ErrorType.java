package error;

public enum ErrorType {
    IllegalSymbol("a"),
    RedefineIdent("b"),
    UndefinedIdent("c"),
    FuncParamCountMismatch("d"),
    FuncParamTypeMismatch("e"),
    VoidFuncReturn("f"),
    MissingReturn("g"),
    ConstAssign("h"),
    MissingSemicolon("i"),
    MissingRParent("j"),
    MissingRBrack("k"),
    PrintfParamMismatch("l"),
    NoLoopBreak("m");

    private String name;

    ErrorType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}