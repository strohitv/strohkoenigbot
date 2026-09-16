package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.QueuedMessage;

import java.util.List;

@Repository
public interface QueuedMessageRepository extends CrudRepository<QueuedMessage, Long> {
	@NotNull List<QueuedMessage> findAll();

	QueuedMessage findById(long id);
}
