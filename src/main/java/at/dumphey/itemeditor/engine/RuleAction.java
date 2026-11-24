package at.dumphey.itemeditor.engine;

/** Something that happens when a rule fires. */
@FunctionalInterface
public interface RuleAction {

    void execute(TriggerContext ctx);
}
