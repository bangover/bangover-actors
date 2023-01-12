package cloud.bangover.actors;

import cloud.bangover.actors.Message.MessageBodyConverter;
import java.util.function.Predicate;
import lombok.EqualsAndHashCode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MessageTest {
  private static final ActorAddress SENDER_ADDRESS = ActorAddress.ofUrn("urn:actor:SENDER");
  private static final ActorAddress RECEIVER_ADDRESS = ActorAddress.ofUrn("urn:actor:RECEIVER");
  private static final ActorAddress ANOTHER_ADDRESS = ActorAddress.ofUrn("urn:actor:ANOTHER");
  private static final CorrelationKey CORRELATION_KEY = CorrelationKey.wrap("123456");
  private static final CorrelationKey ANOTHER_CORRELATION_KEY = CorrelationKey.wrap("123456");

  @Test
  public void shouldCreateMessageFromUnknownSender() {
    Message<BodyObject> message = Message.createFor(RECEIVER_ADDRESS, new BodyObject());
    Assert.assertEquals(CorrelationKey.UNCORRELATED, message.getCorrelationKey());
    Assert.assertEquals(ActorAddress.UNKNOWN_ADDRESS, message.getSender());
    Assert.assertEquals(RECEIVER_ADDRESS, message.getDestination());
    Assert.assertEquals(new BodyObject(), message.getBody());
  }

  @Test
  public void shouldCreateMessageFromSpecifiedSender() {
    Message<BodyObject> message = Message.createFor(SENDER_ADDRESS, RECEIVER_ADDRESS, new BodyObject());
    Assert.assertEquals(CorrelationKey.UNCORRELATED, message.getCorrelationKey());
    Assert.assertEquals(SENDER_ADDRESS, message.getSender());
    Assert.assertEquals(RECEIVER_ADDRESS, message.getDestination());
    Assert.assertEquals(new BodyObject(), message.getBody());
  }

  @Test
  public void shouldCreateMessageWithSpecifiedCorrelationKeyFromUncorrelatedMessage() {
    // Given
    Message<BodyObject> message = Message
        .createFor(SENDER_ADDRESS, RECEIVER_ADDRESS, new BodyObject());
    // When
    message = message.correlateBy(ANOTHER_CORRELATION_KEY);
    // Then
    Assert.assertEquals(ANOTHER_CORRELATION_KEY, message.getCorrelationKey());
    Assert.assertEquals(SENDER_ADDRESS, message.getSender());
    Assert.assertEquals(RECEIVER_ADDRESS, message.getDestination());
    Assert.assertEquals(new BodyObject(), message.getBody());
  }

  @Test
  public void shouldCreateMessageWithSpecifiedCorrelationKeyFromCorrelatedMessage() {
    // Given
    Message<BodyObject> message = Message
        .createFor(SENDER_ADDRESS, RECEIVER_ADDRESS, new BodyObject())
        .correlateBy(CORRELATION_KEY);
    // When
    message = message.correlateBy(ANOTHER_CORRELATION_KEY);
    // Then
    Assert.assertEquals(CORRELATION_KEY, message.getCorrelationKey());
    Assert.assertEquals(SENDER_ADDRESS, message.getSender());
    Assert.assertEquals(RECEIVER_ADDRESS, message.getDestination());
    Assert.assertEquals(new BodyObject(), message.getBody());
  }

  @Test
  public void shouldReplaceMessageSender() {
    // Given
    Message<BodyObject> sourceMessage = Message
        .createFor(SENDER_ADDRESS, RECEIVER_ADDRESS, new BodyObject())
        .correlateBy(CORRELATION_KEY);
    // When
    Message<BodyObject> resultMessage = sourceMessage.withSender(ANOTHER_ADDRESS);
    // Then
    Assert.assertNotSame(sourceMessage, resultMessage);
    Assert.assertEquals(ANOTHER_ADDRESS, resultMessage.getSender());
    Assert.assertEquals(sourceMessage.getDestination(), resultMessage.getDestination());
    Assert.assertEquals(sourceMessage.getBody(), resultMessage.getBody());
    Assert.assertEquals(sourceMessage.getCorrelationKey(), resultMessage.getCorrelationKey());
  }

  @Test
  public void shouldReplaceMessageDestination() {
    // Given
    Message<BodyObject> sourceMessage = Message
        .createFor(SENDER_ADDRESS, RECEIVER_ADDRESS, new BodyObject())
        .correlateBy(CORRELATION_KEY);
    // When
    Message<BodyObject> resultMessage = sourceMessage.withDestination(ANOTHER_ADDRESS);
    // Then
    Assert.assertNotSame(sourceMessage, resultMessage);
    Assert.assertEquals(resultMessage.getSender(), resultMessage.getSender());
    Assert.assertEquals(ANOTHER_ADDRESS, resultMessage.getDestination());
    Assert.assertEquals(sourceMessage.getBody(), resultMessage.getBody());
    Assert.assertEquals(sourceMessage.getCorrelationKey(), resultMessage.getCorrelationKey());
  }

  @Test
  public void shouldReplaceMessageBody() {
    // Given
    Message<BodyObject> sourceMessage = Message.createFor(SENDER_ADDRESS, RECEIVER_ADDRESS, new BodyObject());
    // When
    Message<String> resultMessage = sourceMessage.map(new MessageBodyConverter<MessageTest.BodyObject, String>() {
      @Override
      public String transform(BodyObject currentBody) {
        return currentBody.toString();
      }      
    });
    // Then
    Assert.assertNotSame(sourceMessage, resultMessage);
    Assert.assertEquals(sourceMessage.getSender(), resultMessage.getSender());
    Assert.assertEquals(sourceMessage.getDestination(), resultMessage.getDestination());
    Assert.assertTrue(resultMessage.getBody() instanceof String);
    Assert.assertEquals("BODY_OBJECT", resultMessage.getBody());
    Assert.assertEquals(sourceMessage.getCorrelationKey(), resultMessage.getCorrelationKey());
  }

  @Test
  public void shouldReplyMessageToCurrentSender() {
    // Given
    Message<BodyObject> sourceMessage = Message.createFor(SENDER_ADDRESS, RECEIVER_ADDRESS, new BodyObject());
    // When
    Message<String> replyMessage = sourceMessage.replyWith("OK");
    // Then
    Assert.assertEquals(sourceMessage.getDestination(), replyMessage.getSender());
    Assert.assertEquals(sourceMessage.getSender(), replyMessage.getDestination());
    Assert.assertEquals(sourceMessage.getCorrelationKey(), replyMessage.getCorrelationKey());
    Assert.assertEquals("OK", replyMessage.getBody());
  }

  @Test
  public void shouldReplyMessageToSpecifiedDestination() {
    // Given
    Message<BodyObject> sourceMessage = Message.createFor(SENDER_ADDRESS, RECEIVER_ADDRESS, new BodyObject());
    // When
    Message<String> replyMessage = sourceMessage.replyWith(ANOTHER_ADDRESS, "OK");
    // Then
    Assert.assertEquals(sourceMessage.getDestination(), replyMessage.getSender());
    Assert.assertEquals(sourceMessage.getCorrelationKey(), replyMessage.getCorrelationKey());
    Assert.assertEquals(ANOTHER_ADDRESS, replyMessage.getDestination());
    Assert.assertEquals("OK", replyMessage.getBody());
  }

  @Test
  public void shouldMatchMessageByTypeSuccessfullyWithoutOtherwiseBehavior() throws Throwable {
    // Given
    MockMessageHandleFunction<String> handleFunction = new MockMessageHandleFunction<String>();
    Message<Object> message = Message.createFor(RECEIVER_ADDRESS, "Hello world!");
    // When
    message.whenIsMatchedTo(String.class, handleFunction);
    // Then
    Assert.assertTrue(handleFunction.getHistory().hasEntry(0, "Hello world!"));
  }

  @Test
  public void shouldMatchMessageByTypeNonSuccessfullyWithoutOtherwiseBehavior() throws Throwable {
    // Given
    MockMessageHandleFunction<String> handleFunction = new MockMessageHandleFunction<String>();
    Message<Object> message = Message.createFor(RECEIVER_ADDRESS, new Object());
    // When
    message.whenIsMatchedTo(String.class, handleFunction);
    // Then
    Assert.assertFalse(handleFunction.getHistory().hasEntries());
  }

  @Test
  public void shouldMatchMessageByTypeSuccessfullyWithOtherwiseBehavior() throws Throwable {
    // Given
    MockMessageHandleFunction<String> handleFunction = new MockMessageHandleFunction<String>();
    MockMessageHandleFunction<Object> otherwiseFunction = new MockMessageHandleFunction<Object>();
    Message<Object> message = Message.createFor(RECEIVER_ADDRESS, "Hello world!");
    // When
    message.whenIsMatchedTo(String.class, handleFunction, otherwiseFunction);
    // Then
    Assert.assertTrue(handleFunction.getHistory().hasEntry(0, "Hello world!"));
    Assert.assertFalse(otherwiseFunction.getHistory().hasEntries());
  }

  @Test
  public void shouldMatchMessageByTypeNonSuccessfullyWithOtherwiseBehavior() throws Throwable {
    // Given
    Object body = new Object();
    MockMessageHandleFunction<String> handleFunction = new MockMessageHandleFunction<String>();
    MockMessageHandleFunction<Object> otherwiseFunction = new MockMessageHandleFunction<Object>();
    Message<Object> message = Message.createFor(RECEIVER_ADDRESS, body);
    // When
    message.whenIsMatchedTo(String.class, handleFunction, otherwiseFunction);
    // Then
    Assert.assertFalse(handleFunction.getHistory().hasEntries());
    Assert.assertTrue(otherwiseFunction.getHistory().hasEntry(0, body));
  }

  @Test
  public void shouldMatchMessageByPredicateSuccessfullyWithoutOtherwiseBehavior() throws Throwable {
    // Given
    MockMessageHandleFunction<Long> handleFunction = new MockMessageHandleFunction<Long>();
    Message<Long> message = Message.createFor(RECEIVER_ADDRESS, 1000L);
    // When
    message.whenIsMatchedTo(new EvenPredicate(), handleFunction);
    // Then
    Assert.assertTrue(handleFunction.getHistory().hasEntry(0, 1000L));
  }

  @Test
  public void shouldMatchMessageByPredicateNonSuccessfullyWithoutOtherwiseBehavior() throws Throwable {
    // Given
    MockMessageHandleFunction<Long> handleFunction = new MockMessageHandleFunction<Long>();
    Message<Long> message = Message.createFor(RECEIVER_ADDRESS, 1001L);
    // When
    message.whenIsMatchedTo(new EvenPredicate(), handleFunction);
    // Then
    Assert.assertFalse(handleFunction.getHistory().hasEntries());
  }

  @Test
  public void shouldMatchMessageByPredicateSuccessfullyWithOtherwiseBehavior() throws Throwable {
    // Given
    MockMessageHandleFunction<Long> handleFunction = new MockMessageHandleFunction<Long>();
    MockMessageHandleFunction<Long> otherwiseFunction = new MockMessageHandleFunction<Long>();
    Message<Long> message = Message.createFor(RECEIVER_ADDRESS, 1000L);
    // When
    message.whenIsMatchedTo(new EvenPredicate(), handleFunction, otherwiseFunction);
    // Then
    Assert.assertTrue(handleFunction.getHistory().hasEntry(0, 1000L));    
    Assert.assertFalse(otherwiseFunction.getHistory().hasEntries());
  }

  @Test
  public void shouldMatchMessageByPredicateNonSuccessfullyWithOtherwiseBehavior() throws Throwable {
    // Given
    MockMessageHandleFunction<Long> handleFunction = new MockMessageHandleFunction<Long>();
    MockMessageHandleFunction<Long> otherwiseFunction = new MockMessageHandleFunction<Long>();
    Message<Long> message = Message.createFor(RECEIVER_ADDRESS, 1001L);
    // When
    message.whenIsMatchedTo(new EvenPredicate(), handleFunction, otherwiseFunction);
    // Then
    Assert.assertFalse(handleFunction.getHistory().hasEntries());
    Assert.assertTrue(otherwiseFunction.getHistory().hasEntry(0, 1001L));
  }

  private static class EvenPredicate implements Predicate<Long> {
    @Override
    public boolean test(Long value) {
      return value % 2 == 0;
    }
  }
  
  @EqualsAndHashCode
  private static class BodyObject {
    @Override
    public String toString() {
      return "BODY_OBJECT";
    }
  }
}
