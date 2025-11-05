package error;

public class Error {
    private final ErrorType type;
    private final int line;

    Error(ErrorType type,int line) {
        this.type = type;
        this.line = line;
    }

    public ErrorType getType() {
        return type;
    }

    public int getLine() {
        return line;
    }

    @Override
    public String toString() {
        return line + " " + type.toString();
    }
}
