package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Match;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Mode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2Rule;

import java.util.List;

@Repository
public interface Splatoon2MatchRepository extends CrudRepository<Splatoon2Match, Long> {
	@NotNull List<Splatoon2Match> findAllByAccountId(long accountId);

	Splatoon2Match findByAccountIdAndBattleNumber(long accountId, String battleNumber);
	Splatoon2Match findByAccountIdAndSplatnetBattleNumber(long accountId, Integer splatnetBattleNumber);

	@NotNull List<Splatoon2Match> findByAccountIdAndStartTimeGreaterThanEqualAndMode(long accountId, long startTime, Splatoon2Mode mode);
	Splatoon2Match findTop1ByAccountIdAndModeAndRuleOrderByStartTimeDesc(long accountId, Splatoon2Mode mode, Splatoon2Rule rule);

	@NotNull List<Splatoon2Match> findByAccountIdAndStartTimeGreaterThanEqual(long accountId, long startTime);
	@NotNull List<Splatoon2Match> findByAccountIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(long accountId, long startTime, long endTime);

	@Query("select max(splatnetBattleNumber) from splatoon_2_match where accountId = :accountId")
	int findMaxBattleNumber(long accountId);
}
