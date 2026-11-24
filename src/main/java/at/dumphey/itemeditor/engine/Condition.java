package at.dumphey.itemeditor.engine;

/** A yes/no question about what just happened. Must be cheap and must not change anything. */
@FunctionalInterface
public interface Condition {

    boolean test(TriggerContext ctx);
}
