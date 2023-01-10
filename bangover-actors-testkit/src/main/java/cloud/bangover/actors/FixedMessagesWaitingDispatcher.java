package cloud.bangover.actors;

import cloud.bangover.actors.EventLoop.Dispatcher;
import cloud.bangover.actors.EventLoop.Worker;
import java.util.concurrent.CountDownLatch;

/**
 * This dispatcher implementation is responsible for dispatching only specified count of messages.
 *
 * @author Dmitry Mikhaylenko
 *
 */
public class FixedMessagesWaitingDispatcher implements Dispatcher {
  private final CountDownLatch latch;
  private final Dispatcher wrappedDispatcher;

  /**
   * Create the dispatcher.
   *
   * @param messagesCount     The messages count
   * @param wrappedDispatcher The dispatcher which the current dispatcher wraps
   */
  public FixedMessagesWaitingDispatcher(int messagesCount, Dispatcher wrappedDispatcher) {
    super();
    this.latch = new CountDownLatch(messagesCount);
    this.wrappedDispatcher = wrappedDispatcher;
  }

  @Override
  public void dispatch(Worker worker) {
    wrappedDispatcher.dispatch(new WaitableWorker(worker, latch));
  }

  public WorkersWaiter getWaiter() {
    return new WorkersWaiter(latch);
  }

  public static final FixedMessagesWaitingDispatcher singleThreadDispatcher(int messagesCount) {
    return new FixedMessagesWaitingDispatcher(messagesCount, worker -> worker.execute());
  }
}
