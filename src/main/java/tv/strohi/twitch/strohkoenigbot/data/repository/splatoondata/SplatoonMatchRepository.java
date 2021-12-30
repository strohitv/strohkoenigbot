package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.SplatoonMatch;

import java.util.List;

@Repository
public interface SplatoonMatchRepository extends CrudRepository<SplatoonMatch, Long> {
	@NotNull List<SplatoonMatch> findAll();

	SplatoonMatch findByBattleNumber(String battleNumber);
}
