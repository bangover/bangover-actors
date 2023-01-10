package cloud.bangover.actors;

import cloud.bangover.actors.EventLoop.Dispatcher;
import cloud.bangover.actors.EventLoop.Worker;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(staticName = "of")
public class DispatcherTestCase {
  private final Dispatcher dispatcher;

  public TestCaseExecutionReport execute() {
    // Given
    MockWorker firstWorker = new MockWorker();
    MockWorker secondWorker = new MockWorker();
    MockWorker thirdWorker = new MockWorker();
    MockWorker fourthWorker = new MockWorker();
    CountDownLatch dispatchersAwaitLatch = new CountDownLatch(4);
    WorkersWaiter waiter = new WorkersWaiter(dispatchersAwaitLatch);
    Collection<Worker> workers = Arrays.asList(
      firstWorker,
      secondWorker,
      thirdWorker,
      fourthWorker
    );

    // When
    dispatchAllWorkers(dispatchersAwaitLatch, dispatcher, workers);
    waiter.await();

    // Then
    return new TestCaseExecutionReport(firstWorker.getHistory().hasEntries() && 
        secondWorker.getHistory().hasEntries() && 
        thirdWorker.getHistory().hasEntries() && 
        fourthWorker.getHistory().hasEntries());
  }

  private void dispatchAllWorkers(CountDownLatch dispatcherAwaitLatch, Dispatcher dispatcher,
      Collection<Worker> workers) {
    for (Worker worker : workers) {
      dispatcher.dispatch(new WaitableWorker(worker, dispatcherAwaitLatch));
    }
  }
  
  @Getter
  @RequiredArgsConstructor
  public static class TestCaseExecutionReport {
    private final boolean successfullyCompleted;
  }
}
