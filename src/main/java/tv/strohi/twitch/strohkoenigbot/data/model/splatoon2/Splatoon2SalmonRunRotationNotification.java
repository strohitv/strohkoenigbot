package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;

import javax.persistence.*;

@Entity(name = "splatoon_2_salmon_run_rotation_notification")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2SalmonRunRotationNotification {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@NonNull
	@ManyToOne
	@JoinColumn(foreignKey = @ForeignKey(name = "fk_splatoon_2_salmon_run_rotation_notification_account_id"))
	private Account account;

	private Integer stages;

	private Long includedWeapons;
	private Long excludedWeapons;

	private Integer days;

	private Integer includedRandom;
}
