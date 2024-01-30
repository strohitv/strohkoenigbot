package tv.strohi.twitch.strohkoenigbot.utils.scheduling;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tv.strohi.twitch.strohkoenigbot.chatbot.spring.DiscordBot;
import tv.strohi.twitch.strohkoenigbot.data.model.Configuration;
import tv.strohi.twitch.strohkoenigbot.data.repository.ConfigurationRepository;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.CronSchedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.Schedule;
import tv.strohi.twitch.strohkoenigbot.utils.scheduling.model.TickSchedule;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchedulingService {
	private final Logger logger = LogManager.getLogger(this.getClass().getSimpleName());

	private final static int MAX_ERRORS_SINGLE = 3;
	private final static int MAX_ERRORS_REPEATED = 3; //5; //

	private final ConfigurationRepository configurationRepository;
	private final DiscordBot discordBot;

	private final List<Schedule> schedules = new ArrayList<>();
	private final List<Schedule> singleRunSchedules = new ArrayList<>();

	@Autowired
	public void setScheduledServices(List<ScheduledService> services) {
		services.forEach(service ->
			service.createScheduleRequests().forEach(request ->
				register(request.getName(), request.getSchedule(), request.getRunnable())));
	}

	@Scheduled(fixedDelay = 5000)
	private void run() {
		LocalDateTime now = LocalDateTime.now();

		for (int i = 0; i < singleRunSchedules.size(); i++) {
			Schedule schedule = singleRunSchedules.get(i);

			if (schedule.shouldRun(now)) {
				logger.info("running job...");

				try {
					schedule.getRunnable().run();
					singleRunSchedules.remove(i);
					i--;
				} catch (Exception ignored) {
					schedule.increaseErrorCount();

					if (schedule.isFailed(MAX_ERRORS_SINGLE)) {
						discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID,
							String.format("**ERROR**: Single Runnable failed **%d** times and got removed from Scheduler!! Schedule: `%s`", MAX_ERRORS_SINGLE, schedule));
						singleRunSchedules.remove(i);
						i--;
					}
				}

				logger.info("finished job...");
			}
		}

		for (int i = 0; i < schedules.size(); i++) {
			Schedule schedule = schedules.get(i);

			if (schedule.shouldRun(now)) {
				schedule.run();

				if (schedule.isFailed(MAX_ERRORS_REPEATED)) {
					discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID,
						String.format("**ERROR**: Repeated Runnable '**%s**' failed **%d** times and got removed from Scheduler!! Schedule: `%s`", schedule.getName(), MAX_ERRORS_REPEATED, schedule));

					List<Exception> exceptions = schedule.getErrors();
					Exception exception = exceptions.get(exceptions.size() - 1);

					Long discordId = DiscordBot.ADMIN_ID;

					discordBot.sendPrivateMessage(discordId, String.format("**Message of last exception**: '%s'", exception.getMessage()));

					StringWriter stringWriter = new StringWriter();
					PrintWriter printWriter = new PrintWriter(stringWriter);
					exception.printStackTrace(printWriter);

					String stacktrace = stringWriter.toString();
					if (stacktrace.length() > 1900) {
						stacktrace = stacktrace.substring(0, 1900);
					}

					discordBot.sendPrivateMessage(discordId, String.format("**Stacktrace of last exception**:\n'%s'", stacktrace));

					schedules.remove(i);
					i--;
				}
			}
		}
	}

	public void registerOnce(String name, int ticks, Runnable runnable) {
		singleRunSchedules.add(new TickSchedule(name, ticks, runnable));
	}

	public void registerOnce(String name, String cron, Runnable runnable) {
		singleRunSchedules.add(new CronSchedule(name, cron, runnable));
	}

	public void register(String configName, String defaultValue, Runnable runnable) {
		Configuration config = configurationRepository.findAllByConfigName(configName).stream().findFirst().orElse(null);

		if (config == null) {
			config = configurationRepository.save(new Configuration(0, configName, defaultValue));
			discordBot.sendPrivateMessage(DiscordBot.ADMIN_ID,
				String.format("Added new Schedule: id = **%d**, name = **%s**, value = **%s**", config.getId(), configName, defaultValue));
		}

		schedules.add(createFromSettings(configName, config.getConfigValue(), runnable));
	}

	private static Schedule createFromSettings(String name, String setting, Runnable runnable) {
		if (setting.toLowerCase().startsWith("cron: ")) {
			return create(name, setting.substring("cron: ".length()), runnable);
		} else if (setting.toLowerCase().startsWith("tick: ")) {
			try {
				return create(name, Integer.parseInt(setting.substring("tick: ".length())), runnable);
			} catch (NumberFormatException ignored) {
			}
		}

		return create(name, 720, runnable);
	}

	private static Schedule create(String name, String cron, Runnable runnable) {
		return new CronSchedule(name, cron, runnable);
	}

	private static Schedule create(String name, int tickEvery, Runnable runnable) {
		return new TickSchedule(name, tickEvery, runnable);
	}
}
