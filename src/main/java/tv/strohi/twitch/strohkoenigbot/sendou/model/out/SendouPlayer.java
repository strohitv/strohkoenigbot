package tv.strohi.twitch.strohkoenigbot.sendou.model.out;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class SendouPlayer {
	private long id;
	private String name;
	private boolean isMyself;
}
