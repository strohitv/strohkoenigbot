package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DayFilterWithTimeString {
	private DayFilter filter;
	private String time;

	private int start;
	private int end;
}
