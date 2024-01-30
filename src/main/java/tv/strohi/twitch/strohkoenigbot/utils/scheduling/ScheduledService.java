package tv.strohi.twitch.strohkoenigbot.utils.scheduling;


import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.ScheduleRequest;

import java.util.List;

public interface ScheduledService {
	List<ScheduleRequest> createScheduleRequests();
}
