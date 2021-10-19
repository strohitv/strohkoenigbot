package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchMessageSentHistory;

import java.time.Instant;
import java.util.List;

@Repository
public interface TwitchMessageSentHistoryRepository extends CrudRepository<TwitchMessageSentHistory, Long> {
	@NotNull List<TwitchMessageSentHistory> findAll();

	TwitchMessageSentHistory findById(long id);

	List<TwitchMessageSentHistory> findByUserId(String user);
	List<TwitchMessageSentHistory> findBySentAtBefore(Instant time);
	List<TwitchMessageSentHistory> findBySentAtIsAfter(Instant time);
}
