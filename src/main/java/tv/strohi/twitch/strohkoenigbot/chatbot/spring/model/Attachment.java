package tv.strohi.twitch.strohkoenigbot.chatbot.spring.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.io.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder(toBuilder = true)
@Log4j2
public class Attachment {
	public static Attachment withDefaultName(String content) {
		var now = LocalDateTime.now();
		var attachmentName = String.format("attachment-log_%04d-%02d-%02d_%02d-%02d-%02d-%09d.md", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano());

		var stream = new ByteArrayInputStream(content.getBytes());
		var bytes = stream.readAllBytes();
		close(stream);

		return Attachment.builder().name(attachmentName).content(bytes).build();
	}

	public static Attachment fromFile(String path) {
		var file = new File(path);
		try (var stream = new FileInputStream(path)) {
			return Attachment.builder().name(file.getName()).content(stream.readAllBytes()).build();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static Attachment fromStream(String name, InputStream stream) {
		try {
			var bytes = stream.readAllBytes();
			close(stream);

			return Attachment.builder().name(name).content(bytes).build();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private String name;
	//	private InputStream stream;
	private byte[] content;

	public static void close(InputStream stream) {
		try {
			stream.close();
		} catch (Exception ex) {
			log.error("Could not close stream", ex);
		}
	}
}
