package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ModeFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.RuleFilter;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.Splatoon2RotationNotification;

import java.util.List;

@Repository
public interface Splatoon2RotationNotificationRepository extends CrudRepository<Splatoon2RotationNotification, Long> {
	@NotNull List<Splatoon2RotationNotification> findAll();

	Splatoon2RotationNotification findById(long id);

	List<Splatoon2RotationNotification> findByModeAndRule(ModeFilter mode, RuleFilter rule);
	List<Splatoon2RotationNotification> findByModeAndAccountIdOrderById(ModeFilter mode, long accountId);
	Splatoon2RotationNotification findByIdAndAccountIdOrderById(long id, long accountId);
}
