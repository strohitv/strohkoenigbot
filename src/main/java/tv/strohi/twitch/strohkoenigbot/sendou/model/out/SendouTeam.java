package tv.strohi.twitch.strohkoenigbot.sendou.model.out;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class SendouTeam {
	private long id;
	private String name;
	private String logoUrl;
	private List<SendouPlayer> players;
}
