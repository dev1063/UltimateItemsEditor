package at.dumphey.itemeditor.engine;

/** Builds a {@link Condition} from its YAML value, once, when the rules are loaded. */
@FunctionalInterface
public interface ConditionFactory {

    Condition create(Object config, ParseContext pc) throws ConfigException;
}
