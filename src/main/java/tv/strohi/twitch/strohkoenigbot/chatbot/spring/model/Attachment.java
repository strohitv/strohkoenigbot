package tv.strohi.twitch.strohkoenigbot.chatbot.spring.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder(toBuilder = true)
public class Attachment {
	public static Attachment withDefaultName(String content) {
		var now = LocalDateTime.now();
		var attachmentName = String.format("attachment-log_%04d-%02d-%02d_%02d-%02d-%02d-%09d.md", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano());

		return Attachment.builder().name(attachmentName).content(content).build();
	}

	public static Attachment fromFile(String path) {
		try {
			var file = new File(path);
			var content = Files.readString(Path.of(path));

			return Attachment.builder().name(file.getName()).content(content).build();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Attachment fromStream(String name, InputStream stream) {
		try {
			var content = new String(stream.readAllBytes());
			return Attachment.builder().name(name).content(content).build();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private String name;
	private String content;

	public InputStream openStream() {
		return new ByteArrayInputStream(content.getBytes());
	}
}
