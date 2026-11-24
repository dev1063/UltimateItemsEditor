package at.dumphey.itemeditor.engine;

/** Builds an {@link RuleAction} from its YAML value, once, when the rules are loaded. */
@FunctionalInterface
public interface ActionFactory {

    RuleAction create(Object config, ParseContext pc) throws ConfigException;
}
