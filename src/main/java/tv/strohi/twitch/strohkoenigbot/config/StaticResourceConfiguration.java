package tv.strohi.twitch.strohkoenigbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfiguration implements WebMvcConfigurer {
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		try {
			registry
				.addResourceHandler("/images/**")
				.addResourceLocations(
					new File(Paths.get(System.getProperty("user.dir"), "/images/").toString()).toURI().toURL().toString());
			registry.addResourceHandler("/splatnet3/**")
				.addResourceLocations(
					new File(Paths.get(System.getProperty("user.dir"), "/resources/prod/").toString()).toURI().toURL().toString());
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}
}
