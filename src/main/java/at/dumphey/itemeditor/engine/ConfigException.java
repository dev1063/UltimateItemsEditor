package at.dumphey.itemeditor.engine;

/** A mistake in a rules file. The message always starts with the exact path of the problem. */
public class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }
}
