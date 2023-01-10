package cloud.bangover.actors;

import cloud.bangover.generators.Generator;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CorrelationKeyGeneratorTest {
  private static final String GLOBAL_INSTANCE_ID = "GLOBAL";
  private static final String KEY_PATTERN = "GLOBAL:[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}";
  
  @Test
  public void shouldGenerateKey() {
    // When
    Generator<CorrelationKey> generator = new CorrelationKeyGenerator(GLOBAL_INSTANCE_ID);

    // Then
    Assert.assertTrue(generator.generateNext().toString().matches(KEY_PATTERN));
  }
}
