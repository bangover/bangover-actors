package cloud.bangover.actors;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * This class represents the correlation key value, identifying a business operation in the whole
 * system. For example module A started an operation, but the operation has to be continued in the
 * remote module B and moreover module B has to publish event which should be recognized by the
 * module A as the event, happened during the kind of that initial operation processing, which was
 * started by module A. The correlation key allow to match the operation by the correlation key. You
 * can match two messages by correlation key to decide if the messages assigned to the same.
 * operation.
 *
 * @author Dmitry Mikhaylenko
 *
 */
@EqualsAndHashCode
@RequiredArgsConstructor(staticName = "wrap")
public class CorrelationKey {
  public static final CorrelationKey UNCORRELATED = CorrelationKey.wrap("");

  @NonNull
  private final String operationKey;

  /**
   * Check that the correlation key represents correlated value.
   *
   * @return True if the value is correlated key and false otherwise
   */
  public boolean isRepresentCorrelated() {
    return !UNCORRELATED.equals(this);
  }

  @Override
  public String toString() {
    return operationKey;
  }
}
