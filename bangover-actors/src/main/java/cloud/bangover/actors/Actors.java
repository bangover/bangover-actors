package cloud.bangover.actors;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import cloud.bangover.actors.Actor.Context;
import cloud.bangover.actors.Actor.Factory;
import cloud.bangover.actors.Actor.Mailbox;
import cloud.bangover.actors.EventLoop.Alarm;
import cloud.bangover.actors.EventLoop.Dispatcher;
import cloud.bangover.actors.EventLoop.Worker;
import cloud.bangover.generators.Generator;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

/**
 * This class is responsible for actor system initialization.
 *
 * @author Dmitry Mikhaylenko
 *
 */
@UtilityClass
public class Actors {
  public final ActorSystem create(@NonNull SystemInitializer systemInitializer) {
    return new InternalActorsSystem(
        systemInitializer.initializeSystem(new InternalActorsSystemConfigurer()));
  }

  /**
   * This interface describes the contract of actors system configuring.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public interface SystemConfigurer {
    /**
     * Specify the dispatcher component.
     *
     * @param dispatcher The dispatcher
     * @return The system configurer instance
     */
    public SystemConfigurer withDispatcher(Dispatcher dispatcher);

    /**
     * Specify the correlation key generator.
     *
     * @param correlationKeyGenerator The correlation key generator
     * @return The system configurer instance
     */
    public SystemConfigurer withCorrelationKeyGenerator(
        Generator<CorrelationKey> correlationKeyGenerator);

