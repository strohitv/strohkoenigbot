package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchAuth;

import java.util.List;

@Repository
public interface TwitchAuthRepository extends CrudRepository<TwitchAuth, Long> {
	@NotNull List<TwitchAuth> findAll();

	TwitchAuth findById(long id);

	List<TwitchAuth> findByIsMain(boolean isMain);
}
