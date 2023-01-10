package cloud.bangover.actors;

import cloud.bangover.FormatChecker;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * This class represents the address of actor in the actor system in which the actor is registered.
 *
 * @author Dmitry Mikhaylenko
 *
 */
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ActorAddress {
  public static final ActorAddress UNKNOWN_ADDRESS =
      new ActorAddress("urn:actor:SYSTEM.DEAD_LETTER");
  private static final String ACTOR_URN_PATTERN = "urn:actor:(.+)";
  private static final FormatChecker ACTOR_URN_FORMAT_CHECKER =
      FormatChecker.createFor(ACTOR_URN_PATTERN, WrongActorAddressFormatException::new);

  private final String addressUrn;

  /**
   * Create the actor address of the specified URN address.
   *
   * @param urn The actor URN address string
   * @return The actor address
   */
  public static final ActorAddress ofUrn(@NonNull String urn) {
    ACTOR_URN_FORMAT_CHECKER.checkThatValueIsWellFormatted(urn);
    return new ActorAddress(urn);
  }

  /**
   * Create the actor of the specified actor name.
   *
   * @param actorName The actor name
   * @return The actor address
   */
  static final ActorAddress ofName(@NonNull ActorName actorName) {
    return ofUrn(String.format("urn:actor:%s", actorName));
  }

  /**
   * Get the actor name.
   *
   * @return The actor name
   */
  public ActorName getActorName() {
    return ActorName.wrap(getUrnMatcher().group(1));
  }

  @Override
  public String toString() {
    return addressUrn;
  }

  private Matcher getUrnMatcher() {
    Pattern urnPattern = Pattern.compile(ACTOR_URN_PATTERN);
    Matcher urnMatcher = urnPattern.matcher(addressUrn);
    urnMatcher.find();
    return urnMatcher;
  }

  /**
   * This exception is happened if an actor URN address is badly formatted.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public static class WrongActorAddressFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WrongActorAddressFormatException(String urn) {
      super(String.format("The actor URN \"%s\" isn't matched to the \"%s\" pattern", urn,
          ACTOR_URN_PATTERN));
    }
  }
}
