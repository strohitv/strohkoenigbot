package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.AbilityType;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.GearType;

import javax.persistence.*;

@Entity(name = "splatoon_2_ability_notification")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2AbilityNotification {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private long discordId;

	private AbilityType main;

	private AbilityType favored;

	private GearType gear;

	private Integer slots;
}
