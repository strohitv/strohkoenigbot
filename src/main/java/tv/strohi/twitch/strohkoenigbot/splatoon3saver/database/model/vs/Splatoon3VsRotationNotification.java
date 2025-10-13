package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ModeFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.RuleFilter;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_rotation_notification")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon3VsRotationNotification {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@NonNull
	@ManyToOne
	@JoinColumn(foreignKey = @ForeignKey(name = "fk_splatoon_3_vs_rotation_notification_account_id"))
	private Account account;

	private ModeFilter mode;
	private RuleFilter rule;

	private int includedStages;
	private int excludedStages;

	private String zoneId;

	private boolean notifyMonday;
	private int startTimeMonday;
	private int endTimeMonday;

	private boolean notifyTuesday;
	private int startTimeTuesday;
	private int endTimeTuesday;

	private boolean notifyWednesday;
	private int startTimeWednesday;
	private int endTimeWednesday;

	private boolean notifyThursday;
	private int startTimeThursday;
	private int endTimeThursday;

	private boolean notifyFriday;
	private int startTimeFriday;
	private int endTimeFriday;

	private boolean notifySaturday;
	private int startTimeSaturday;
	private int endTimeSaturday;

	private boolean notifySunday;
	private int startTimeSunday;
	private int endTimeSunday;
}
