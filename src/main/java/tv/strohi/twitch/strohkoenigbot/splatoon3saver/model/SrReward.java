package tv.strohi.twitch.strohkoenigbot.splatoon3saver.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class SrReward {
	private String date;
	private int money;
	private int money_ticket_small;
	private int money_ticket_big;
	private int silver_scales;
	private int gold_scales;

	@ToString.Exclude
	private String results;
}
