package tv.strohi.twitch.strohkoenigbot.sendou.model.out;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
public class SendouMap {
	private Long id;
	private String name;
}
