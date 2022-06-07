package tv.strohi.twitch.strohkoenigbot.data.repository.splatoon2.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.Splatoon2Gear;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums.Splatoon2GearType;

import java.util.List;

@Repository
public interface Splatoon2GearRepository extends CrudRepository<Splatoon2Gear, Long> {
	@NotNull List<Splatoon2Gear> findAll();

	Splatoon2Gear findBySplatoonApiIdAndKind(String splatoonApiId, Splatoon2GearType kind);
}
