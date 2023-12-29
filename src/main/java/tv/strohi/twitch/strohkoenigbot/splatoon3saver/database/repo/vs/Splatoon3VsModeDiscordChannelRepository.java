package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsModeDiscordChannel;

import java.util.List;
import java.util.Optional;

@Repository
public interface Splatoon3VsModeDiscordChannelRepository extends CrudRepository<Splatoon3VsModeDiscordChannel, Long> {
	Optional<Splatoon3VsModeDiscordChannel> findById(long id);

	List<Splatoon3VsModeDiscordChannel> findAllByModeId(long modeId);
}
