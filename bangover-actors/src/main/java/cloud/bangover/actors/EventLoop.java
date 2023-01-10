package cloud.bangover.actors;

import java.util.Collection;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * This class performs handling of the one event-loop tick. It awake all asleep workers using
 * {@link Alarm} and dispatch them using {@link Dispatcher} on the every tick.
 *
 * @author Dmitry Mikhaylenko
 *
 */
@RequiredArgsConstructor
public final class EventLoop {
  @NonNull
  private final Dispatcher dispatcher;
  
  @NonNull
  private final Alarm alarm;
  
  /**
   * Dispatch all asleep workers.
   */
  public final void dispatchAsleepWorkers() {
    Collection<Worker> workers = alarm.wakeupAsleepWorkers();
    workers.forEach(dispatcher::dispatch);
  }

  /**
   * This interface declares the contract for workers dispatching and making decision about workers
   * loading. For example if we need to run N workers on M threads parallel, we should implement
   * this interface and schedule N workers running on the M threads, assigning workers to threads,
   * in the dispatch method.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public interface Dispatcher {
    /**
     * Dispatch the worker running.
     *
     * @param worker The worker
     */
    public void dispatch(Worker worker);
  }

  /**
   * This interface declares the contract for waking up of workers doing nothing at the moment. At
   * the moment there are workers which could perform some work and can't be used and workers that
   * has already done their work and just sleeping doing nothing. Every tick, event loop wakes up
   * all sleeping workers and dispatch them.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public interface Alarm {
    /**
     * Wake up all asleep workers.
     *
     * @return The collection of woken up workers
     */
    public Collection<Worker> wakeupAsleepWorkers();
  }

  /**
   * This interface represents the worker component. The worker is a component doing any routine
   * work.
   *
   * @author Dmitry Mikhaylenko
   */
  public interface Worker {
    /**
     * Execute a worker task.
     */
    public void execute();
  }
}
