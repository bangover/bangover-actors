package cloud.bangover.actors;

import cloud.bangover.actors.EventLoop.Dispatcher;
import cloud.bangover.actors.EventLoop.Worker;

/**
 * This class is the dispatcher implementation, working in the single thread.
 *
 * @author Dmitry Mikhaylenko
 *
 */
public class SingleThreadDispatcher implements Dispatcher {
  @Override
  public final void dispatch(Worker worker) {
    worker.execute();
  }
}
