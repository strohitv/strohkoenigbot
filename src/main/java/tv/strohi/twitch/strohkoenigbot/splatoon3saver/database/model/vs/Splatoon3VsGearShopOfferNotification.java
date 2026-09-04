package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_gear_shop_offer_notification")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Splatoon3VsGearShopOfferNotification {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String gearName;

	// ---

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "account_id")
	@EqualsAndHashCode.Exclude
	private Account account;
}
