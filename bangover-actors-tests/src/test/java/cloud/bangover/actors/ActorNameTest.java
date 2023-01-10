package cloud.bangover.actors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ActorNameTest {
  @Test
  public void shouldActorNameBeCreated() {
    Assert.assertEquals("SOME_ACTOR", ActorName.wrap("SOME_ACTOR").toString());
  }
  
  @Test
  public void shouldActorNameBeDerived() {
    // Given
    ActorName baseActorName = ActorName.wrap("BASE");
    ActorName targetActorName = ActorName.wrap("DERIVED");
    // When
    ActorName derivedActorFullName = baseActorName.deriveWith(targetActorName);
    // Then
    Assert.assertEquals("BASE.DERIVED", derivedActorFullName.toString());
  }
}
