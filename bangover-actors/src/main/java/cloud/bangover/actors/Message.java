package cloud.bangover.actors;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * This class represents the message object, wrapping data transferring between actors.
 *
 * @author Dmitry Mikhaylenko
 *
 * @param <B> The body type
 */
@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Message<B> {
  private final CorrelationKey correlationKey;
  private final ActorAddress sender;
  private final ActorAddress destination;
  private final Map<String, Object> metadata;
  private final B body;

  /**
   * Convert the message body.
   *
   * @param <T>       The message body
   * @param converter The message body converter
   * @return The result message
   */
  public <T> Message<T> map(MessageBodyConverter<B, T> converter) {
    return new Message<T>(correlationKey, sender, destination, getMetadata(),
        converter.transform(body));
  }

  /**
   * Get the metadata attribute with type cast.
   * 
   * @param <T>  The attribute type name
   * @param type The attribute type class
   * @param key  The attribute key
   * @return The attribute value wrapped by the {@link Optional}
   */
  public <T> Optional<T> getMetadataAttribute(Class<T> type, String key) {
    return getMetadataAttribute(key).map(type::cast);
  }

  /**
   * Get the metadata attribute without type cast.
   * 
   * @param key The attribute key
   * @return The attribute value wrapped by the {@link Optional}
   */
  public Optional<Object> getMetadataAttribute(String key) {
    return Optional.ofNullable(this.getMetadata().get(key));
  }

  /**
   * Create the message with appended metadata attribute
   * 
   * @param key   The metadata attribute key
   * @param value The metadata attribute value
   * @return The message with the metadata attribute
   */
  public final Message<B> withMetadata(String key, Object value) {
    Map<String, Object> derived = new HashMap<String, Object>(getMetadata());
    derived.put(key, value);
    return new Message<B>(correlationKey, sender, destination, derived, body);
  }

  /**
   * Create the message correlated by the specified correlation key.
   *
   * @param correlationKey The correlation key value
   * @return The message with correlation key
   */
  public final Message<B> correlateBy(CorrelationKey correlationKey) {
    if (isNotCorrelated()) {
      return new Message<>(correlationKey, sender, destination, getMetadata(), body);
    }
    return this;
  }

  /**
   * Create reply message to the sender.
   *
   * @param <T>       The reply message body type
   * @param replyBody The reply message body
   * @return The reply message
   */
  public <T> Message<T> replyWith(T replyBody) {
    return replyWith(sender, replyBody);
  }

  /**
   * Create reply message to the specified destination.
   *
   * @param <T>         The reply message body type
   * @param destination The destination address
   * @param replyBody   The reply message body
   * @return The reply message
   */
  public <T> Message<T> replyWith(ActorAddress destination, T replyBody) {
    return new Message<T>(correlationKey, this.destination, destination, getMetadata(), replyBody);
  }

  /**
   * Change the message sender.
   *
   * @param sender The message sender
   * @return The message with changed sender
   */
  public Message<B> withSender(ActorAddress sender) {
    return new Message<>(correlationKey, sender, this.destination, getMetadata(), this.body);
  }

  /**
   * Change the message destination.
   *
   * @param destination The message destination address
   * @return The message with changed destination
   */
  public Message<B> withDestination(ActorAddress destination) {
    return new Message<B>(correlationKey, this.sender, destination, getMetadata(), this.body);
  }

  /**
   * Create message to the destination, from the anonymous sender.
   *
   * @param <B>         The message body type name
   * @param destination The message destination address
   * @param messageBody The message body
   * @return The created message
   */
  public static final <B> Message<B> createFor(ActorAddress destination, B messageBody) {
    return createFor(ActorAddress.UNKNOWN_ADDRESS, destination, messageBody);
  }

  /**
   * Create message to the destination, from the specified sender.
   *
   * @param <B>         The message body type name
   * @param sender      The message sender address
   * @param destination The message destination address
   * @param messageBody The message body
   * @return The created message
   */
  public static final <B> Message<B> createFor(ActorAddress sender, ActorAddress destination,
      B messageBody) {
    return new Message<>(CorrelationKey.UNCORRELATED, sender, destination, new HashMap<>(),
        messageBody);
  }

  /**
   * Match that message body has specified type.
   *
   * @param <T>              The expected body type name
   * @param expectedBodyType The expected body type class
   * @param matchedReceiver  The message handle function receiver for case when the message body is
   *                         matched to specified typee
   * @throws Exception The thrown error
   */
  public <T> void whenIsMatchedTo(Class<T> expectedBodyType,
      MessageHandleFunction<T> matchedReceiver) throws Exception {
    whenIsMatchedTo(expectedBodyType, matchedReceiver, body -> {
    });
  }

  /**
   * Match that message body has specified type.
   *
   * @param <T>               The expected body type name
   * @param expectedBodyType  The expected body type class
   * @param matchedReceiver   The message handle function receiver for case when the message body is
   *                          matched to specified type
   * @param unmatchedReceiver The message handle function receiver for case when the message body is
   *                          unmatched to specified type
   * @throws Exception The thrown error
   */
  public <T> void whenIsMatchedTo(Class<T> expectedBodyType,
      MessageHandleFunction<T> matchedReceiver, MessageHandleFunction<B> unmatchedReceiver)
      throws Exception {
    if (expectedBodyType.isInstance(body)) {
      matchedReceiver.receive(expectedBodyType.cast(body));
    } else {
      unmatchedReceiver.receive(body);
    }
  }

  /**
   * Match body by a predicate.
   *
   * @param predicate       The predicate
   * @param matchedReceiver The message handle function receiver for case when the message body is
   *                        matched by the specified predicate
   * @throws Exception The thrown error
   */
  public void whenIsMatchedTo(Predicate<B> predicate, MessageHandleFunction<B> matchedReceiver)
      throws Exception {
    whenIsMatchedTo(predicate, matchedReceiver, body -> {
    });
  }

  /**
   * Match body by a predicate.
   *
   * @param predicate         The predicate
   * @param matchedReceiver   The message handle function receiver for case when the message body is
   *                          matched by the specified predicate
   * @param unmatchedReceiver The message handle function receiver for case when the message body is
   *                          unmatched by the specified predicate
   * @throws Exception The thrown error
   */
  public void whenIsMatchedTo(Predicate<B> predicate, MessageHandleFunction<B> matchedReceiver,
      MessageHandleFunction<B> unmatchedReceiver) throws Exception {
    if (predicate.test(body)) {
      matchedReceiver.receive(body);
    } else {
      unmatchedReceiver.receive(body);
    }
  }

  private boolean isNotCorrelated() {
    return !correlationKey.isRepresentCorrelated();
  }

  /**
   * This interface declares the contract of the message body transformer.
   *
   * @author Dmitry Mikhaylenko
   *
   * @param <B> The current body type name
   * @param <T> The transformed body type name
   */
  public interface MessageBodyConverter<B, T> {
    /**
     * Transform the message body type.
     *
     * @param currentBody The current body
     * @return The transformed body
     */
    public T transform(B currentBody);
  }

  /**
   * This interface declares the contract of the message handling on matched condition.
   *
   * @author Dmitry Mikhaylenko
   *
   * @param <B> The message body type name
   */
  public interface MessageHandleFunction<B> {
    /**
     * Consume the message body.
     *
     * @param body The message body
     */
    public void receive(B body) throws Exception;
  }
}
