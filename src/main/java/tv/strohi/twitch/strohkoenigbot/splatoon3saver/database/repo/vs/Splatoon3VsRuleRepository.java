package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsRule;

import java.util.Optional;

@Repository
public interface Splatoon3VsRuleRepository extends CrudRepository<Splatoon3VsRule, Long> {
	Optional<Splatoon3VsRule> findById(long id);
	Optional<Splatoon3VsRule> findByApiId(String apiId);
	Optional<Splatoon3VsRule> findByApiRule(String apiRule);
}
