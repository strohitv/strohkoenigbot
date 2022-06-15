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
	@NotNull List<Splatoon2Match> findAll();

	Splatoon2Match findByBattleNumber(String battleNumber);
	Splatoon2Match findBySplatnetBattleNumber(Integer splatnetBattleNumber);

	@NotNull List<Splatoon2Match> findByStartTimeGreaterThanEqualAndMode(long startTime, Splatoon2Mode mode);
	Splatoon2Match findTop1ByModeAndRuleOrderByStartTimeDesc(Splatoon2Mode mode, Splatoon2Rule rule);

	@NotNull List<Splatoon2Match> findByStartTimeGreaterThanEqual(long startTime);
	@NotNull List<Splatoon2Match> findByStartTimeGreaterThanEqualAndEndTimeLessThanEqual(long startTime, long endTime);

	@Query("select max(splatnetBattleNumber) from splatoon_2_match")
	int findMaxBattleNumber();
}
