package cloud.bangover.actors;

import lombok.RequiredArgsConstructor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class CorrelationKeyTest {
  
//  @DataPoint("keysCorrelation")
//  public static final Collection<Object[]> keysCorrelation() {
//    return Arrays.asList(new Object[][] {
//      {CorrelationKey.UNCORRELATED, false},
//      {CorrelationKey.wrap(""), false},
//      {CorrelationKey.wrap("12345"), false},
//    });
//  }
 
  @DataPoints("checkCorrelationsTasks")
  public static Runnable[] checkCorrelationsTasks() {
    return new Runnable[] {
        new CheckCorrelationTask(CorrelationKey.UNCORRELATED, false),
        new CheckCorrelationTask(CorrelationKey.wrap(""), false),
        new CheckCorrelationTask(CorrelationKey.wrap("12345"), true)
    };
  }
 
  @Test
  public void shouldValueBeWrapped() {
    // When
    CorrelationKey correlationKey = CorrelationKey.wrap("INSTANCE_1.12556537123.11");
    // Then
    Assert.assertEquals("INSTANCE_1.12556537123.11", correlationKey.toString());
  }
  
  @Theory
  public void shouldCheckCorrelations(@FromDataPoints("checkCorrelationsTasks") Runnable task) {
    task.run();
  }
  
  @RequiredArgsConstructor
  private static class CheckCorrelationTask implements Runnable {
    private final CorrelationKey key;
    private final boolean correlated;
    @Override
    public void run() {
      Assert.assertEquals(correlated, key.isRepresentCorrelated());
    }
  }
}
