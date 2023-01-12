package cloud.bangover.actors;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

import cloud.bangover.actors.EventLoop.Worker;
import lombok.NonNull;

/**
 * This class represents the base actor.
 *
 * @author Dmitry Mikhaylenko
 * 
 * @param <T> The actor type
 */
public abstract class Actor<T> {
  private final Queue<Message<T>> messageQueue;
  private final Context context;

  private ActorState state;

  protected Actor(@NonNull Context context) {
    super();
    this.messageQueue = new LinkedList<>();
    this.state = ActorState.NEW;
    this.context = context;
  }

  final boolean isReadyToWork() {
    return state == ActorState.ASLEEP && !messageQueue.isEmpty();
  }

  final Mailbox getMailbox() {
    return new Mailbox();
  }

  final void start() {
    sleep();
  }

  final void submitMailboxMessages(Mailbox mailbox) {
    Queue<Message<T>> previousQueue = mailbox.extract();
    while (!previousQueue.isEmpty()) {
      this.messageQueue.offer(previousQueue.poll());
    }
  }

  /**
   * Get the current actor address.
   *
   * @return The current actor address
   */
  protected final ActorAddress self() {
    return ActorAddress.ofName(context.selfName());
  }

  /**
   * Tell about message to the actor system.
   *
   * @param <B>     The message body type
   * @param message The message
   * @return The actual message correlation key
   */
  protected final <B> CorrelationKey tell(@NonNull Message<B> message) {
    return context.tell(message);
  }

  /**
   * Stop the specified actor. Note that method is unsafe and you can break your system if you stop
   * the actor, providing the functional, shared between another actors. It is the most suitable
   * situation for using this method, when you are created an actor and you aren't needed in this
   * one anymore.
   *
   * @param target The target actor address
   */
  protected final void stop(@NonNull ActorAddress target) {
    context.stop(target);
  }

  /**
   * Restart the specified actor. Note that method is unsafe and you can break your system if you
   * restart the actor, providing the functional, shared between another actors. It is the most
   * suitable situation for using this method, when your actor has to make a decision about its
   * derived actor restarting.
   *
   * @param target The target actor addess.
   */
  protected final void restart(@NonNull ActorAddress target) {
    context.restart(target);
  }

  /**
   * Register the derived actor of current actor.
   *
   * @param <B>          The actor message body type name
   * @param actorName    The derived actor name (the full registered actor name will be different of
   *                     this value).
   * @param actorFactory The actor factory
   * @return The registered derived actor address
   */
  protected final <B> ActorAddress actorOf(@NonNull ActorName actorName,
      @NonNull Factory<B> actorFactory) {
    return context.actorOf(actorName, actorFactory);
  }

  /**
   * This lifecycle method is called after the actor is stopped and plugged out of the event loop.
   */
  protected void afterStop() {
  }

  /**
   * This lifecycle method is called before the actor is started and plugged in to the event loop.
   */
  protected void beforeStart() {
  }

  /**
   * This lifecycle method is called before the actor instance destroyed during restart
   * process,after the actor is stopped and plugged out of the event loop.
   */
  protected void beforeRestart() {
  }

  /**
   * This lifecycle method is called after the actor instance created during restart process, before
   * the actor is started and plugged in to the event loop.
   */
  protected void afterRestart() {
  }

  /**
   * Get a fault resolver. By default it resumes the actor, but you can redefine this method and
   * return your own fault resolver.
   *
   * @return The fault resolver
   */
  protected FaultResolver<T> getFaultResover() {
    return (lifecycle, message, error) -> {
      lifecycle.resume();
    };
  }

  /**
   * Receive the message from the mailbox. This method will be called when the message is ready to
   * be processed.
   *
   * @param message The message
   * @throws Exception an object, which is reason of the message handling break
   */
  protected abstract void receive(Message<T> message) throws Exception;

  private void wakeup() {
    this.state = ActorState.BUSY;
  }

  private void sleep() {
    this.state = ActorState.ASLEEP;
  }

  private void handleError(Message<T> message, Exception error) {
    getFaultResover().resolveError(new LifecycleController() {
      @Override
      public void resume() {
        sleep();
      }

      @Override
      public void restart() {
        context.restart(self());
      }

      @Override
      public void stop() {
        Actor.this.stop(self());
      }
    }, message, error);
  }

  class Mailbox {
    public final boolean isEmpty() {
      return messageQueue.isEmpty();
    }

    public final void put(Message<T> message) {
      messageQueue.offer(message);
    }

    public final Worker checkout() {
      wakeup();
      return () -> Optional.ofNullable(messageQueue.poll()).ifPresent(this::handleMessage);
    }

    final Queue<Message<T>> extract() {
      return new LinkedList<Message<T>>(messageQueue);
    }

    private void handleMessage(Message<T> message) {
      try {
        receive(message);
        sleep();
      } catch (Exception error) {
        state = ActorState.FAILED;
        handleError(message, error);
      }
    }
  }

  /**
   * This interface represents the contract for actor instance creating.
   *
   * @author Dmitry Mikhaylenko
   *
   * @param <T> The actor instance
   */
  public interface Factory<T> {
    public Actor<T> createActor(Context actorSystem);
  }

  /**
   * The actor internal states enumeration.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  enum ActorState {
    /**
     * An actor is instantiated, but isn't ready to handle messages.
     */
    NEW,
    /**
     * An actor isn't working and ready to receive a message.
     */
    ASLEEP,
    /**
     * An actor is working and it can't receive additional message.
     */
    BUSY,
    /**
     * An actor had an error during message processing and it couldn't recovered yet.
     */
    FAILED;
  }

  /**
   * This interface gets access to the lifecycle management of the current actor.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public interface LifecycleController {
    /**
     * Restart the actor.
     */
    public void restart();

    /**
     * Resume actor.
     */
    public void resume();

    /**
     * Stop actor.
     */
    public void stop();
  }

  /**
   * This interface declares the contract of the component, resolving an actor message processing
   * error.
   *
   * @author Dmitry Mikhaylenko
   *
   * @param <T> The message body type
   */
  public interface FaultResolver<T> {
    /**
     * Resolve error happened during message processing.
     *
     * @param lifecycle The actor lifecycle controller
     * @param message   The message, during which processing the error was happened
     * @param error     The happened error
     */
    public void resolveError(LifecycleController lifecycle, Message<T> message, Exception error);
  }

  /**
   * This interface declares the actor is associated to the current actor.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public interface Context {
    /**
     * Get the actor self name.
     *
     * @return The actor name
     */
    public ActorName selfName();

    /**
     * Tell about message to the actor system.
     *
     * @param <T>     The message body type
     * @param message The message
     * @return The actual correlation key
     */
    public <T> CorrelationKey tell(Message<T> message);

    /**
     * Create the actor instance in the actor system.
     *
     * @param <T>       The actor message body type name
     * @param actorName The actor name
     * @param factory   The actor factory
     * @return The actor reference
     */
    public <T> ActorAddress actorOf(ActorName actorName, Factory<T> factory);

    /**
     * Restart the specified actor.
     *
     * @param target The address of actor which is going to be restarted
     */
    public void restart(ActorAddress target);

    /**
     * Stop the specified actor.
     *
     * @param target The address of actor which is going to be stopped
     */
    public void stop(ActorAddress target);
  }
}
