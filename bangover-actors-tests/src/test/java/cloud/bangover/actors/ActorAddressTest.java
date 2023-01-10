package cloud.bangover.actors;

import cloud.bangover.actors.ActorAddress.WrongActorAddressFormatException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ActorAddressTest {
  @Test
  public void shouldCreateActorAddressFromActorName() {
    ActorName actorName = ActorName.wrap("SOME.ACTOR");
    ActorAddress actorAddress = ActorAddress.ofName(actorName);
    Assert.assertEquals("urn:actor:SOME.ACTOR", actorAddress.toString());
  }
  
  @Test
  public void shouldCreateActorAddressFromUrnAddress() {
    ActorAddress actorAddress = ActorAddress.ofUrn("urn:actor:SOME_ACTOR");
    Assert.assertEquals("urn:actor:SOME_ACTOR", actorAddress.toString());
  }
  
  
  @Test(expected = WrongActorAddressFormatException.class)
  public void shouldNotActorBeCreatedFromBadFormattedUrn() {
    ActorAddress.ofUrn("WRONG ADDRESS");
  }
  
  @Test
  public void shouldExtractActorName() {
    ActorAddress actorAddress = ActorAddress.ofUrn("urn:actor:SOME_ACTOR");
    Assert.assertEquals(ActorName.wrap("SOME_ACTOR"), actorAddress.getActorName());
  }
  
  @Test
  public void shouldUnknownAddressBeReserved() {
    Assert.assertEquals("urn:actor:SYSTEM.DEAD_LETTER", ActorAddress.UNKNOWN_ADDRESS.toString());
  }
}
