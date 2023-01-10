package cloud.bangover.actors;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * This class represents the actor name. It may be global name in the whole actor system or a part
 * of the another actor name.
 *
 * @author Dmitry Mikhaylenko
 *
 */
@EqualsAndHashCode
@RequiredArgsConstructor(staticName = "wrap")
public class ActorName {
  @NonNull
  private final String name;

  @Override
  public String toString() {
    return name;
  }

  /**
   * Derive the actor name from the current actor name with another actor name. The result actor
   * name will include as the current name as the another actor name.
   *
   * @param actorName The derived actor name string
   * @return The derived actor name
   */
  public ActorName deriveWith(@NonNull ActorName actorName) {
    return new ActorName(String.format("%s.%s", this, actorName));
  }
}
