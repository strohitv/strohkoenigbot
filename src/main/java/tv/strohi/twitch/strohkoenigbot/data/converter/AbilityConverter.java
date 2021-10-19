package tv.strohi.twitch.strohkoenigbot.data.converter;

import tv.strohi.twitch.strohkoenigbot.chatbot.actions.model.AbilityType;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.stream.Stream;

@Converter(autoApply = true)
public class AbilityConverter implements AttributeConverter<AbilityType, String> {

	@Override
	public String convertToDatabaseColumn(AbilityType ability) {
		if (ability == null) {
			return AbilityType.Any.getName();
		}
		return ability.getName();
	}

	@Override
	public AbilityType convertToEntityAttribute(String name) {
		if (name == null) {
			return null;
		}

		return Stream.of(AbilityType.values())
				.filter(c -> c.getName().equals(name))
				.findFirst()
				.orElseThrow(IllegalArgumentException::new);
	}
}
