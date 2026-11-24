package at.dumphey.itemeditor.engine;

/** Where in a rules file the parser currently is: used to build precise error messages. */
public final class ParseContext {

    private final TriggerEngine engine;
    private final String path;

    ParseContext(TriggerEngine engine, String path) {
        this.engine = engine;
        this.path = path;
    }

    public TriggerEngine engine() {
        return engine;
    }

    public String path() {
        return path;
    }

    public ParseContext child(String key) {
        return new ParseContext(engine, path + "." + key);
    }

    public ParseContext index(int i) {
        return new ParseContext(engine, path + "[" + i + "]");
    }

    public ConfigException error(String message) {
        return new ConfigException(path + ": " + message);
    }
}
