package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.ModeFilter;
import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.RuleFilter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRotationNotification;

import java.util.List;

@Repository
public interface Splatoon3RotationNotificationRepository extends CrudRepository<Splatoon3VsRotationNotification, Long> {
	@NotNull List<Splatoon3VsRotationNotification> findAll();

	Splatoon3VsRotationNotification findById(long id);

	List<Splatoon3VsRotationNotification> findByModeAndRule(ModeFilter mode, RuleFilter rule);
	List<Splatoon3VsRotationNotification> findByModeAndAccountIdOrderById(ModeFilter mode, long accountId);
	Splatoon3VsRotationNotification findByIdAndAccountIdOrderById(long id, long accountId);
}
