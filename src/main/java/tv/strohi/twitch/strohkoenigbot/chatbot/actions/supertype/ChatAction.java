package tv.strohi.twitch.strohkoenigbot.chatbot.actions.supertype;

import org.springframework.stereotype.Component;

@Component
public abstract class ChatAction implements IChatAction {
    protected TriggerReason reason;

    public TriggerReason getReason() {
        return reason;
    }

    public abstract void run();
}