    /**
     * Configure the system.
     *
     * @return The system configurer instance
     */
    public SystemConfiguration configure();
  }

  /**
   * This interface describes the methods getting access to the configured system dependencies.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public interface SystemConfiguration {
    /**
     * Get the dispatcher.
     *
     * @return The dispatcher instasnce
     */
    public Dispatcher getDispatcher();

    /**
     * Get the correlation key generator.
     *
     * @return The correlation key generator
     */
    public Generator<CorrelationKey> getCorrelationKeyGenerator();
  }

  /**
   * This interface declares the actor system initialization mechanism.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public interface SystemInitializer {
    /**
     * Initialize the system.
     *
     * @param configurer The system configurer
     * @return The system configuration
     */
    public SystemConfiguration initializeSystem(SystemConfigurer configurer);
  }

  /**
   * This exception notifies about the exceptional case, when the already registered actor is tried
   * to be created.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public class ActorDuplicationException extends RuntimeException {
    private static final long serialVersionUID = 1357258487421540699L;

    public ActorDuplicationException(ActorName actorName) {
      super(String.format("Actor %s has already been created", actorName));
    }
  }

  @Getter
  private class InternalActorsSystemConfigurer implements SystemConfigurer, SystemConfiguration {
    private Dispatcher dispatcher = new SingleThreadDispatcher();
    private Generator<CorrelationKey> correlationKeyGenerator = () -> CorrelationKey.UNCORRELATED;

    @Override
    public SystemConfigurer withDispatcher(Dispatcher dispatcher) {
      this.dispatcher = dispatcher;
      return this;
    }

    @Override
    public SystemConfigurer withCorrelationKeyGenerator(
        Generator<CorrelationKey> correlationKeyGenerator) {
      this.correlationKeyGenerator = correlationKeyGenerator;
      return this;
    }

    @Override
    public SystemConfiguration configure() {
      return this;
    }
  }

  private class InternalActorsSystem implements ActorSystem {
    private final ActorCreatorsPool creatorsPool;
    private final ActorsCoordinator actorsCoordinator;
    private final Generator<CorrelationKey> correlationKeyGenerator;
    private final EventLoop eventLoop;
    private boolean started;

    public InternalActorsSystem(SystemConfiguration configuration) {
      super();
      this.creatorsPool = new ActorCreatorsPool();
      this.actorsCoordinator = new ActorsCoordinator();
      this.eventLoop = new EventLoop(configuration.getDispatcher(), actorsCoordinator);
      this.correlationKeyGenerator = configuration.getCorrelationKeyGenerator();
      this.started = false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CorrelationKey tell(Message<T> message) {
      Message<T> correlatedMessage = message.correlateBy(correlationKeyGenerator.generateNext());
      Optional.ofNullable(message.getDestination())
          .flatMap(destination -> actorsCoordinator.getMailbox(destination.getActorName()))
          .ifPresent(mailbox -> mailbox.put(correlatedMessage));
      dispatchAllAsleepWorkers();
      return correlatedMessage.getCorrelationKey();
    }

    @Override
    public <T> ActorAddress actorOf(ActorName actorName, Factory<T> factory) {
      checkThatActorIsNotCreated(actorName);
      creatorsPool.registerActorCreator(actorName, factory);
      actorsCoordinator.start(actorName);
      return ActorAddress.ofName(actorName);
    }

    @Override
    public void start() {
      this.started = true;
    }

    @Override
    public void shutdown() {
      this.started = false;
      actorsCoordinator.stopAll();
    }

    private ExecutorService eventLoopExecutorService = Executors.newSingleThreadExecutor();

    private void dispatchAllAsleepWorkers() {
      if (started && actorsCoordinator.isContainNonEmptyMailbox()) {
        eventLoopExecutorService.execute(() -> {
          while (actorsCoordinator.isContainNonEmptyMailbox()) {
            eventLoop.dispatchAsleepWorkers();
          }
        });
      }
    }

    private void checkThatActorIsNotCreated(ActorName actorName) {
      if (creatorsPool.isRegistered(actorName)) {
        throw new ActorDuplicationException(actorName);
      }
    }

    @RequiredArgsConstructor
    private class SystemActorContext implements Context {
      private final ActorName actorName;

      @Override
      public ActorName selfName() {
        return actorName;
      }

      @Override
      public <T> CorrelationKey tell(Message<T> message) {
        return InternalActorsSystem.this.tell(message);
      }

      @Override
      public <T> ActorAddress actorOf(ActorName actorName, Factory<T> factory) {
        ActorName derivedActorName = this.actorName.deriveWith(actorName);
        return InternalActorsSystem.this.actorOf(derivedActorName, factory);
      }

      @Override
      public void restart(ActorAddress target) {
        actorsCoordinator.restart(target.getActorName());
      }

      @Override
      public void stop(ActorAddress target) {
        actorsCoordinator.stop(target.getActorName());
      }
    }

    private class ActorDescriptor<T> {
      private final Factory<T> actorFactory;
      private final SystemActorContext actorContext;

      public ActorDescriptor(ActorName actorName, Factory<T> actorFactory) {
        super();
        this.actorFactory = actorFactory;
        this.actorContext = new SystemActorContext(actorName);
      }

      public Actor<T> createActorInstance() {
        return actorFactory.createActor(actorContext);
      }
    }

    private class ActorCreatorsPool {
      private final Map<ActorName, ActorDescriptor<?>> creators = new ConcurrentHashMap<>();

      public boolean isRegistered(ActorName actorName) {
        return creators.containsKey(actorName);
      }

      public <T> void registerActorCreator(ActorName actorName, Factory<T> actorFactory) {
        ActorDescriptor<T> creator = new ActorDescriptor<>(actorName, actorFactory);
        this.creators.put(actorName, creator);
      }

      public void dispose(ActorName actorName) {
        this.creators.remove(actorName);
      }

      public Optional<ActorDescriptor<?>> getActorCreator(ActorName actorName) {
        return Optional.ofNullable(creators.get(actorName));
      }
    }

    private class ActorsCoordinator implements Alarm {
      private Map<ActorName, Actor<?>> actors = new ConcurrentHashMap<>();

      @Override
      public Collection<Worker> wakeupAsleepWorkers() {
        return actors.values().stream().filter(Actor::isReadyToWork)
            .map(actor -> actor.getMailbox().checkout()).collect(Collectors.toList());
      }

      @SuppressWarnings("rawtypes")
      public Optional<Mailbox> getMailbox(ActorName actorName) {
        return Optional.ofNullable(actors.get(actorName)).map(Actor::getMailbox);
      }

      public boolean isContainNonEmptyMailbox() {
        return actors.values().stream().map(Actor::getMailbox)
            .anyMatch(mailbox -> !mailbox.isEmpty());
      }

      public void start(ActorName actorName) {
        creatorsPool.getActorCreator(actorName).ifPresent(actorCreator -> {
          Actor<?> actorInstance = actorCreator.createActorInstance();
          actorInstance.beforeStart();
          actors.put(actorName, (Actor<?>) actorInstance);
          actorInstance.start();
        });
      }

      @SuppressWarnings({ "unchecked", "rawtypes" })
      public void restart(ActorName actorName) {
        creatorsPool.getActorCreator(actorName).ifPresent(actorCreator -> {
          Optional.ofNullable(actors.remove(actorName)).ifPresent(actor -> {
            Actor<?> restartedActor = actorCreator.createActorInstance();
            actor.beforeRestart();
            restartedActor.afterRestart();
            restartedActor.submitMailboxMessages((Mailbox) actor.getMailbox());
            actors.put(actorName, restartedActor);
            restartedActor.start();
          });
        });
      }

      public void stop(ActorName actorName) {
        Optional.ofNullable(actors.remove(actorName)).ifPresent(actor -> {
          creatorsPool.dispose(actorName);
          actor.afterStop();
        });
      }

      public void stopAll() {
        actors.keySet().stream().forEach(this::stop);
      }
    }
  }
}
