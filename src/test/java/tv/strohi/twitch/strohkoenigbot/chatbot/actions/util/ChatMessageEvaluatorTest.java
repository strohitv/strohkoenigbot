package tv.strohi.twitch.strohkoenigbot.chatbot.actions.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ChatMessageEvaluatorTest {
	private enum TestEnumClass {
		EnumTest,
		OtherEnumTest
	}

	@Test
	void builderShouldWork() {
		ChatMessageEvaluator cme = ChatMessageEvaluator.builder()
				.withFlag("Test")
				.withFlag("Awesome")
				.withEnum(TestEnumClass.class)
				.withRegex("(?:1+)")
				.build();

		Map<Object, Object> results = cme.evaluate("Test EnumTest 11111 OtherEnumTest Tset 333321");

		System.out.println(results);
	}
}
