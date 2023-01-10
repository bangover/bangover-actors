package cloud.bangover.actors;

import cloud.bangover.generators.Generator;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * This class is responsible for the actors correlation keys generation.
 *
 * @author Dmitry Mikhaylenko
 *
 */
@RequiredArgsConstructor
public class CorrelationKeyGenerator implements Generator<CorrelationKey> {
  @NonNull
  private final String instanceId;

  @Override
  public final CorrelationKey generateNext() {
    return CorrelationKey.wrap(String.format("%s:%s", instanceId, UUID.randomUUID()));
  }
}
