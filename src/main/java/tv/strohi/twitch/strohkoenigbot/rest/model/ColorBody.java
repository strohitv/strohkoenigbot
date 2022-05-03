package tv.strohi.twitch.strohkoenigbot.rest.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ColorBody {
	private int[] ownTeamColor;
	private int[] otherTeamColor;
}
