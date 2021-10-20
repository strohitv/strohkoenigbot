package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.DiscordAccount;

import java.util.List;

@Repository
public interface DiscordAccountRepository extends CrudRepository<DiscordAccount, Long> {
	@NotNull List<DiscordAccount> findAll();

	DiscordAccount findById(long id);

	List<DiscordAccount> findByTwitchUserId(String twitchUserId);
	List<DiscordAccount> findByDiscordId(Long discordId);
}
