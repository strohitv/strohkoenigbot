package tv.strohi.twitch.strohkoenigbot.data.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.Command;

import java.util.List;

@Repository
public interface CommandRepository extends CrudRepository<Command, Long> {
	List<Command> findAll(Sort sort);
}
