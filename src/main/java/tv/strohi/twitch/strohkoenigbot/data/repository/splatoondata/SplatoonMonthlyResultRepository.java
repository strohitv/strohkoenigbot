package tv.strohi.twitch.strohkoenigbot.data.repository.splatoondata;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.SplatoonMonthlyResult;

import java.util.List;

@Repository
public interface SplatoonMonthlyResultRepository extends CrudRepository<SplatoonMonthlyResult, Long> {
	@NotNull List<SplatoonMonthlyResult> findAll();

	SplatoonMonthlyResult findByPeriodYearAndPeriodMonth(int year, int month);
}
