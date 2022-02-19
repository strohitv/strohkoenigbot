package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonMatch;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonMode;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums.SplatoonRule;

import java.util.List;

@Repository
public interface SplatoonMatchRepository extends CrudRepository<SplatoonMatch, Long> {
	@NotNull List<SplatoonMatch> findAll();

	SplatoonMatch findByBattleNumber(String battleNumber);
	SplatoonMatch findBySplatnetBattleNumber(Integer splatnetBattleNumber);
	@NotNull List<SplatoonMatch> findByBattleNumberIsNotNullAndSplatnetBattleNumberIsNull();

	@NotNull List<SplatoonMatch> findByStartTimeGreaterThanEqualAndMode(long startTime, SplatoonMode mode);
	SplatoonMatch findTop1ByModeAndRuleOrderByStartTimeDesc(SplatoonMode mode, SplatoonRule rule);

	@Query("select max(splatnetBattleNumber) from SplatoonMatch")
	int findMaxBattleNumber();
}
