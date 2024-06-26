package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.Account;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends CrudRepository<Account, Long> {
	@NotNull List<Account> findAll();

	Optional<Account> findById(long id);
	Optional<Account> findByTwitchUserId(String id);

	List<Account> findByDiscordIdOrderById(Long discordId);
	List<Account> findByEnableSplatoon3(Boolean enable);
	List<Account> findByIsMainAccount(Boolean isMainAccount);
}
