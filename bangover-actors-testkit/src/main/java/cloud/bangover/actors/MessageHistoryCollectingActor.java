package cloud.bangover.actors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This actor is responsible for keeping of the messages history to use them later for different
 * checks in tests.
 *
 * @author Dmitry Mikhaylenko
 *
 */
public class MessageHistoryCollectingActor extends Actor<Object> {
  private final MessagesCollector messageStore;

  private MessageHistoryCollectingActor(Context context, MessagesCollector messagesCollector) {
    super(context);
    this.messageStore = messagesCollector;
  }

  @Override
  protected void receive(Message<Object> message) {
    messageStore.putMessage(message);
  }

  public static Factory<Object> factory(MessagesCollector messagesCollector) {
    return context -> new MessageHistoryCollectingActor(context, messagesCollector);
  }

  /**
   * This class is the messages collector, storing all input messages.
   *
   * @author Dmitry Mikhaylenko
   *
   */
  public class MessagesCollector {
    private List<Message<Object>> history = new ArrayList<>();

    /**
     * Get the messages history.
     *
     * @return The messages history
     */
    public Collection<Message<Object>> getHistory() {
      return history;
    }

    /**
     * Get the message from history.
     *
     * @param index The message position index in the history
     * @return The message object
     */
    public Message<Object> getMessage(int index) {
      return history.get(index);
    }

    /**
     * Put message to the history.
     *
     * @param message The message
     */
    public void putMessage(Message<Object> message) {
      this.history.add(message);
    }
  }
}
