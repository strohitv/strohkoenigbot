package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.Splatoon3ModeDiscordChannel;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3ModeDiscordChannelRepository extends CrudRepository<Splatoon3ModeDiscordChannel, Long> {
	Optional<Splatoon3ModeDiscordChannel> findById(long id);

	List<Splatoon3ModeDiscordChannel> findAllByModeId(long modeId);
}
