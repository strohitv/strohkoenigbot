package tv.strohi.twitch.strohkoenigbot.utils.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.Schedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchedulingService {
	private final static int MAX_ERRORS_SINGLE = 3;
	private final static int MAX_ERRORS_REPEATED = 5;

	private final ConfigurationRepository configurationRepository;
	private final DiscordBot discordBot;

	private final List<Schedule> schedules = new ArrayList<>();
	private final List<Schedule> singleRunSchedules = new ArrayList<>();

	@Scheduled(fixedDelay = 5000)
	private void run() {
		LocalDateTime now = LocalDateTime.now();

		for (int i = 0; i < singleRunSchedules.size(); i++) {
			Schedule schedule = singleRunSchedules.get(i);

			if (schedule.shouldRun(now)) {
				try {
					schedule.getRunnable().run();
					singleRunSchedules.remove(i);
					i--;
				} catch (Exception ignored){
					schedule.increaseErrorCount();

					if (schedule.isFailed(MAX_ERRORS_SINGLE)) {
						discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"),
								String.format("**ERROR**: Single Runnable failed **%d** times and got removed from Scheduler!! Schedule: `%s`", MAX_ERRORS_SINGLE, schedule));
						singleRunSchedules.remove(i);
						i--;
					}
				}
			}
		}

		for (int i = 0; i < schedules.size(); i++) {
			Schedule schedule = schedules.get(i);

			if (schedule.shouldRun(now)) {
				try {
					schedule.getRunnable().run();
				} catch (Exception ignored){
					schedule.increaseErrorCount();

					if (schedule.isFailed(MAX_ERRORS_REPEATED)) {
						discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"),
								String.format("**ERROR**: Repeated Runnable failed **%d** times and got removed from Scheduler!! Schedule: `%s`", MAX_ERRORS_REPEATED, schedule));
						schedules.remove(i);
						i--;
					}
				}
			}
		}
	}

	public void registerOnce(int ticks, Runnable runnable) {
		singleRunSchedules.add(new TickSchedule(ticks, runnable));
	}

	public void registerOnce(String cron, Runnable runnable) {
		singleRunSchedules.add(new CronSchedule(cron, runnable));
	}

	public void register(String configName, String defaultValue, Runnable runnable) {
		Configuration config = configurationRepository.findByConfigName(configName).stream().findFirst().orElse(null);

		if (config == null) {
			config = configurationRepository.save(new Configuration(0, configName, defaultValue));
			discordBot.sendPrivateMessage(discordBot.loadUserIdFromDiscordServer("strohkoenig#8058"),
					String.format("Added new Schedule: id = **%d**, name = **%s**, value = **%s**", config.getId(), configName, defaultValue));
		}

		schedules.add(createFromSettings(config.getConfigValue(), runnable));
	}

	private static Schedule createFromSettings(String setting, Runnable runnable) {
		if (setting.toLowerCase().startsWith("cron: ")) {
			return create(setting.substring("cron: ".length()), runnable);
		} else if (setting.toLowerCase().startsWith("tick: ")) {
			try {
				return create(Integer.parseInt(setting.substring("tick: ".length())), runnable);
			} catch (NumberFormatException ignored) {
			}
		}

		return create(720, runnable);
	}

	private static Schedule create(String cron, Runnable runnable) {
		return new CronSchedule(cron, runnable);
	}

	private static Schedule create(int tickEvery, Runnable runnable) {
		return new TickSchedule(tickEvery, runnable);
	}
}
