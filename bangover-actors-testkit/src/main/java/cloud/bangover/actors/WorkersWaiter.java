package cloud.bangover.actors;

import cloud.bangover.errors.MustNeverBeHappenedError;
import java.util.concurrent.CountDownLatch;
import lombok.RequiredArgsConstructor;

/**
 * This class is responsible for waiting when the {@link FixedMessagesWaitingDispatcher} will
 * dispatch all it's events.
 *
 * @author Dmitry Mikhaylenko
 *
 */
@RequiredArgsConstructor
public class WorkersWaiter {
  private final CountDownLatch workersExecutionWaiterLatch;

  /**
   * Wait dispatching completion.
   */
  public void await() {
    try {
      workersExecutionWaiterLatch.await();
    } catch (InterruptedException errors) {
      throw new MustNeverBeHappenedError(errors);
    }
  }
}
