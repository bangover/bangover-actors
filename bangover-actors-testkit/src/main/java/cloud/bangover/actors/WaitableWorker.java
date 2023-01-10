package cloud.bangover.actors;

import cloud.bangover.actors.EventLoop.Worker;
import java.util.concurrent.CountDownLatch;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class WaitableWorker implements Worker {
  private final Worker originalWorker;
  private final CountDownLatch workersExecutionWaiterLatch;

  @Override
  public void execute() {
    originalWorker.execute();
    workersExecutionWaiterLatch.countDown();
  }
}
