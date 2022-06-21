package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.TwitchSoAccount;

import java.util.List;

@Repository
public interface TwitchSoAccountRepository extends CrudRepository<TwitchSoAccount, Long> {
	@NotNull List<TwitchSoAccount> findAllByAccountId(long accountId);

	TwitchSoAccount findById(long id);
	TwitchSoAccount findByAccountIdAndUsername(long accountId, String username);
}
