package tv.strohi.twitch.strohkoenigbot.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ComputerNameEvaluator {
	public static String getComputerName() {
		String hostname;

		try {
			InetAddress addr;
			addr = InetAddress.getLocalHost();
			hostname = addr.getHostName();
		} catch (UnknownHostException ex) {
			Map<String, String> env = System.getenv();

			if (env.containsKey("COMPUTERNAME")) {
				return env.get("COMPUTERNAME");
			} else {
				return env.getOrDefault("HOSTNAME", "Unknown Computer");
			}
		}

		return hostname;
	}
}
