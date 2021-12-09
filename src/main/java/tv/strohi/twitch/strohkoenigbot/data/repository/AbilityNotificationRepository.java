package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.AbilityNotification;

import java.util.List;

@Repository
public interface AbilityNotificationRepository extends CrudRepository<AbilityNotification, Long> {
	@NotNull List<AbilityNotification> findAll();

	AbilityNotification findById(long id);

	List<AbilityNotification> findByDiscordIdOrderById(long discordId);
}
