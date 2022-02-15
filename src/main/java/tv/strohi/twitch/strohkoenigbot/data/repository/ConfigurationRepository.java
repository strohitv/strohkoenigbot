package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;

import java.util.List;

@Repository
public interface ConfigurationRepository extends CrudRepository<Configuration, Long> {
	String streamPrefix = "streamPrefix";
	String gameSceneName = "GameScene";
	String resultsSceneName = "ResultsScene";

	@NotNull List<Configuration> findAll();

	Configuration findById(long id);

	List<Configuration> findByConfigName(String configName);
}
