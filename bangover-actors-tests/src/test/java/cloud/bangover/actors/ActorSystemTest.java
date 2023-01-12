package cloud.bangover.actors;

import cloud.bangover.actors.Actors.ActorDuplicationException;
import cloud.bangover.actors.Actors.SystemConfiguration;
import cloud.bangover.actors.Actors.SystemConfigurer;
import cloud.bangover.actors.Actors.SystemInitializer;
import cloud.bangover.actors.EventLoop.Dispatcher;
import cloud.bangover.generators.Generator;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ActorSystemTest {
  private static final ActorAddress BOB_ACTOR_ADDRESS = ActorAddress.ofUrn("urn:actor:BOB");
  private static final ActorAddress ALICE_ACTOR_ADDRESS = ActorAddress.ofUrn("urn:actor:ALICE");
  private static final CorrelationKey GENERATED_KEY = CorrelationKey.wrap("CORRELATED_KEY");

  @Test
  public void shouldTellMessageToTheActorRegisteredInTheActorSystem() {
    // Given
    FixedMessagesWaitingDispatcher dispatcher =
        FixedMessagesWaitingDispatcher.singleThreadDispatcher(3);
    ActorSystem actorSystem = createActorSystem(dispatcher);
    actorSystem.start();
    StringWriter printWriter = new StringWriter();
    ActorAddress actorAddress =
        actorSystem.actorOf(PrinterActor.PRINT_ACTOR_NAME, PrinterActor.factory(printWriter));
    // When
    CorrelationKey helloWorldKey =
        actorSystem.tell(Message.createFor(actorAddress, "Hello world!"));
    CorrelationKey niceToMeetYouKey =
        actorSystem.tell(Message.createFor(BOB_ACTOR_ADDRESS, actorAddress, "Nice to meet you!"));
    CorrelationKey niceToMeetYouTooKey = actorSystem
        .tell(Message.createFor(ALICE_ACTOR_ADDRESS, actorAddress, "Nice to meet you too!"));
    dispatcher.getWaiter().await();
    actorSystem.shutdown();
    // Then
    Collection<String> printPhrases =
        Arrays.asList(printWriter.getBuffer().toString().split("\r\n"));
    Assert.assertTrue(printPhrases.contains("Printer is started!"));
    Assert.assertTrue(printPhrases
        .contains("The actor urn:actor:SYSTEM.DEAD_LETTER has sent the message: \"Hello world!\""));
    Assert.assertTrue(printPhrases
        .contains("The actor urn:actor:BOB has sent the message: \"Nice to meet you!\""));
    Assert.assertTrue(printPhrases
        .contains("The actor urn:actor:ALICE has sent the message: \"Nice to meet you too!\""));
    Assert.assertTrue(printPhrases.contains("Printer is stopped!"));
    Assert.assertNotEquals(CorrelationKey.UNCORRELATED, helloWorldKey);
    Assert.assertNotEquals(CorrelationKey.UNCORRELATED, niceToMeetYouKey);
    Assert.assertNotEquals(CorrelationKey.UNCORRELATED, niceToMeetYouTooKey);
  }

  @Test
  public void shouldHandleErrorsWithRestartHandlingStrategy() {
    // Given
    FixedMessagesWaitingDispatcher dispatcher =
        FixedMessagesWaitingDispatcher.singleThreadDispatcher(11);
    ActorSystem actorSystem = createActorSystem(dispatcher);
    actorSystem.start();
    StringWriter printWriter = new StringWriter();
    ActorAddress printerAddress =
        actorSystem.actorOf(PrinterActor.PRINT_ACTOR_NAME, PrinterActor.factory(printWriter));
    ActorAddress resumeStrategyErrorProducer =
        actorSystem.actorOf(ErrorProducerActor.RESUME_STRATEGY_ERROR_HANDLER_ADDRESS,
            ErrorProducerActor.resumeErrorHandlingStrategyActor(printerAddress));
    ActorAddress restartStrategyErrorProducer =
        actorSystem.actorOf(ErrorProducerActor.RESTART_STRATEGY_ERROR_HANDLER_ADDRESS,
            ErrorProducerActor.restartErrorHandlingStrategyActor(printerAddress));
    ActorAddress defaultStrategyErrorProducer =
        actorSystem.actorOf(ErrorProducerActor.DEFAULT_STRATEGY_ERROR_HANDLER_ADDRESS,
            ErrorProducerActor.defaultErrorHandlingStrategyActor(printerAddress));
    // When
    actorSystem.tell(Message.createFor(resumeStrategyErrorProducer, "Hello world!"));
    actorSystem.tell(
        Message.createFor(BOB_ACTOR_ADDRESS, restartStrategyErrorProducer, "Nice to meet you!"));
    actorSystem.tell(Message.createFor(ALICE_ACTOR_ADDRESS, defaultStrategyErrorProducer,
        "Nice to meet you too!"));
    dispatcher.getWaiter().await();
    actorSystem.shutdown();
    // Then
    Collection<String> phrases = Arrays.asList(printWriter.getBuffer().toString().split("\r\n"));
    Assert.assertTrue(phrases.contains("Printer is started!"));
    Assert.assertTrue(phrases.contains(
        "The actor urn:actor:RESUME_ERROR_HANDLER has sent the message: \"Actor urn:actor:RESUME_ERROR_HANDLER started!\""));
    Assert.assertTrue(phrases.contains(
        "The actor urn:actor:RESTART_ERROR_HANDLER has sent the message: \"Actor urn:actor:RESTART_ERROR_HANDLER started!\""));
    Assert.assertTrue(phrases.contains(
        "The actor urn:actor:DEFAULT_ERROR_HANDLER has sent the message: \"Actor urn:actor:DEFAULT_ERROR_HANDLER started!\""));
    Assert.assertTrue(phrases.contains(
        "The actor urn:actor:SYSTEM.DEAD_LETTER has sent the message: \"Message \"Hello world!\" from urn:actor:SYSTEM.DEAD_LETTER has been completed with error.\""));
    Assert.assertTrue(phrases.contains(
        "The actor urn:actor:BOB has sent the message: \"Message \"Nice to meet you!\" from urn:actor:BOB has been completed with error.\""));
    Assert.assertTrue(phrases.contains(
        "The actor urn:actor:ALICE has sent the message: \"Message \"Nice to meet you too!\" from urn:actor:ALICE has been completed with error.\""));
    Assert.assertTrue(phrases.contains(
        "The actor urn:actor:RESTART_ERROR_HANDLER has sent the message: \"Actor urn:actor:RESTART_ERROR_HANDLER is going to be restarted!\""));
    Assert.assertTrue(phrases.contains(
        "The actor urn:actor:RESTART_ERROR_HANDLER has sent the message: \"Actor urn:actor:RESTART_ERROR_HANDLER restarted!\""));
    Assert.assertTrue(phrases.contains("Printer is stopped!"));
  }

  @Test(expected = ActorDuplicationException.class)
  public void shouldBeProtectedOfDoubleActorRegistration() {
    // Given
    FixedMessagesWaitingDispatcher dispatcher =
        FixedMessagesWaitingDispatcher.singleThreadDispatcher(11);
    ActorSystem actorSystem = createActorSystem(dispatcher);
    try {
      actorSystem.start();
      StringWriter printWriter = new StringWriter();
      actorSystem.actorOf(PrinterActor.PRINT_ACTOR_NAME, PrinterActor.factory(printWriter));

      // When
      actorSystem.actorOf(PrinterActor.PRINT_ACTOR_NAME, PrinterActor.factory(printWriter));
    } finally {
      actorSystem.shutdown();
    }
  }

  @Test
  public void shouldBeCreatedAndDestroyedDerivedActor() {
    // Given
    FixedMessagesWaitingDispatcher dispatcher =
        FixedMessagesWaitingDispatcher.singleThreadDispatcher(3);
    ActorSystem actorSystem = createActorSystem(dispatcher);
    actorSystem.start();
    StringWriter printWriter = new StringWriter();
    ActorAddress actorAddress =
        actorSystem.actorOf(PrinterLocalUsingActor.PRINTER_LOCAL_USER_ACTOR_NAME,
            PrinterLocalUsingActor.factory(printWriter));
    // When
    actorSystem.tell(Message.createFor(actorAddress, new PrintLocal()));
    dispatcher.getWaiter().await();
    actorSystem.shutdown();

    // Then
    Collection<String> phrases = Arrays.asList(printWriter.getBuffer().toString().split("\r\n"));
    Assert.assertTrue(phrases.contains("Printer is started!"));
    Assert.assertTrue(phrases.contains("Printer is going to be restarted!"));
    Assert.assertTrue(phrases.contains("Printer is restarted!"));
    Assert.assertTrue(phrases
        .contains("The actor urn:actor:PRINTER_LOCAL_USER has sent the message: \"Hello world!\""));
    Assert.assertTrue(phrases
        .contains("The actor urn:actor:PRINTER_LOCAL_USER has sent the message: \"Good bye!\""));
    Assert.assertTrue(phrases.contains("Printer is stopped!"));
  }

  static class PrinterActor extends Actor<String> {
    public static final ActorName PRINT_ACTOR_NAME = ActorName.wrap("PRINT_ACTOR");
    private PrintWriter printWriter;

    private PrinterActor(Context context, StringWriter stringWriter) {
      super(context);
      this.printWriter = new PrintWriter(stringWriter);
    }

    @Override
    protected void beforeStart() {
      printWriter.println("Printer is started!");
      super.beforeStart();
    }

    @Override
    protected void afterStop() {
      printWriter.println("Printer is stopped!");
      super.afterStop();
    }

    @Override
    protected void beforeRestart() {
      printWriter.println("Printer is going to be restarted!");
      super.beforeRestart();
    }

    @Override
    protected void afterRestart() {
      printWriter.println("Printer is restarted!");
      super.afterRestart();
    }

    @Override
    protected void receive(Message<String> message) {
      System.out.println(message);
      printWriter.println(String.format("The actor %s has sent the message: \"%s\"",
          message.getSender(), message.getBody()));
    }

    public static final Factory<String> factory(StringWriter bufferWriter) {
      return new Factory<String>() {

        @Override
        public Actor<String> createActor(Context context) {
          return new PrinterActor(context, bufferWriter);
        }
      };
    }
  }

  static class ErrorProducerActor extends Actor<Object> {
    private static final ActorName RESUME_STRATEGY_ERROR_HANDLER_ADDRESS =
        ActorName.wrap("RESUME_ERROR_HANDLER");
    private static final ActorName RESTART_STRATEGY_ERROR_HANDLER_ADDRESS =
        ActorName.wrap("RESTART_ERROR_HANDLER");
    private static final ActorName DEFAULT_STRATEGY_ERROR_HANDLER_ADDRESS =
        ActorName.wrap("DEFAULT_ERROR_HANDLER");

    private Optional<FaultResolver<Object>> faultResolverOptional;
    private ActorAddress printerActorAddress;

    public ErrorProducerActor(ActorAddress printerActorAddress, Context context,
        Optional<FaultResolver<Object>> faultResolver) {
      super(context);
      this.faultResolverOptional = faultResolver;
      this.printerActorAddress = printerActorAddress;
    }

    @Override
    protected void beforeStart() {
      tellAboutLifecyclePhase("Actor %s started!");
      super.beforeStart();
    }

    @Override
    protected void afterStop() {
      tellAboutLifecyclePhase("Actor %s stopped!");
      super.afterStop();
    }

    @Override
    protected void beforeRestart() {
      tellAboutLifecyclePhase("Actor %s is going to be restarted!");
      super.beforeRestart();
    }

    @Override
    protected void afterRestart() {
      tellAboutLifecyclePhase("Actor %s restarted!");
      super.afterRestart();
    }

    private void tellAboutLifecyclePhase(String formatMessage) {
      tell(Message.createFor(self(), printerActorAddress, String.format(formatMessage, self())));
    }

    @Override
    protected void receive(Message<Object> message) {
      throw new RuntimeException(
          String.format("Message \"%s\" from %s has been completed with error.", message.getBody(),
              message.getSender()));
    }

    @Override
    protected FaultResolver<Object> getFaultResover() {
      FaultResolver<Object> defaultResolver = super.getFaultResover();
      return new FaultResolver<Object>() {
        @Override
        public void resolveError(LifecycleController lifecycle, Message<Object> message,
            Exception error) {
          tell(Message.createFor(message.getSender(), printerActorAddress, error.getMessage()));
          FaultResolver<Object> faultResolver = faultResolverOptional.orElse(defaultResolver);
          faultResolver.resolveError(lifecycle, message, error);
        }
      };
    }

    private static final Factory<Object> factory(ActorAddress printerActorAddress,
        Optional<FaultResolver<Object>> faultResolver) {
      return new Factory<Object>() {
        @Override
        public Actor<Object> createActor(Context context) {
          return new ErrorProducerActor(printerActorAddress, context, faultResolver);
        }
      };
    }

    public static final Factory<Object> resumeErrorHandlingStrategyActor(
        ActorAddress printerActorAddress) {
      return factory(printerActorAddress, Optional.of(new FaultResolver<Object>() {
        @Override
        public void resolveError(LifecycleController lifecycle, Message<Object> message,
            Exception error) {
          lifecycle.restart();
        }
      }));
    }

    public static final Factory<Object> restartErrorHandlingStrategyActor(
        ActorAddress printerActorAddress) {
      return factory(printerActorAddress, Optional.of(new FaultResolver<Object>() {
        @Override
        public void resolveError(LifecycleController lifecycle, Message<Object> message,
            Exception error) {
          lifecycle.restart();
        }

      }));
    }

    public static final Factory<Object> defaultErrorHandlingStrategyActor(
        ActorAddress printerActorAddress) {
      return factory(printerActorAddress, Optional.empty());
    }
  }

  static class PrinterLocalUsingActor extends Actor<PrintLocal> {
    public static final ActorName PRINTER_LOCAL_USER_ACTOR_NAME =
        ActorName.wrap("PRINTER_LOCAL_USER");

    private final Factory<String> printerFactory;
    private ActorAddress printerAddress;

    public PrinterLocalUsingActor(Context context, StringWriter bufferWriter) {
      super(context);
      this.printerFactory = PrinterActor.factory(bufferWriter);
    }

    @Override
    protected void receive(Message<PrintLocal> message) {
      this.printerAddress = actorOf(PrinterActor.PRINT_ACTOR_NAME, printerFactory);
      tell(Message.createFor(self(), printerAddress, "Hello world!"));
      restart(printerAddress);
      tell(Message.createFor(self(), printerAddress, "Good bye!"));
    }

    @Override
    protected void afterStop() {
      stop(printerAddress);
    }

    public static final Factory<PrintLocal> factory(StringWriter bufferWriter) {
      return new Factory<PrintLocal>() {

        @Override
        public Actor<PrintLocal> createActor(Context context) {
          return new PrinterLocalUsingActor(context, bufferWriter);
        }

      };
    }
  }

  static class PrintLocal {
  }

  private ActorSystem createActorSystem(Dispatcher dispatcher) {
    return Actors.create(new SystemInitializer() {
      @Override
      public SystemConfiguration initializeSystem(SystemConfigurer configurer) {
        return configurer.withDispatcher(dispatcher)
            .withCorrelationKeyGenerator(new Generator<CorrelationKey>() {
              @Override
              public CorrelationKey generateNext() {
                return GENERATED_KEY;
              }
            }).configure();
      }
    });
  }
}
