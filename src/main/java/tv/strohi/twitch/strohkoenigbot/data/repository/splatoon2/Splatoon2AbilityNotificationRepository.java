package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.Splatoon2AbilityNotification;

import java.util.List;

@Repository
public interface Splatoon2AbilityNotificationRepository extends CrudRepository<Splatoon2AbilityNotification, Long> {
	@NotNull List<Splatoon2AbilityNotification> findAll();

	Splatoon2AbilityNotification findById(long id);

	List<Splatoon2AbilityNotification> findByDiscordIdOrderById(long discordId);
}
