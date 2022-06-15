package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.DiscordAccount;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscordAccountRepository extends CrudRepository<DiscordAccount, Long> {
	@NotNull List<DiscordAccount> findAll();

	Optional<DiscordAccount> findById(long id);

	List<DiscordAccount> findByDiscordIdOrderById(Long discordId);
}
